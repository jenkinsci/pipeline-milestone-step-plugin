package org.jenkinsci.plugins.pipeline.milestone;

import hudson.model.Result;
import java.util.logging.Level;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.SnippetizerTester;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.junit.jupiter.BuildWatcherExtension;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;

class MilestoneStepTest {

    @SuppressWarnings("unused")
    @RegisterExtension
    private static final BuildWatcherExtension BUILD_WATCHER = new BuildWatcherExtension();

    @RegisterExtension
    private final JenkinsSessionExtension story = new JenkinsSessionExtension();

    @SuppressWarnings("unused")
    private final LogRecorder logRecorder = new LogRecorder().recordPackage(MilestoneStep.class, Level.FINE);

    @Test
    void buildsMustPassThroughInOrder() throws Throwable {
        story.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                    """
                            semaphore 'inorder'
                            echo 'Before milestone'
                            milestone()
                            echo 'Passed first milestone'
                            milestone()
                            echo 'Passed second milestone'
                            milestone()
                            echo 'Passed third milestone'
                        """, true));
            WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("inorder/1", b1);
            WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("inorder/2", b2);

            // Let #2 continue so it finish before #1
            SemaphoreStep.success("inorder/2", null);
            r.assertBuildStatusSuccess(r.waitForCompletion(b2));

            // Let #1 continue, so it must be early cancelled since #2 already passed through milestone 1
            SemaphoreStep.success("inorder/1", null);
            r.assertBuildStatus(Result.NOT_BUILT, r.waitForCompletion(b1));
            r.assertLogNotContains("Passed first milestone", b1);
        });
    }

    @Test
    void olderBuildsMustBeCancelledOnMilestoneExit() throws Throwable {
        story.then(r -> {
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        """
                                milestone()
                                echo 'First milestone'
                                semaphore 'wait'
                                milestone()
                                echo 'Second milestone'
                                """, true));
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b1);
                WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/2", b2);
                // Now both #1 and #2 passed milestone 1

                // Let #2 continue so it goes away from milestone 1 (and passes milestone 2)
                SemaphoreStep.success("wait/2", null);
                r.waitForCompletion(b2);

                // Once #2 continues and passes milestone 2 then #1 is automatically cancelled
                r.assertBuildStatus(Result.NOT_BUILT, r.waitForCompletion(b1));
            });
    }

    @Test
    void olderBuildsMustBeCancelledOnBuildFinish() throws Throwable {
        story.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                    """
                            milestone label: 'My Label'
                            node {
                              echo 'First milestone'
                              semaphore 'wait'
                            }""", true));
            WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/1", b1);
            WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/2", b2);
            // Now both #1 and #2 passed milestone 1

            // Let #2 continue
            SemaphoreStep.success("wait/2", null);
            r.waitForCompletion(b2);

            // Once #2 finishes, so it passes the virtual ad-infinitum milestone, then #1 is automatically cancelled
            r.assertBuildStatus(Result.NOT_BUILT, r.waitForCompletion(b1));
        });
    }

    @Test
    void olderBuildsMustBeCancelledOnBuildAborted() throws Throwable {
        story.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                    """
                            milestone label: 'My Label'
                            node {
                              echo 'First milestone'
                              semaphore 'wait'
                            }""", true));
            WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/1", b1);
            WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/2", b2);
            // Now both #1 and #2 passed milestone 1

            // Abort #2
            b2.getOneOffExecutor().doStop();
            r.waitForMessage("Finished: ABORTED", b2);

            r.assertBuildStatus(Result.NOT_BUILT, r.waitForCompletion(b1)); // #1 should be cancelled
        });
    }

    @Test
    void milestoneNotAllowedInsideParallel() throws Throwable {
        story.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                    """
                            sleep 1
                            parallel one: { echo 'First' }, two: {\s
                              node {
                                echo 'Test'
                              }
                              milestone()
                            }
                            milestone()
                            """, true));
            WorkflowRun b1 = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
            r.assertLogContains("Using a milestone step inside parallel is not allowed", b1);

            p.setDefinition(new CpsFlowDefinition(
                    """
                            sleep 1
                            parallel one: { echo 'First' }, two: {\s
                              echo 'Pre-node'
                              node {
                                milestone()
                              }
                            }
                            milestone()
                            """, true));
            WorkflowRun b2 = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
            r.assertLogContains("Using a milestone step inside parallel is not allowed", b2);

            p.setDefinition(new CpsFlowDefinition(
                    """
                            sleep 1
                            parallel one: { echo 'First' }, two: {\s
                              echo 'Pre-node'
                              node {
                                echo 'Inside node'
                              }
                            }
                            milestone()
                            """, true));
            r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        });
    }

    @Test
    void unsafeMilestoneAllowedInsideParallel() throws Throwable {
        story.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                    """
                            sleep 1
                            parallel one: { echo 'First' }, two: {\s
                              node {
                                echo 'Test'
                              }
                              milestone unsafe: true
                            }
                            milestone()
                            """, true));
            r.assertBuildStatusSuccess(p.scheduleBuild2(0));

            p.setDefinition(new CpsFlowDefinition(
                    """
                            sleep 1
                            parallel one: { echo 'First' }, two: {\s
                              echo 'Pre-node'
                              node {
                                milestone unsafe: true
                              }
                            }
                            milestone()
                            """, true));
            r.assertBuildStatusSuccess(p.scheduleBuild2(0));

            p.setDefinition(new CpsFlowDefinition(
                    """
                            sleep 1
                            parallel one: { echo 'First' }, two: {\s
                              echo 'Pre-node'
                              node {
                                echo 'Inside node'
                              }
                            }
                            milestone()
                            """, true));
            r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        });
    }

    @Issue("JENKINS-38464")
    @Test
    void milestoneAllowedOutsideParallel() throws Throwable {
        story.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                    """
                            node {
                              parallel one: { echo 'First' }, two: {\s
                                echo 'In-node'
                              }
                              milestone 1
                            }""", true));
            r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        });
    }

    @Test
    void ordinals() throws Throwable {
        story.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                    """
                            milestone()
                            node {
                              milestone ordinal: 1
                            }
                            milestone()
                            milestone 5
                            milestone()""", true));
            r.assertBuildStatusSuccess(p.scheduleBuild2(0));

            p.setDefinition(new CpsFlowDefinition(
                    """
                            milestone()
                            node {
                              milestone()
                            }
                            milestone()
                            milestone ordinal: 2""", true)); // Invalid ordinal
            r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
        });
    }

    @Test
    void configRoundtrip() throws Throwable {
        story.then(r -> {
            SnippetizerTester t = new SnippetizerTester(r);
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
        });
    }

    /**
     * Sanity test for {@link MilestoneStepExecution.Listener}.
     */
    @Test
    void notUsingMilestone() throws Throwable {
        story.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition("""
                    semaphore 'blocked'
                    """, true));
            WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("blocked/1", b1);
            WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("blocked/2", b1);
            SemaphoreStep.success("blocked/2", null);
            r.assertBuildStatusSuccess(r.waitForCompletion(b2));
            SemaphoreStep.success("blocked/1", null);
            r.assertBuildStatusSuccess(r.waitForCompletion(b1));
        });
    }

    /**
     * Sanity test for {@link MilestoneStepExecution.FlowExecutionListenerImpl}.
     */
    @Test
    void notUsingMilestoneWithResume() throws Throwable {
        story.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition("""
                    semaphore 'blocked'
                    """, true));
            WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("blocked/1", b1);
            WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("blocked/2", b2);
        });
        story.then(r -> {
            WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
            WorkflowRun b1 = p.getBuildByNumber(1);
            WorkflowRun b2 = p.getBuildByNumber(2);
            SemaphoreStep.success("blocked/2", null);
            r.assertBuildStatusSuccess(r.waitForCompletion(b2));
            SemaphoreStep.success("blocked/1", null);
            r.assertBuildStatusSuccess(r.waitForCompletion(b1));
        });
    }

    @Test
    void reachSameMilestone() throws Throwable {
        story.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                    """
                                    milestone()
                                    semaphore 'inorder'
                                    milestone()
                        """, true));
            WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("inorder/1", b1);
            WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("inorder/2", b2);
            SemaphoreStep.success("inorder/1", null);
            r.assertBuildStatusSuccess(r.waitForCompletion(b1));
            SemaphoreStep.success("inorder/2", null);
            r.assertBuildStatusSuccess(r.waitForCompletion(b2));
        });
    }

    @Test
    void reloadJobWhileRunning() throws Throwable {
        story.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                    """
                            echo 'Before milestone'
                            milestone()
                            echo 'Passed first milestone'
                            milestone()
                            semaphore 'inorder'
                            echo 'Passed second milestone'
                            milestone()
                            echo 'Passed third milestone'
                        """, true));
            WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("inorder/1", b1);
            WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("inorder/2", b2);
            // Simulate a project reload.
            p.load();
            // Let #2 continue so it finish before #1
            SemaphoreStep.success("inorder/2", null);
            r.assertBuildStatusSuccess(r.waitForCompletion(b2));

            // Let #1 continue, so it must be early cancelled since #2 already passed through milestone 1
            SemaphoreStep.success("inorder/1", null);
            r.assertBuildStatus(Result.NOT_BUILT, r.waitForCompletion(b1));
            r.assertLogNotContains("Passed second milestone", b1);
        });
    }

    @Test
    void buildsMustPassThroughInOrderWithRestart() throws Throwable {
        story.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                    """
                                semaphore 'inorder'
                                echo 'Before milestone'
                                milestone()
                                echo 'Passed first milestone'
                                milestone()
                                echo 'Passed second milestone'
                                milestone()
                                echo 'Passed third milestone'
                            """, true));
            WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("inorder/1", b1);
            WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("inorder/2", b2);
        });
        story.then( r -> {
            WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
            WorkflowRun b1 = p.getBuildByNumber(1);
            WorkflowRun b2 = p.getBuildByNumber(2);
            // Let #2 continue so it finish before #1
            SemaphoreStep.success("inorder/2", null);
            r.assertBuildStatusSuccess(r.waitForCompletion(b2));

            // Let #1 continue, so it must be early cancelled since #2 already passed through milestone 1
            SemaphoreStep.success("inorder/1", null);
            r.assertBuildStatus(Result.NOT_BUILT, r.waitForCompletion(b1));
            r.assertLogNotContains("Passed first milestone", b1);
        });
    }

    @Issue("JENKINS-75668")
    @Test
    void olderBuildsAtLaterMilestonesMustNotBeCancelledOnBuildFinish() throws Throwable {
      story.then(r -> {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                        milestone()
                        semaphore 'wait_a'
                        if (currentBuild.number == 2) { error 'failure' }
                        milestone()
                        semaphore 'wait_b'
                        """, true));
        WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("wait_a/1", b1);
        
        // Let #1 continue until the the second milestone
        SemaphoreStep.success("wait_a/1", null);
        SemaphoreStep.waitForStart("wait_b/1", b1);

        WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("wait_a/2", b2);        
        
        // Let #2 continue and error
        SemaphoreStep.success("wait_a/2", null);
        r.assertBuildStatus(Result.FAILURE, r.waitForCompletion(b2));

        // Let #1 finish
        SemaphoreStep.success("wait_b/1", null);

        // Confirm that #1 was not canceled
        r.assertBuildStatus(Result.SUCCESS, r.waitForCompletion(b1));
    });
    }
}
