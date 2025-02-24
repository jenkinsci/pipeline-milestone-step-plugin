package org.jenkinsci.plugins.pipeline.milestone;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Job;
import hudson.model.Run;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * The default implementation storing milestones in memory.
 */
@Extension(ordinal = -1)
@Restricted(NoExternalUse.class)
public class DefaultMilestoneStorage implements MilestoneStorage {
    private static final Logger LOGGER = Logger.getLogger(DefaultMilestoneStorage.class.getName());

    private final Map<Job<?,?>, Map<Integer, Integer>> milestonesPerJob = Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public Map<Integer, Integer> store(@NonNull Run<?, ?> run, @CheckForNull Integer ordinal) {
        return Collections.unmodifiableMap(milestonesPerJob.compute(run.getParent(), (job, milestones) -> {
            if (milestones == null) {
                milestones = new TreeMap<>();
            }
            milestones.put(run.getNumber(), ordinal);
            return milestones;
        }));
    }

    @Override
    @NonNull
    public ClearResult clear(@NonNull Run<?, ?> run) {
        var previousMilestone = new AtomicReference<Integer>();
        var newMilestones = milestonesPerJob.compute(run.getParent(), (job, milestones) -> {
            if (milestones != null) {
                previousMilestone.set(milestones.remove(run.getNumber()));
                if (milestones.isEmpty()) {
                    return null;
                }
            }
            return milestones;
        });
        return new ClearResult(previousMilestone.get(), Collections.unmodifiableMap(newMilestones == null ? new HashMap<>() : newMilestones));
    }

    @Override
    public void onDeletedJob(@NonNull Job<?, ?> job) {
        LOGGER.log(Level.FINE, () -> "Clearing milestones for " + job.getFullName());
        milestonesPerJob.remove(job);
    }
}
