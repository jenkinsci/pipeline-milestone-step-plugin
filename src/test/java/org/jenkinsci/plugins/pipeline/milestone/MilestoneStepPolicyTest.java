package org.jenkinsci.plugins.pipeline.milestone;

import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class MilestoneStepPolicyTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    /**
     * Different groups should not interference
     *
     * If milestone policy set to cancel old builds
     * b1 passed milestone default#0
     * b2 passed milestone default#0
     * b1 should be canceled by b2
     *
     *
     * */
    @Issue("JENKINS-43353")
    @Test
    public void olderBuildsMustBeCancelled() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {

                // we need 4 executors on master node
                // otherwise builds hangs in waiting state
                story.j.jenkins.setNumExecutors(2);

                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                                "node {\n" +
                                "semaphore 'wait'\n" +
                                "milestone label: 'My Label', policy: 'CANCEL_OLD_BUILDS'\n" +
                                "  echo 'First milestone'\n" +
                                "  sleep(time: 5, unit: 'SECONDS' )\n" +
                                "}",true));
                // b2 should cancel b1 as they have same expression argument
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b1);

                WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/2", b2);


                // Let #2 continue. It should finished ok not being interrupted by b3
                SemaphoreStep.success("wait/2", null);

                // Let #1 continue.
                SemaphoreStep.success("wait/1", null);


                // Once #2 passed milestone, then #1 is automatically cancelled
                story.j.assertBuildStatus(Result.NOT_BUILT, story.j.waitForCompletion(b1));

                // #2 should be successfully finished
                story.j.assertBuildStatus(Result.SUCCESS, story.j.waitForCompletion(b2));
            }
        });
    }

    /**
     * Different groups should not interference
     *
     * If milestone policy set to cancel old builds
     * b1 passed milestone default#0
     * b2 passed milestone default#0
     * b1 should not be canceled by b2 as it has already passed the milestone
     *
     *
     * */
    @Issue("JENKINS-43353")
    @Test
    public void DefaultPolicyShouldKeepOldBuildsRunning() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {

                // we need 4 executors on master node
                // otherwise builds hangs in waiting state
                story.j.jenkins.setNumExecutors(2);

                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                          "node {\n" +
                                "milestone label: 'My Label'\n" +
                                "semaphore 'wait'\n" +
                                "  echo 'First milestone'\n" +
                                "  sleep(time: 5, unit: 'SECONDS' )\n" +
                                "}",true));
                // b2 should cancel b1 as they have same expression argument
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b1);

                WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/2", b2);


                // Let #2 continue. It should finished ok not being interrupted by b3
                SemaphoreStep.success("wait/2", null);

                // Let #1 continue.
                SemaphoreStep.success("wait/1", null);


                // Once #2 passed milestone, then #1 is automatically cancelled
                story.j.assertBuildStatus(Result.SUCCESS, story.j.waitForCompletion(b1));

                // #2 should be successfully finished
                story.j.assertBuildStatus(Result.SUCCESS, story.j.waitForCompletion(b2));
            }
        });
    }

    /**
     * Different groups should not interference
     *
     * If milestone policy set to cancel old builds
     * b1 passed milestone default#0
     * b2 passed milestone default#0
     * b1 should not be canceled by b2 as it has already passed the milestone
     *
     *
     * */
    @Issue("JENKINS-43353")
    @Test
    public void keepOldBuildsRunning() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {

                // we need 4 executors on master node
                // otherwise builds hangs in waiting state
                story.j.jenkins.setNumExecutors(2);

                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                                "node {\n" +
                                "milestone label: 'My Label'\n" +
                                "semaphore 'wait'\n" +
                                "  echo 'First milestone'\n" +
                                "  sleep(time: 5, unit: 'SECONDS' )\n" +
                                "}",true));
                // b2 should cancel b1 as they have same expression argument
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b1);

                WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/2", b2);


                // Let #2 continue. It should finished ok not being interrupted by b3
                SemaphoreStep.success("wait/2", null);

                // Let #1 continue.
                SemaphoreStep.success("wait/1", null);


                // Once #2 passed milestone, then #1 is automatically cancelled
                story.j.assertBuildStatus(Result.SUCCESS, story.j.waitForCompletion(b1));

                // #2 should be successfully finished
                story.j.assertBuildStatus(Result.SUCCESS, story.j.waitForCompletion(b2));
            }
        });
    }

    /**
     * Different groups should not interference
     *
     * If milestone policy set to cancel old builds
     * b1 passed milestone group1#0
     * b2 passed milestone group1#0
     * b1 should be canceled by b2
     *
     * b3 passed milestone group2#0
     * b4 passed milestone group2#0
     * b3 should be canceled by b4
     *
     * */
    @Issue("JENKINS-43353")
    @Test
    public void olderBuildsMustBeCancelledInScopeOfOneGroup() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {

                // we need 4 executors on master node
                // otherwise builds hangs in waiting state
                story.j.jenkins.setNumExecutors(4);

                ParametersDefinitionProperty pdpG1 = new ParametersDefinitionProperty(
                        new StringParameterDefinition("KEY", "group1"));

                ParametersDefinitionProperty pdpG2 = new ParametersDefinitionProperty(
                        new StringParameterDefinition("KEY", "group2"));

                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.addProperty(pdpG1);
                p.setDefinition(new CpsFlowDefinition(
                        "echo \"KEY=${KEY}\"\n" +
                                "node {\n" +
                                "milestone label: 'My Label', group: KEY, policy: 'CANCEL_OLD_BUILDS'\n" +
                                "semaphore 'wait'\n" +
                                "  echo 'First milestone'\n" +
                                "  sleep(time: 5, unit: 'SECONDS' )\n" +
                                "}",true));

                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b1);

                WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/2", b2);

                p.removeProperty(pdpG1);
                p.addProperty(pdpG2);
                WorkflowRun b3 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/3", b3);

                WorkflowRun b4 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/4", b4);


                // Let #3 continue
                SemaphoreStep.success("wait/3", null);

                // Let #4 continue
                SemaphoreStep.success("wait/4", null);

                // Let #2 continue. It should finished ok not being interrupted by b3
                SemaphoreStep.success("wait/2", null);

                // Let #1 continue.
                SemaphoreStep.success("wait/1", null);

                // #1 should be canceled by #2 as they are of the same group
                story.j.assertBuildStatus(Result.NOT_BUILT, story.j.waitForCompletion(b1));

                // #2 should be successfully finished
                story.j.assertBuildStatus(Result.SUCCESS, story.j.waitForCompletion(b2));

                // #3 should be canceled by #4 as they are of the same group
                story.j.assertBuildStatus(Result.NOT_BUILT, story.j.waitForCompletion(b3));

                // #4 should be successfully finished
                story.j.assertBuildStatus(Result.SUCCESS, story.j.waitForCompletion(b4));
            }
        });
    }
}
