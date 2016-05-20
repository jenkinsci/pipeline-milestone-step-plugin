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

import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.CheckForNull;

import org.jenkinsci.plugins.workflow.actions.LabelAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.FlowStartNode;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;

import com.google.inject.Inject;

import hudson.AbortException;
import hudson.Extension;
import hudson.model.Executor;
import hudson.model.InvisibleAction;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.listeners.RunListener;
import jenkins.model.Jenkins;

public class MilestoneStepExecution extends AbstractSynchronousStepExecution<Void> {

    private static final Logger LOGGER = Logger.getLogger(MilestoneStepExecution.class.getName());

    @Inject(optional=true) private transient MilestoneStep step;
    @StepContextParameter private transient Run<?,?> run;
    @StepContextParameter private transient FlowNode node;
    @StepContextParameter private transient TaskListener listener;

    @Override
    public Void run() throws Exception {
        if (step.getLabel() != null) {
            node.addAction(new LabelAction(step.getLabel()));
            node.addAction(new MilestoneAction(step.getLabel()));
        }
        int ordinal = processOrdinal();
        tryToPass(run, getContext(), ordinal);
        return null;
    }

    /**
     * Gets the next ordinal and throw {@link AbortException} the milestone lives inside a parallel step branch.
     */
    private synchronized int processOrdinal() throws AbortException {
        // TODO: use FlowNodeSerialWalker when released
        FlowGraphWalker walker = new FlowGraphWalker();
        walker.addHead(node);
        Integer previousOrdinal = null;
        int parallelDetectionEnabled = 0;
        for (FlowNode n : walker) {

            if (parallelDetectionEnabled <= 0 && n.getAction(ThreadNameAction.class) != null) {
                throw new AbortException("Using a milestone step inside parallel is not allowed");
            }

            if (n instanceof BlockEndNode) {
                parallelDetectionEnabled++;
            } else if (n instanceof BlockStartNode && !(n instanceof FlowStartNode)) {
                parallelDetectionEnabled--;
            }

            OrdinalAction a = n.getAction(OrdinalAction.class);
            if (a != null) {
                previousOrdinal = a.ordinal;
                break;
            }
        }

        // If step.ordinal is set then use it and check order with the previous one
        // Otherwise use calculated ordinal (previousOrdinal + 1)
        int nextOrdinal = 0;
        Integer stepOrdinal = step.getOrdinal();
        if (stepOrdinal != null) {
            if (previousOrdinal != null) {
                if (previousOrdinal >= stepOrdinal) {
                    throw new AbortException(String.format("Invalid ordinal %s, as the previous one was %s", nextOrdinal, previousOrdinal));
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
        node.addAction(new OrdinalAction(nextOrdinal));
        return nextOrdinal;
    }

    private static class OrdinalAction extends InvisibleAction {
        int ordinal;
        public OrdinalAction(int ordinal) {
            this.ordinal = ordinal;
        }
    }

    private static Map<String, Map<Integer, Milestone>> getMilestonesByOrdinalByJob() {
        return ((MilestoneStep.DescriptorImpl) Jenkins.getActiveInstance().getDescriptorOrDie(MilestoneStep.class)).getMilestonesByOrdinalByJob();
    }

    private synchronized void tryToPass(Run<?,?> r, StepContext context, int ordinal) throws IOException, InterruptedException {
        LOGGER.log(Level.FINE, "build {0} trying to pass milestone {1}", new Object[] {r, ordinal});
        println(context, "Trying to pass milestone " + ordinal);
        load();
        Job<?,?> job = r.getParent();
        String jobName = job.getFullName();
        Map<Integer, Milestone> milestonesInJob = getMilestonesByOrdinalByJob().get(jobName);
        if (milestonesInJob == null) {
            milestonesInJob = new TreeMap<Integer,Milestone>();
            getMilestonesByOrdinalByJob().put(jobName, milestonesInJob);
        }
        Milestone milestone = milestonesInJob.get(ordinal);
        if (milestone == null) {
            milestone = new Milestone(ordinal);
            milestonesInJob.put(ordinal, milestone);
        }

        // Defensive order check and cancel older builds behind
        for (Map.Entry<Integer, Milestone> entry : milestonesInJob.entrySet()) {
            if (entry.getKey().equals(ordinal)) {
                continue;
            }
            Milestone milestone2 = entry.getValue();
            // The build is passing a milestone, so it's not visible to any previous milestone
            if (milestone2.wentAway(r)) {
                // Ordering check
                if(milestone2.ordinal >= ordinal) {
                    throw new AbortException(String.format("Unordered milestone. Found ordinal %s but %s (or bigger) was expected.", ordinal, milestone2.ordinal + 1));
                }
                // Cancel older builds (holding or waiting to enter)
                cancelOldersInSight(milestone2, r);
            }
        }

        // checking order
        if (milestone.lastBuild != null && r.getNumber() < milestone.lastBuild) {
            // cancel if it's older than the last one passing this milestone
            cancel(context, milestone.lastBuild);
        } else {
            // It's in-order, proceed
            milestone.pass(context, r);
        }
        cleanUp(job, jobName);
        save();
    }

    private static synchronized void exit(Run<?,?> r) {
        load();
        LOGGER.log(Level.FINE, "exit {0}: {1}", new Object[] {r, getMilestonesByOrdinalByJob()});
        Job<?,?> job = r.getParent();
        String jobName = job.getFullName();
        Map<Integer, Milestone> milestonesInJob = getMilestonesByOrdinalByJob().get(jobName);
        if (milestonesInJob == null) {
            return;
        }
        boolean modified = false;
        for (Milestone milestone : milestonesInJob.values()) {
            if (milestone.wentAway(r)) {
                modified = true;
                cancelOldersInSight(milestone, r);
            }
        }
        if (modified) {
            cleanUp(job, jobName);
        }

        // Clean non-existing milestones
        if (r instanceof FlowExecutionOwner.Executable) {
            Integer lastMilestoneOrdinal = getLastOrdinalInBuild((FlowExecutionOwner.Executable) r);
            if (lastMilestoneOrdinal == null) {
                return;
            }
            Milestone m = getFirstWithoutInSight(milestonesInJob);
            while (m != null && milestonesInJob.size() - 1 > lastMilestoneOrdinal) {
                modified = true;
                milestonesInJob.remove(m.ordinal);
                m = getFirstWithoutInSight(milestonesInJob);
            }
            if (milestonesInJob.isEmpty()) {
                modified = true;
                getMilestonesByOrdinalByJob().remove(jobName);
            }
        }

        if (modified) {
            save();
        }
    }

    @CheckForNull
    private static Integer getLastOrdinalInBuild(FlowExecutionOwner.Executable r) {
        int lastMilestoneOrdinal = 0;
        FlowExecutionOwner owner = r.asFlowExecutionOwner();
        if (owner == null) {
            return null;
        }
        try {
            List<FlowNode> heads = owner.get().getCurrentHeads();
            if (heads.size() == 1) {
                FlowGraphWalker walker = new FlowGraphWalker();
                walker.addHead(heads.get(0));
                for (FlowNode n : walker) {
                    OrdinalAction action = n.getAction(OrdinalAction.class);
                    if (action != null) {
                        lastMilestoneOrdinal = action.ordinal;
                        break;
                    }
                }
            } else {
                LOGGER.log(Level.WARNING, "Trying to get last ordinal for a build still in progress?");
                return null;
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to traverse flow graph to search the last milestone ordinal", e);
        }
        return lastMilestoneOrdinal;
    }

    /**
     * Returns the first milestone without any build in sight or null if not found.
     */
    @CheckForNull
    private static Milestone getFirstWithoutInSight(Map<Integer, Milestone> milestones) {
        for (Entry<Integer, Milestone> entry : milestones.entrySet()) {
            Milestone m = entry.getValue();
            if (m.inSight.isEmpty()) {
                return m;
            }
        }
        return null;
    }

    /**
     * Cancels any build older than the given one in sight of the milestone.
     *
     * @param r the build which is going away of the given milestone
     * @param milestone the milestone which r is leaving (because it entered the next milestone or finished).
     */
    private static void cancelOldersInSight(Milestone milestone, Run<?, ?> r) {
        // Cancel any older build in sight of the milestone
        for (Integer inSightNumber : milestone.inSight) {
            if (r.getNumber() > inSightNumber) {
                Run<?, ?> olderInSightBuild = r.getParent().getBuildByNumber(inSightNumber);
                Executor e = olderInSightBuild.getExecutor();
                if (e != null) {
                    e.interrupt(Result.NOT_BUILT, new CancelledCause(r.getExternalizableId()));
                } else {
                    LOGGER.log(WARNING, "could not cancel an older flow because it has no assigned executor");
                }
            }
        }
    }

    private static void println(StepContext context, String message) {
        if (!context.isReady()) {
            LOGGER.log(Level.FINE, "cannot print message ‘{0}’ to dead {1}", new Object[] {message, context});
            return;
        }
        try {
            context.get(TaskListener.class).getLogger().println(message);
        } catch (Exception x) {
            LOGGER.log(WARNING, "failed to print message to dead " + context, x);
        }
    }

    private static void cancel(StepContext context, Integer build) throws IOException, InterruptedException {
        if (context.isReady()) {
            println(context, "Canceled since build #" + build + " already got here");
            Run<?, ?> r = context.get(Run.class);
            String job = "";
            if (r != null) { // it should be always non-null at this point, but let's do a defensive check
                job = r.getParent().getFullName();
            }
            throw new FlowInterruptedException(Result.NOT_BUILT, new CancelledCause(job + "#" + build));
        } else {
            LOGGER.log(WARNING, "cannot cancel dead #" + build);
        }
    }

    private static void cleanUp(Job<?,?> job, String jobName) {
        Map<Integer, Milestone> milestonesInJob = getMilestonesByOrdinalByJob().get(jobName);
        assert milestonesInJob != null;
        Iterator<Entry<Integer, Milestone>> it = milestonesInJob.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Milestone> entry = it.next();
            Set<Integer> inSight = entry.getValue().inSight;
            Iterator<Integer> it2 = inSight.iterator();
            while (it2.hasNext()) {
                Integer number = it2.next();
                if (job.getBuildByNumber(number) == null) {
                    // Deleted at some point but did not properly clean up from exit(…).
                    LOGGER.log(WARNING, "Cleaning up apparently deleted {0}#{1}", new Object[] {jobName, number});
                    it2.remove();
                }
            }
        }
    }

    private static void load() {
        Jenkins.getActiveInstance().getDescriptorOrDie(MilestoneStep.class).load();
    }

    private static void save() {
        Jenkins.getActiveInstance().getDescriptorOrDie(MilestoneStep.class).save();
    }

    @Extension
    public static final class Listener extends RunListener<Run<?,?>> {
        @Override public void onCompleted(Run<?,?> r, TaskListener listener) {
            if (!(r instanceof FlowExecutionOwner.Executable) || ((FlowExecutionOwner.Executable) r).asFlowExecutionOwner() == null
                    || Result.ABORTED.equals(r.getResult())) {
                return;
            }
            exit(r);
        }
    }

    private static final long serialVersionUID = 1L;

}
