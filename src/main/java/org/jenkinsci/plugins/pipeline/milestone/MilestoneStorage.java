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
import java.util.Set;
import java.util.logging.Logger;

/**
 * Manages persistence of active milestones and cancellation of older builds as needed.
 */
public abstract class MilestoneStorage implements ExtensionPoint {
    private static final Logger LOGGER = Logger.getLogger(MilestoneStorage.class.getName());

    /**
     * Records passing a milestone.
     * <p>
     * This is expected to cancel all prior builds that did not pass the same milestone,
     * or even the current build if another recent build passed it.
     *
     * @param run the active run
     * @param ordinal the ordinal of the milestone to pass
     */
    public abstract void put(@NonNull Run<?,?> run, int ordinal);

    /**
     * Records a completed build.
     * <p>
     * This is expected to cancel all prior builds that are still ongoing.
     *
     * @param run the active run
     */
    public abstract void complete(@NonNull Run<?,?> run);

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
    public abstract void start(@NonNull Run<?, ?> run);

    /**
     * Called when a run resumes.
     * @param run the active run
     * @param ordinal the highest ordinal found for this run while resuming it.
     */
    public abstract void resume(@NonNull Run<?,?> run, @CheckForNull Integer ordinal);

    /**
     * Cancels the given run due to another run passing a milestone.
     * @param run the run to cancel
     * @param referenceRun the run that caused the cancellation.
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
     * The default implementation, attaching an {@link Action} to the {@link Job}.
     */
    @Extension(ordinal = -1)
    public static class DefaultMilestoneStorage extends MilestoneStorage {
        @Override
        public void put(@NonNull Run<?,?> run, int ordinal) {
            var buildsToCancel = MilestoneJobAction.store(run, ordinal);
            cancelAll(buildsToCancel, run);
        }

        @Override
        public void complete(@NonNull Run<?,?> run) {
            var buildsToCancel = MilestoneJobAction.clear(run);
            cancelAll(buildsToCancel, run);
        }

        @Override
        public void start(Run<?, ?> run) {
            MilestoneJobAction.store(run);
        }

        @Override
        public void resume(@NonNull Run<?, ?> run, @CheckForNull Integer ordinal) {
            MilestoneJobAction.store(run, ordinal);
        }

        /**
         * Cancel all runs with the given numbers
         * @param buildsToCancel
         * @param referenceRun
         */
        private void cancelAll(Set<Integer> buildsToCancel, Run<?,?> referenceRun) {
            LOGGER.fine(() -> "Cancelling " + buildsToCancel + " (reference run :" + referenceRun + ")");
            buildsToCancel.forEach(buildNumber -> cancel(referenceRun.getParent().getBuildByNumber(buildNumber), referenceRun.getExternalizableId()));
        }
    }
}
