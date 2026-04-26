package pl.mkn.incidenttracker.analysis.ai.copilot.coverage;

public enum GitLabEvidenceCoverage {
    NONE,
    SYMBOL_ONLY,
    STACK_FRAME_ONLY,
    FAILING_METHOD_ONLY,
    DIRECT_COLLABORATOR_ATTACHED,
    FLOW_CONTEXT_ATTACHED,
    SUFFICIENT
}
