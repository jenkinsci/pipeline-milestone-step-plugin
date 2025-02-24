package org.jenkinsci.plugins.pipeline.milestone;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.model.Executor;
import hudson.model.Result;
import hudson.model.Run;
import java.util.Set;
import java.util.SortedMap;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.ProtectedExternally;

/**
 * Manages persistence of active milestones and cancellation of older builds as needed.
 */
public interface MilestoneStorage extends ExtensionPoint {
    Logger LOGGER = Logger.getLogger(MilestoneStorage.class.getName());


    /**
     * Records passing a milestone.
     * @param run The run passing the milestone.
     * @param ordinal the ordinal of the milestone gettting passed. May be {@code null} to record build starting.
     * @return The list of milestones for the job after storing the new one.
     */
    SortedMap<Integer, Integer> store(@NonNull Run<?,?> run, @CheckForNull Integer ordinal);


    /**
     * Clears a {@link Run} from recorded milestones.
     * @param run The completed run.
     * @return The result from clearing the run.
     */
    @NonNull
    ClearResult clear(@NonNull Run<?,?> run);

    /**
     * Result of {@link #clear(Run)}.
     * @param lastMilestoneBeforeCompletion the last milestone the cleared run reached before completion
     * @param milestones the currently known milestones for other running builds of the same job.
     */
    record ClearResult(Integer lastMilestoneBeforeCompletion, SortedMap<Integer, Integer> milestones) {}

    /**
     * Cancels the given run due to another run passing a milestone.
     * @param run the run to cancel
     * @param externalizableId the externalizable id for the run causing the cancellation.
     */
    default void cancel(Run<?, ?> run, String externalizableId) {
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
    @Restricted(ProtectedExternally.class)
    void cancel(Set<Integer> buildNumbers, Run<?, ?> referenceRun);

}
