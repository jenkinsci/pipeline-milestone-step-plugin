package org.jenkinsci.plugins.pipeline.milestone;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Action;
import hudson.model.Executor;
import hudson.model.Job;
import hudson.model.Result;
import hudson.model.Run;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Manages persistence of active milestones and cancellation of older builds as needed.
 */
public abstract class MilestoneStorage implements ExtensionPoint {
    private static final Logger LOGGER = Logger.getLogger(MilestoneStorage.class.getName());

    /**
     * @param buildNumber The build number currently querying for builds to cancel
     * @param ordinal The ordinal the build just passed. {@code null} means it just started.
     * @param milestones A map keyed by build numbers recording their current milestone.
     * @return A subset of build numbers among the given milestones eligible for cancellation.
     */
    public static Set<Integer> getBuildsToCancel(int buildNumber, @CheckForNull Integer ordinal, @NonNull Map<Integer, Integer> milestones) {
        return milestones.entrySet().stream()
                .filter(entry -> entry.getKey() < buildNumber)
                .filter(entry -> entry.getValue() == null || (ordinal != null && entry.getValue() <= ordinal))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }


    /**
     * Records passing a milestone.
     * @param run The run passing the milestone.
     * @param ordinal the ordinal of the milestone gettting passed. May be {@code null} to record build starting.
     * @return The current list of milestones for the job.
     */
    protected abstract Map<Integer, Integer> store(@NonNull Run<?,?> run, @CheckForNull Integer ordinal);

    /**
     * Records passing a milestone.
     * <p>
     * This is expected to cancel all prior builds that did not pass the same milestone,
     * or even the current build if another recent build passed it.
     *
     * @param run the active run
     * @param ordinal the ordinal of the milestone to pass
     */
    public final void put(@NonNull Run<?,?> run, int ordinal) {
        var milestones = store(run, ordinal);
        LOGGER.finest(() -> "milestones after put: " + milestones);
        var buildsToCancel = getBuildsToCancel(run.getNumber(), ordinal, milestones);
        cancel(buildsToCancel, run);
    }


    /**
     * Clears a {@link Run} from recorded milestones.
     * @param run The completed run.
     * @return The result from clearing the run.
     */
    @NonNull
    protected abstract ClearResult clear(@NonNull Run<?,?> run);

    /**
     * Result of {@link #clear(Run)}.
     * @param lastMilestoneBeforeCompletion the last milestone the cleared run reached before completion
     * @param milestones the currently known milestones for other running builds of the same job.
     */
    public record ClearResult(Integer lastMilestoneBeforeCompletion, Map<Integer, Integer> milestones) {}

    /**
     * Records a completed build.
     * <p>
     * This is expected to cancel all prior builds that are still ongoing.
     *
     * @param run the active run
     */
    public final void complete(@NonNull Run<?,?> run) {
        var result = clear(run);
        LOGGER.finest(() -> "milestones after completion: " + result.milestones);
        if (result.lastMilestoneBeforeCompletion != null) {
            LOGGER.finest(() -> "Build" + run + " last milestone before completion: " + result.lastMilestoneBeforeCompletion);
            var buildsToCancel = getBuildsToCancel(run.getNumber(), Integer.MAX_VALUE, result.milestones);
            cancel(buildsToCancel, run);
        } else {
            LOGGER.finest(() -> "Build " + run + " was not using milestones, nothing to cancel");
        }
    }

    /**
     * @return the active implementation
     */
    public static MilestoneStorage get() {
        return ExtensionList.lookupFirst(MilestoneStorage.class);
    }

    /**
     * Records a starting run.
     *
     * @param run the active run
     */
    public final void start(@NonNull Run<?, ?> run) {
        store(run, null);
    }

    /**
     * Called when a run resumes.
     * @param run the active run
     * @param ordinal the highest ordinal found for this run while resuming it.
     */
    public final void resume(@NonNull Run<?,?> run, @CheckForNull Integer ordinal) {
        store(run, ordinal);
    }

    /**
     * Cancels the given run due to another run passing a milestone.
     * @param run the run to cancel
     * @param externalizableId the externalizable id for the run causing the cancellation.
     */
    public final void cancel(Run<?, ?> run, String externalizableId) {
        LOGGER.fine(() -> "Cancelling " + run);
        Executor e = run.getExecutor();
        if (e != null) {
            e.interrupt(Result.NOT_BUILT, new CancelledCause(externalizableId));
        } else {
            LOGGER.warning(() -> "could not cancel an older flow because it has no assigned executor");
        }
    }

    /**
     * Given a reference run, cancels all the given builds of the same job with the given build numbers.
     * @param buildNumbers The build numbers of runs to cancel.
     * @param referenceRun The run that is causing the cancellation.
     */
    protected abstract void cancel(Set<Integer> buildNumbers, Run<?, ?> referenceRun);

    /**
     * The default implementation, attaching an {@link Action} to the {@link Job}.
     */
    @Extension(ordinal = -1)
    public static class DefaultMilestoneStorage extends MilestoneStorage {
        @Override
        protected Map<Integer, Integer> store(@NonNull Run<?,?> run, @CheckForNull Integer ordinal) {
            return MilestoneJobAction.store(run, ordinal);
        }

        @Override
        @NonNull
        protected ClearResult clear(@NonNull Run<?,?> run) {
            return MilestoneJobAction.clear(run);
        }

        /**
         * Cancel all runs with the given numbers
         */
        protected void cancel(Set<Integer> buildsToCancel, Run<?,?> referenceRun) {
            LOGGER.fine(() -> "Cancelling " + buildsToCancel + " (reference run :" + referenceRun + ")");
            buildsToCancel.forEach(buildNumber -> cancel(referenceRun.getParent().getBuildByNumber(buildNumber), referenceRun.getExternalizableId()));
        }
    }
}
