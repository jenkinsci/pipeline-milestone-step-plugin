package org.jenkinsci.plugins.pipeline.milestone;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.InvisibleAction;
import hudson.model.Job;
import hudson.model.Run;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import net.jcip.annotations.GuardedBy;

/**
 * Stores passed milestones for currently running builds.
 */
public class MilestoneJobAction extends InvisibleAction {
    /**
     * Do not persist, live data is rebuilt by {@link MilestoneStepExecution.FlowExecutionListenerImpl}.
     */
    @NonNull
    @GuardedBy("this")
    private transient Map<Integer, Integer> milestones = new TreeMap<>();

    protected MilestoneJobAction readResolve() {
        milestones = new TreeMap<>();
        return this;
    }

    public static Set<Integer> getBuildsToCancel(int buildNumber, @CheckForNull Integer ordinal, @NonNull Map<Integer, Integer> milestones) {
        return milestones.entrySet().stream()
                .filter(entry -> entry.getKey() < buildNumber)
                .filter(entry -> entry.getValue() == null || (ordinal != null && entry.getValue() <= ordinal))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public static void store(Run<?, ?> run) {
        store(run, null);
    }

    public static Set<Integer> store(@NonNull Run<?,?> run, @CheckForNull Integer ordinal) {
        var job = run.getParent();
        var action = ensure(job);
        int referenceRunNumber;
        Integer candidateOrdinal = ordinal;
        synchronized(action) {
            var mayRecentBuild = action.milestones.entrySet().stream()
                    .filter(entry -> entry.getKey() > run.getNumber())
                    .filter(entry -> ordinal == null || entry.getValue() >= ordinal).findFirst();
            if (mayRecentBuild.isEmpty()) {
                // Normal case, we didn't find any
                action.milestones.put(run.getNumber(), ordinal);
                job.addOrReplaceAction(action);
                referenceRunNumber = run.getNumber();
            } else {
                // Defensive: a more recent build already passed the same milestone
                var recentMilestone = mayRecentBuild.get();
                referenceRunNumber = recentMilestone.getKey();
                candidateOrdinal = recentMilestone.getValue();
            }
        }
        return MilestoneJobAction.getBuildsToCancel(referenceRunNumber, candidateOrdinal, action.milestones);
    }

    @NonNull
    private static MilestoneJobAction ensure(Job<?,?> job) {
        var action = job.getAction(MilestoneJobAction.class);
        if (action == null) {
            action = new MilestoneJobAction();
        }
        return action;
    }

    public static Set<Integer> clear(@NonNull Run<?,?> run) {
        var job = run.getParent();
        var action = ensure(job);
        var latestMilestone = action.milestones.remove(run.getNumber());
        job.addOrReplaceAction(action);
        if (latestMilestone != null) {
            // Cancel older builds if we've seen at least one milestone
            return MilestoneJobAction.getBuildsToCancel(run.getNumber(), Integer.MAX_VALUE, action.milestones);
        } else {
            // This build hasn't been using any milestone step, no need to cancel anything.
            return Set.of();
        }
    }
}
