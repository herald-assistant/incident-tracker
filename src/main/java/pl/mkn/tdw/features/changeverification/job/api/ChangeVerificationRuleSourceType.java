package pl.mkn.tdw.features.changeverification.job.api;

public enum ChangeVerificationRuleSourceType {
    ACCEPTANCE_CRITERION,
    JIRA_DESCRIPTION,
    JIRA_COMMENT,
    CONFLUENCE,
    REPOSITORY_INSTRUCTION,
    OPERATOR_INSTRUCTION,
    AI_SUGGESTION
}
