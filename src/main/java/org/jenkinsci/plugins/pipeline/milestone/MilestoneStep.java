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


import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.model.Run;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * This step can be used to grant:
 * <ol>
 *   <li>Builds pass through the step in order (taking the build number as sorter field)</li>
 *   <li>Older builds will not proceed (they are aborted) if a newer one already entered the milestone</li>
 *   <li>When a build passes a milestone, any older build that passed the previous milestone - but not this one - is aborted.</li>
 *   <li>Once a build passes the milestone, it will be never aborted by a newer build that didn't pass the milestone yet.</li>
 * </ol>
 */
public class MilestoneStep extends Step {
    private static final Logger LOGGER = Logger.getLogger(MilestoneStep.class.getName());

    /**
     * Optional milestone label.
     */
    @CheckForNull
    private String label;

    /**
     * Optional ordinal.
     */
    @CheckForNull
    private final Integer ordinal;

    /**
     * Optional unsafe.
     */
    private boolean unsafe;

    @DataBoundConstructor
    public MilestoneStep(@CheckForNull Integer ordinal) {
        this.ordinal = ordinal;
    }

    @DataBoundSetter
    public void setLabel(String label) {
        this.label = Util.fixEmpty(label);
    }

    @DataBoundSetter
    public void setUnsafe(boolean unsafe) {
        this.unsafe = unsafe;
    }

    @CheckForNull
    public String getLabel() {
        return label;
    }

    @CheckForNull
    public Integer getOrdinal() {
        return ordinal;
    }

    public boolean isUnsafe() {
        return unsafe;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new MilestoneStepExecution(context, label, ordinal, unsafe);
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {
        /**
         * @deprecated Replaced by {@link DefaultMilestoneStorage#milestonesPerJob}.
         */
        @Deprecated
        private transient Map<String, Map<Integer, Milestone>> milestonesByOrdinalByJob;

        public DescriptorImpl() {
            load();
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(FlowNode.class, Run.class);
        }

        @Override
        public String getFunctionName() {
            return "milestone";
        }

        @Override
        @NonNull
        public String getDisplayName() {
            return "The milestone step forces all builds to go through in order";
        }

        @Override
        public void load() {
            super.load();
            if (milestonesByOrdinalByJob == null) {
                milestonesByOrdinalByJob = new TreeMap<>();
            }
            LOGGER.log(Level.FINE, "load: {0}", milestonesByOrdinalByJob);
        }

        @Override
        public void save() {
            super.save();
            LOGGER.log(Level.FINE, "save: {0}", milestonesByOrdinalByJob);
        }

        @Deprecated
        public Map<String, Map<Integer, Milestone>> getMilestonesByOrdinalByJob() {
            return milestonesByOrdinalByJob;
        }
    }
}
