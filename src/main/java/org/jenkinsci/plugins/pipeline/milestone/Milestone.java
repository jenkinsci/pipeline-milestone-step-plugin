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

import java.util.Set;
import java.util.TreeSet;

import edu.umd.cs.findbugs.annotations.CheckForNull;

import org.jenkinsci.plugins.workflow.steps.StepContext;

import hudson.model.Run;

class Milestone {

    /**
     * Milestone ordinal.
     */
    final Integer ordinal;

    /**
     * milestone group.
     */
    final String group;

    /**
     * Numbers of builds that passed this milestone but haven't passed the next one.
     */
    final Set<Integer> inSight = new TreeSet<Integer>();

    /**
     * Last build that passed through the milestone, or null if none passed yet.
     */
    @CheckForNull
    Integer lastBuild;

    Milestone(Integer ordinal, String group) {
        this.ordinal = ordinal;
        this.group = group;
    }

    @Override public String toString() {
        return "Milestone[inSight=" + inSight
                +", group="+group
                +", ordinal="+ordinal
                +", lastBuild="+lastBuild+"]";
    }

    public void pass(StepContext context, Run<?, ?> build) {
        lastBuild = build.getNumber();
        inSight.add(build.getNumber());
    }

    /**
     * Called when a build passes the next milestone. remove build number if it was in sight
     *
     * @param build the build passing the next milestone.
     */
    public synchronized void wentAway(Run<?, ?> build) {
        if (inSight.contains(build.getNumber())) {
            inSight.remove(build.getNumber()); // XSTR-757
        }
    }
    /**
     * Check if build is in sight
     *
     * @param build build to look for
     * @return true if the build is in sight (exists in inSight), false otherwise.
     */
    public synchronized boolean isInSight(Run<?, ?> build) {
        if (inSight.contains(build.getNumber())) {
            return true;
        } else {
            return false;
        }
    }
}
