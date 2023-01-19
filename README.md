# Pipeline Milestone Step Plugin

Provides the milestone step in Pipeline scripts.

By default, Pipeline builds can run concurrently. The milestone step
ensures an older build will not override a newer build, so the older
build will never be allowed to pass a `milestone` (it is aborted) if a
newer build already passed it.

This step is specially useful in **Continuous Delivery pipelines**,
where builds ordering is very important as the order defines what is
going to be delivered, so it provides a control point that **aborts any
build trying to deliver an old commit when a newer one is already being
delivered**.

In general this step grants:

-   Builds pass milestones in order (taking the build number as sorter
    field).
-   Older builds will not proceed (they are aborted) if a newer one
    already passed the milestone.
-   When a build passes a milestone, any older build that passed the
    previous milestone but not this one is aborted (see TIP 2 below).

There are two optional parameters:

-   `ordinal`: sequential number which makes milestones comparable. For
    any given two consecutive milestones, ordinal for the second
    milestone must be a greater integer than the ordinal for the first
    one. If not specified an auto-incremented ordinal is internally
    generated.
-   `label`: just for displaying purposes in Pipeline visual
    representations (provided by other plugins).

This examples are both valid:

    milestone 1
    ... pipeline code here

    milestone 2
    ... more pipeline code

    milestone 3
    ... and more

    milestone 100
    ... pipeline code here

    milestone 200
    ... more pipeline code

    milestone 300
    ... and more

**TIP 1**: A combination of this step and the `lock` step can be used to
configure a reliable Continuous Delivery pipeline as both delivering
latest code status and unique deployments are granted. There is an
example of this pattern in the [Pipeline CD demo
Jenkinsfile](https://github.com/jenkinsci/workflow-aggregator-plugin/blob/0c46fa697ffd8a1ca61b87d51ec91ea4a0746453/demo/repo/Jenkinsfile).

**TIP 2**: retroactive builds abort, which is older builds being
cancelled once a newer one passes a milestone that the older builds has
not passed yet. For example, given this script:

    milestone 1
    input message: 'Continue?'
    milestone 2

If three builds (\#1, \#2 and \#3) start concurrently they are all going
to stop on `input` step waiting for user interaction. If the user allows
build \#3 to proceed (so it passes milestone 2) then builds \#2 and \#1
will be automatically cancelled.
