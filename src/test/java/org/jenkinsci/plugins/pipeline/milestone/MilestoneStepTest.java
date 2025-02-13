package org.jenkinsci.plugins.pipeline.milestone;

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class MilestoneStepTest {

    @Test
    void buildsMustPassThroughInOrder(JenkinsRule j) throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "semaphore 'inorder'\n" +
                        "echo 'Before milestone'\n" +
                        "milestone()\n" +
                        "echo 'Passed first milestone'\n" +
                        "milestone()\n" +
                        "echo 'Passed second milestone'\n" +
                        "milestone()\n" +
                        "echo 'Passed third milestone'\n", true));
        WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("inorder/1", b1);
        WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("inorder/2", b2);

        // Let #2 continue so it finish before #1
        SemaphoreStep.success("inorder/2", null);
        j.assertBuildStatusSuccess(j.waitForCompletion(b2));

        // Let #1 continue, so it must be early cancelled since #2 already passed through milestone 1
        SemaphoreStep.success("inorder/1", null);
        j.assertBuildStatus(Result.NOT_BUILT, j.waitForCompletion(b1));
        j.assertLogNotContains("Passed first milestone", b1);
    }

    @Test
    void olderBuildsMustBeCancelledOnMilestoneExit(JenkinsRule j) throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "milestone()\n" +
                        "echo 'First milestone'\n" +
                        "semaphore 'wait'\n" +
                        "milestone()\n" +
                        "echo 'Second milestone'\n", true));
        WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("wait/1", b1);
        WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("wait/2", b2);
        // Now both #1 and #2 passed milestone 1

        // Let #2 continue so it goes away from milestone 1 (and passes milestone 2)
        SemaphoreStep.success("wait/2", null);
        j.waitForCompletion(b2);

        // Once #2 continues and passes milestone 2 then #1 is automatically cancelled
        j.assertBuildStatus(Result.NOT_BUILT, j.waitForCompletion(b1));
    }

    @Test
    void olderBuildsMustBeCancelledOnBuildFinish(JenkinsRule j) throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "milestone label: 'My Label'\n" +
                        "node {\n" +
                        "  echo 'First milestone'\n" +
                        "  semaphore 'wait'\n" +
                        "}", true));
        WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("wait/1", b1);
        WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("wait/2", b2);
        // Now both #1 and #2 passed milestone 1

        // Let #2 continue
        SemaphoreStep.success("wait/2", null);
        j.waitForCompletion(b2);

        // Once #2 finishes, so it passes the virtual ad-inifinitum milestone, then #1 is automatically cancelled
        j.assertBuildStatus(Result.NOT_BUILT, j.waitForCompletion(b1));
    }

    @Test
    void olderBuildsMustBeCancelledOnBuildAborted(JenkinsRule j) throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "milestone label: 'My Label'\n" +
                        "node {\n" +
                        "  echo 'First milestone'\n" +
                        "  semaphore 'wait'\n" +
                        "}", true));
        WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("wait/1", b1);
        WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("wait/2", b2);
        // Now both #1 and #2 passed milestone 1

        // Abort #2
        b2.getOneOffExecutor().doStop();
        j.waitForMessage("Finished: ABORTED", b2);

        j.assertBuildStatus(Result.NOT_BUILT, j.waitForCompletion(b1)); // #1 shuould be cancelled
    }

    @Test
    void milestoneNotAllowedInsideParallel(JenkinsRule j) throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "sleep 1\n" +
                        "parallel one: { echo 'First' }, two: { \n" +
                        "  node {\n" +
                        "    echo 'Test'\n" +
                        "  }\n" +
                        "  milestone()\n" +
                        "}\n" +
                        "milestone()\n", true));
        WorkflowRun b1 = j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        j.assertLogContains("Using a milestone step inside parallel is not allowed", b1);

        p.setDefinition(new CpsFlowDefinition(
                "sleep 1\n" +
                        "parallel one: { echo 'First' }, two: { \n" +
                        "  echo 'Pre-node'\n" +
                        "  node {\n" +
                        "    milestone()\n" +
                        "  }\n" +
                        "}\n" +
                        "milestone()\n", true));
        WorkflowRun b2 = j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        j.assertLogContains("Using a milestone step inside parallel is not allowed", b2);

        p.setDefinition(new CpsFlowDefinition(
                "sleep 1\n" +
                        "parallel one: { echo 'First' }, two: { \n" +
                        "  echo 'Pre-node'\n" +
                        "  node {\n" +
                        "    echo 'Inside node'\n" +
                        "  }\n" +
                        "}\n" +
                        "milestone()\n", true));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    }

    @Test
    void unsafeMilestoneAllowedInsideParallel(JenkinsRule j) throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "sleep 1\n" +
                        "parallel one: { echo 'First' }, two: { \n" +
                        "  node {\n" +
                        "    echo 'Test'\n" +
                        "  }\n" +
                        "  milestone unsafe: true\n" +
                        "}\n" +
                        "milestone()\n", false));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        p.setDefinition(new CpsFlowDefinition(
                "sleep 1\n" +
                        "parallel one: { echo 'First' }, two: { \n" +
                        "  echo 'Pre-node'\n" +
                        "  node {\n" +
                        "    milestone unsafe: true\n" +
                        "  }\n" +
                        "}\n" +
                        "milestone()\n", false));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        p.setDefinition(new CpsFlowDefinition(
                "sleep 1\n" +
                        "parallel one: { echo 'First' }, two: { \n" +
                        "  echo 'Pre-node'\n" +
                        "  node {\n" +
                        "    echo 'Inside node'\n" +
                        "  }\n" +
                        "}\n" +
                        "milestone()\n", false));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    }

    @Issue("JENKINS-38464")
    @Test
    void milestoneAllowedOutsideParallel(JenkinsRule j) throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                        "  parallel one: { echo 'First' }, two: { \n" +
                        "    echo 'In-node'\n" +
                        "  }\n" +
                        "  milestone 1\n" +
                        "}", true));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
    }

    @Test
    void ordinals(JenkinsRule j) throws Exception {
        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "milestone()\n" +
                        "node {\n" +
                        "  milestone ordinal: 1\n" +
                        "}\n" +
                        "milestone()\n" +
                        "milestone 5\n" +
                        "milestone()", true));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        p.setDefinition(new CpsFlowDefinition(
                "milestone()\n" +
                        "node {\n" +
                        "  milestone()\n" +
                        "}\n" +
                        "milestone()\n" +
                        "milestone ordinal: 2", true)); // Invalid ordinal
        j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
    }

    @Test
    void configRoundtrip(JenkinsRule j) throws Exception {
        SnippetizerTester t = new SnippetizerTester(j);
        MilestoneStep lacksOrdinal = new MilestoneStep(null);
        t.assertRoundTrip(lacksOrdinal, "milestone()");
        MilestoneStep hasOrdinal = new MilestoneStep(1);
        t.assertRoundTrip(hasOrdinal, "milestone 1");
        lacksOrdinal.setLabel("here");
        t.assertRoundTrip(lacksOrdinal, "milestone label: 'here'");
        hasOrdinal.setLabel("here");
        t.assertRoundTrip(hasOrdinal, "milestone label: 'here', ordinal: 1");
        lacksOrdinal.setLabel("");
        t.assertRoundTrip(lacksOrdinal, "milestone()");
        hasOrdinal.setLabel("");
        t.assertRoundTrip(hasOrdinal, "milestone 1");
    }
}
