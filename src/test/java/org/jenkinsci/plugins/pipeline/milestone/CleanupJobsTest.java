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

import hudson.logging.LogRecorder;
import hudson.logging.LogRecorderManager;
import hudson.model.listeners.ItemListener;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertFalse;

@WithJenkins
class CleanupJobsTest {

    private JenkinsRule j;

    @BeforeEach
    void beforeEach(JenkinsRule rule) {
        j = rule;
    }

    @Test
    void cleanupTrackedJobsOnDeleted() throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("milestone 1;milestone 2", true));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        p.delete();

        p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("milestone 1;milestone 2", true));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0)); // build #1 is not cancelled
    }

    @Issue("JENKINS-41311")
    @Test
    void noNPEOnCleanup() throws Exception {
        LogRecorder recorder = new LogRecorder("recorder");
        LogRecorderManager mgr = j.jenkins.getLog();
        LogRecorder.Target t = new LogRecorder.Target(ItemListener.class.getName(), Level.ALL);
        recorder.getLoggers().add(t);
        recorder.save();
        t.enable();
        mgr.getRecorders().add(recorder);

        j.createFreeStyleProject().delete();
        for (LogRecord r : recorder.getLogRecords()) {
            assertFalse(r.getMessage().contains("failed to send event to listener of class org.jenkinsci.plugins.pipeline.milestone.MilestoneStepExecution$CleanupJobsOnDelete"));
        }
    }
}
