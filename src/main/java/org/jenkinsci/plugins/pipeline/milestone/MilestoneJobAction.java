package org.jenkinsci.plugins.pipeline.milestone;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.InvisibleAction;
import hudson.model.Job;
import hudson.model.Run;
import java.io.Serial;
import java.util.Map;
import java.util.TreeMap;

/**
 * Stores passed milestones for currently running builds.
 */
public class MilestoneJobAction extends InvisibleAction {
    /**
     * Do not persist, live data is rebuilt by {@link MilestoneStepExecution.FlowExecutionListenerImpl}.
     */
    @NonNull
    private transient Map<Integer, Integer> milestones = new TreeMap<>();

    @Serial
    protected MilestoneJobAction readResolve() {
        milestones = new TreeMap<>();
        return this;
    }

    @NonNull
    public static Map<Integer, Integer> store(@NonNull Run<?,?> run, @CheckForNull Integer ordinal) {
        var job = run.getParent();
        var action = ensure(job);
        var buildNumber = run.getNumber();
        action.milestones.put(buildNumber, ordinal);
        job.addOrReplaceAction(action);
        return new TreeMap<>(action.milestones);
    }

    @NonNull
    private static MilestoneJobAction ensure(Job<?,?> job) {
        var action = job.getAction(MilestoneJobAction.class);
        if (action == null) {
            action = new MilestoneJobAction();
        }
        return action;
    }

    @NonNull
    public static MilestoneStorage.ClearResult clear(@NonNull Run<?,?> run) {
        var job = run.getParent();
        var action = ensure(job);
        var previousMilestone = action.milestones.remove(run.getNumber());
        job.addOrReplaceAction(action);
        return new MilestoneStorage.ClearResult(previousMilestone, new TreeMap<>(action.milestones));
    }
}
