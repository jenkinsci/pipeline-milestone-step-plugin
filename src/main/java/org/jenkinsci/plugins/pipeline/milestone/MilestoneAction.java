package org.jenkinsci.plugins.pipeline.milestone;

import org.jenkinsci.plugins.workflow.actions.StageAction;

import hudson.model.InvisibleAction;

class MilestoneAction extends InvisibleAction implements StageAction {

    private final String label;

    MilestoneAction(String label) {
        this.label = label;
    }

    @Override 
    public String getStageName() {
        return label;
    }
}
