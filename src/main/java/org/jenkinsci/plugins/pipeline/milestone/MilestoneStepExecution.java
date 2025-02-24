/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.pipeline.milestone;

import com.google.common.base.Predicate;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.model.InvisibleAction;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.ItemListener;
import hudson.model.listeners.RunListener;
import java.io.IOException;
import java.io.Serial;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionListener;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.FlowScanningUtils;
import org.jenkinsci.plugins.workflow.graphanalysis.LinearScanner;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;

public class MilestoneStepExecution extends SynchronousStepExecution<Void> {

    private static final Predicate<FlowNode> ORDINAL_MATCHER = FlowScanningUtils.hasActionPredicate(OrdinalAction.class);
    private static final Logger LOGGER = Logger.getLogger(MilestoneStepExecution.class.getName());
    private final String label;
    private final Integer ordinal;
    private final boolean unsafe;

    public MilestoneStepExecution(@NonNull StepContext context, @CheckForNull String label, @CheckForNull Integer ordinal, boolean unsafe) {
        super(context);
        this.label = label;
        this.ordinal = ordinal;
        this.unsafe = unsafe;
    }

    /**
     * @param buildNumber The build number currently querying for builds to cancel
     * @param ordinal The ordinal the build just passed. {@code null} means it just started.
     * @param milestones A map keyed by build numbers recording their current milestone.
     * @return A subset of build numbers among the given milestones eligible for cancellation.
     */
    public static Map<Integer, Integer> getBuildsToCancel(int buildNumber, @CheckForNull Integer ordinal, @NonNull Map<Integer, Integer> milestones) {
        Map<Integer, Integer> result = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : milestones.entrySet()) {
            if (entry.getKey() < buildNumber) {
                if (entry.getValue() == null || (ordinal != null && entry.getValue() < ordinal)) {
                    Integer key = entry.getKey();
                    result.put(key, buildNumber);
                }
            } else if (entry.getKey() > buildNumber && ((ordinal == null && entry.getValue() != null) || (ordinal != null  && entry.getValue() >= ordinal))) {
                // Defensive, this should never happen.
                result.put(buildNumber, entry.getKey());
            }
        }
        return result;
    }

    @Override
    public Void run() throws Exception {
        if (label != null) {
            getContext().get(FlowNode.class).addAction(new LabelAction(label));
        }
        tryToPass(getContext().get(Run.class), getContext(), processOrdinal());
        return null;
    }

    /**
     * Gets the next ordinal and throw {@link AbortException} the milestone lives inside a parallel step branch.
     */
    private synchronized int processOrdinal() throws IOException, InterruptedException {
        var node = getContext().get(FlowNode.class);
        List<FlowNode> heads = node.getExecution().getCurrentHeads();
        if (heads.size() > 1 && !unsafe) {  // TA-DA!  We're inside a parallel, which is forbidden.
            throw new AbortException("Using a milestone step inside parallel is not allowed");
        }
        var nextOrdinal = getNextOrdinal(getLatestOrdinalAction(heads));
        node.addAction(new OrdinalAction(nextOrdinal));
        return nextOrdinal;
    }

    private static OrdinalAction getLatestOrdinalAction(List<FlowNode> heads) {
        FlowNode lastOrdinalNode = new LinearScanner().findFirstMatch(heads.get(0), ORDINAL_MATCHER);
        return lastOrdinalNode != null ? lastOrdinalNode.getAction(OrdinalAction.class) : null;
    }

    private int getNextOrdinal(@CheckForNull OrdinalAction action) throws AbortException {
        Integer previousOrdinal = action != null ? action.ordinal : null;

        // If step.ordinal is set then use it and check order with the previous one
        // Otherwise use calculated ordinal (previousOrdinal + 1)
        int nextOrdinal = 0;
        Integer stepOrdinal = ordinal;
        if (stepOrdinal != null) {
            if (previousOrdinal != null) {
                if (previousOrdinal >= stepOrdinal) {
                    throw new AbortException(String.format("Invalid ordinal %s, as the previous one was %s", stepOrdinal, previousOrdinal));
                } else {
                    nextOrdinal = stepOrdinal;
                }
            } else {
                nextOrdinal = stepOrdinal;
            }
        } else {
            if (previousOrdinal != null) {
                nextOrdinal = previousOrdinal + 1;
            } // else next ordinal 0
        }
        return nextOrdinal;
    }

    private static class OrdinalAction extends InvisibleAction {
        int ordinal;
        public OrdinalAction(int ordinal) {
            this.ordinal = ordinal;
        }
    }

    private synchronized void tryToPass(Run<?,?> r, StepContext context, int ordinal) {
        LOGGER.log(Level.FINE, () -> "build " + r + " trying to pass milestone " + ordinal);
        println(context, "Trying to pass milestone " + ordinal);
        MilestoneStorage milestoneStorage = getStorage();
        var milestones = milestoneStorage.store(r, ordinal);
        LOGGER.fine(() -> "build " + r + " : milestones after put -> " + milestones);
        var buildsToCancel = getBuildsToCancel(r.getNumber(), ordinal, milestones);
        milestoneStorage.cancel(r.getParent(), buildsToCancel);
    }

    private static void println(StepContext context, String message) {
        if (!context.isReady()) {
            LOGGER.log(Level.FINE, "cannot print message ‘{0}’ to dead {1}", new Object[] {message, context});
            return;
        }
        try {
            context.get(TaskListener.class).getLogger().println(message);
        } catch (Exception x) {
            LOGGER.log(Level.WARNING, x, () -> "failed to print message to dead " + context);
        }
    }

    @Extension
    public static final class Listener extends RunListener<Run<?,?>> {
        @Override
        public void onStarted(Run<?, ?> r, TaskListener listener) {
            if (isPipelineRun(r)) {
                MilestoneStorage milestoneStorage = getStorage();
                milestoneStorage.store(r, null);
            }
        }

        @Override public void onCompleted(Run<?,?> r, @NonNull TaskListener listener) {
            if (isPipelineRun(r)) {
                MilestoneStorage milestoneStorage = getStorage();
                var result = milestoneStorage.clear(r);
                MilestoneStorage.LOGGER.finest(() -> "milestones after completion: " + result.milestones());
                if (result.lastMilestoneBeforeCompletion() != null) {
                    MilestoneStorage.LOGGER.finest(() -> "Build" + r + " last milestone before completion: " + result.lastMilestoneBeforeCompletion());
                    var buildsToCancel = getBuildsToCancel(r.getNumber(), Integer.MAX_VALUE, result.milestones());
                    milestoneStorage.cancel(r.getParent(), buildsToCancel);
                } else {
                    MilestoneStorage.LOGGER.finest(() -> "Build " + r + " was not using milestones, nothing to cancel");
                }
            }
        }

        private boolean isPipelineRun(Run<?, ?> r) {
            return r instanceof FlowExecutionOwner.Executable executable && executable.asFlowExecutionOwner() != null;
        }
    }


    @Extension
    public static final class ItemListenerImpl extends ItemListener {
        @Override
        public void onDeleted(Item item) {
            if (item instanceof Job<?,?> job) {
                getStorage().onDeletedJob(job);
            }
        }
    }

    /**
     * Listens to pipeline resume, and let {@link MilestoneStorage} know the latest persisted milestone.
     */
    @Extension
    public static final class FlowExecutionListenerImpl extends FlowExecutionListener {
        @Override
        public void onResumed(@NonNull FlowExecution execution) {
            try {
                LOGGER.finest(() -> "Resuming " + execution);
                var executable = execution.getOwner().getExecutable();
                if (executable instanceof Run<?,?> run) {
                    LOGGER.fine(() -> "Executable " + executable + " is a run");
                    var ordinalAction = getLatestOrdinalAction(execution.getCurrentHeads());
                    MilestoneStorage milestoneStorage = getStorage();
                    milestoneStorage.store(run, ordinalAction == null ? null : ordinalAction.ordinal);
                } else {
                    LOGGER.fine(() -> "Executable " + executable + " is not a run");
                }
            } catch (IOException e) {
               LOGGER.log(Level.WARNING, e, () -> "Unable to look up executable from " + execution);
            }
        }
    }

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * @return the active implementation
     */
    @NonNull
    private static MilestoneStorage getStorage() {
        return ExtensionList.lookupFirst(MilestoneStorage.class);
    }
}
