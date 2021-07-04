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


import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import hudson.util.ListBoxModel;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.Util;

/**
 * This step can be used to grant:
 * <ol>
 *   <li>Builds pass through the step in order (taking the build number as sorter field)</li>
 *   <li>Older builds will not proceed (they are aborted) if a newer one already entered the milestone</li>
 *   <li>When a build passes a milestone, any older build that passed the previous milestone - but not this one - is aborted.</li>
 *   <li>Once a build passes the milestone, it will be never aborted by a newer build that didn't pass the milestone yet unless user set policy to cancel such old builds</li>
 * </ol>
 */
public class MilestoneStep extends AbstractStepImpl {

    /**
     * Optional milestone group.
     */
    private String group = "default";

    /**
     * Optional milestone label.
     */
    private String label;

    /**
     * Optional ordinal.
     */
    private Integer ordinal;

    /**
     * Optional unsafe.
     */
    private boolean unsafe;

    /**
     * cancel policy
     */
    private MilestonePolicy policy = MilestonePolicy.CONTINUE_OLD_BUILDS;

    @DataBoundConstructor
    public MilestoneStep(Integer ordinal) {
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

    @DataBoundSetter
    public void setGroup(String group) {
        if(group == null || group.isEmpty() ) {
            this.group = "default";
        } else {
            this.group = group;
        }
    }

    @DataBoundSetter
    public void setPolicy(MilestonePolicy policy) {
        this.policy = policy;
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

    @CheckForNull
    public String getGroup() {
        return group;
    }

    @CheckForNull
    public MilestonePolicy getPolicy() {
        return policy;
    }

    @Override
    public String toString() {
        return "MilestoneStep["
                +" group="+group
                +", ordinal="+ordinal
                +", label="+label
                +"]";
    }

    public ListBoxModel doFillPolicyItems() {
        ListBoxModel r = new ListBoxModel();
        for (MilestonePolicy policy : MilestonePolicy.values()) {
            r.add(policy.name());
        }
        return r;
    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {

        private transient Map<String, Map<String, Milestone>> milestonesByGroupByJob;
        private transient Map<String, Map<Integer, Milestone>> milestonesByOrdinalByJob;

        private static final Logger LOGGER = Logger.getLogger(MilestoneStep.class.getName());

        public DescriptorImpl() {
            super(MilestoneStepExecution.class);
            load();
        }

        @Override
        public String getFunctionName() {
            return "milestone";
        }

        @Override
        public String getDisplayName() {
            return "The milestone step forces all builds to go through in order";
        }

        @Override
        public void load() {
            super.load();
            if (milestonesByGroupByJob == null) {
                milestonesByGroupByJob = new TreeMap<String, Map<String, Milestone>>();
            }
            else {
                this.milestonesByGroupByJob.forEach((j,m) -> m.forEach( (o,v) -> LOGGER.log(Level.INFO, "loaded milestone: {0}", o)));
            }
            LOGGER.log(Level.INFO, "load: {0}", milestonesByGroupByJob);
        }

        public Object readResolve() {
            LOGGER.log(Level.INFO, "readResolve: {0}", this);
            LOGGER.log(Level.INFO, "readResolve: this.milestonesByGroupByJob: {0}", this.milestonesByGroupByJob);
            LOGGER.log(Level.INFO, "readResolve: this.milestonesByOrdinalByJob: {0}", this.milestonesByOrdinalByJob);
            return this;
        }

        @Override
        public void save() {
            super.save();
            LOGGER.log(Level.FINE, "save: {0}", milestonesByGroupByJob);
        }

        public Map<String, Map<String, Milestone>> getMilestonesByGroupByJob() {
            return milestonesByGroupByJob;
        }

        public Map<String, Map<Integer, Milestone>> milestonesByOrdinalByJob() {
            return milestonesByOrdinalByJob;
        }

    }

    public enum MilestonePolicy {
        CONTINUE_OLD_BUILDS,
        CANCEL_OLD_BUILDS
    }

}

