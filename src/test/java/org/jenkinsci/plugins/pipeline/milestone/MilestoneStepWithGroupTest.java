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

public class MilestoneStepWithGroupTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Issue("JENKINS-48510")
    @Test
    public void differentGroupsShouldNotInterference() {
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
                                "milestone label: 'My Label', group: KEY\n" +
                                "node {\n" +
                                "  echo 'First milestone'\n" +
                                "  semaphore 'wait'\n" +
                                "}",true));
                // b2 should cancel b1 as they have same expression argument
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

                // Let #4 continue
                SemaphoreStep.success("wait/4", null);
                story.j.waitForCompletion(b4);

                // Let #3 continue
                SemaphoreStep.success("wait/3", null);
                story.j.waitForCompletion(b3);

                // Let #2 continue. It should finished ok not being interrupted by b3
                SemaphoreStep.success("wait/2", null);
                story.j.waitForCompletion(b2);

                // Once #2 finishes, then #1 is automatically cancelled as they are in the
                // same group
                story.j.assertBuildStatus(Result.NOT_BUILT, story.j.waitForCompletion(b1));

                // #2 should be successfully finished
                story.j.assertBuildStatus(Result.SUCCESS, story.j.waitForCompletion(b2));

                // #3 should be canceled by #4 as they are in the same group
                story.j.assertBuildStatus(Result.NOT_BUILT, story.j.waitForCompletion(b3));

                // #4 should be successfully finished
                story.j.assertBuildStatus(Result.SUCCESS, story.j.waitForCompletion(b4));
            }
        });
    }

    /**
     * verify a chain of builds as jenkins stores milestones objects
     * just to make sure that inSight is cleared correctly, and last build is updated
     * */
    @Issue("JENKINS-48510")
    @Test
    public void milestoneConfigurationExists() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {

                // we need 4 executors on master node
                // otherwise builds hangs in waiting state
                story.j.jenkins.setNumExecutors(2);

                ParametersDefinitionProperty pdpG1 = new ParametersDefinitionProperty(
                        new StringParameterDefinition("KEY", "group1"));

                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.addProperty(pdpG1);
                p.setDefinition(new CpsFlowDefinition(
                        "echo \"KEY=${KEY}\"\n" +
                                "milestone label: 'My Label', group: KEY\n" +
                                "node {\n" +
                                "  echo 'First milestone'\n" +
                                "  semaphore 'wait'\n" +
                                "}",true));
                // b2 should cancel b1 as they have same expression argument
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b1);

                WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/2", b2);

                // Let #2 continue. It should finished ok not being interrupted by b3
                SemaphoreStep.success("wait/2", null);
                story.j.waitForCompletion(b2);

                // Let #4 continue
                SemaphoreStep.success("wait/1", null);
                story.j.waitForCompletion(b1);

                WorkflowRun b3 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/3", b3);

                WorkflowRun b4 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/4", b4);

                // Let #4 continue
                SemaphoreStep.success("wait/4", null);
                story.j.waitForCompletion(b4);

                // Let #3 continue
                SemaphoreStep.success("wait/3", null);
                story.j.waitForCompletion(b3);

                // Once #2 finishes, then #1 is automatically cancelled as they are in the
                // same group
                story.j.assertBuildStatus(Result.NOT_BUILT, story.j.waitForCompletion(b1));

                // #2 should be successfully finished
                story.j.assertBuildStatus(Result.SUCCESS, story.j.waitForCompletion(b2));

                // #3 should be canceled by #4 as they are in the same group
                story.j.assertBuildStatus(Result.NOT_BUILT, story.j.waitForCompletion(b3));

                // #4 should be successfully finished
                story.j.assertBuildStatus(Result.SUCCESS, story.j.waitForCompletion(b4));
            }
        });
    }

    @Issue("JENKINS-48510")
    @Test
    public void defaultGroupValue() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {

                // we need 4 executors on master node
                // otherwise builds hangs in waiting state
                story.j.jenkins.setNumExecutors(2);

                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                                "milestone label: 'My Label', group: null\n" +
                                "node {\n" +
                                "  echo 'First milestone'\n" +
                                "  semaphore 'wait'\n" +
                                "}",true));
                // b2 should cancel b1 as they have same expression argument
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b1);

                WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/2", b2);

                // Let #2 continue. It should finished ok not being interrupted by b3
                SemaphoreStep.success("wait/2", null);
                story.j.waitForCompletion(b2);

                // Once #2 finishes, then #1 is automatically cancelled as they are in the
                // same group
                story.j.assertBuildStatus(Result.NOT_BUILT, story.j.waitForCompletion(b1));

                // #2 should be successfully finished
                story.j.assertBuildStatus(Result.SUCCESS, story.j.waitForCompletion(b2));
            }
        });
    }
}
