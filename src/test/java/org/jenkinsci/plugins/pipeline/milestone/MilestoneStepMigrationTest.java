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
import org.jvnet.hudson.test.recipes.LocalData;
import org.jvnet.hudson.test.recipes.WithTimeout;

public class MilestoneStepMigrationTest {


    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();


    @Test
    //@WithTimeout(60)
    @LocalData("migration_1_3to1_4")
    public void migration_from_1_3to1_4() {
        story.then(r -> {
            WorkflowJob p = (WorkflowJob) story.j.jenkins.getItem("p");
            WorkflowRun b3 = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/2", b3);
            SemaphoreStep.success("wait/2", null);
        });
    }


}
