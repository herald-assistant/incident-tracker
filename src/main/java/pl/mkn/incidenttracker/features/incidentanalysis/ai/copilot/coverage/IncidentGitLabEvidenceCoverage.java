package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.coverage;

public enum IncidentGitLabEvidenceCoverage {
    NONE,
    SYMBOL_ONLY,
    STACK_FRAME_ONLY,
    FAILING_METHOD_ONLY,
    DIRECT_COLLABORATOR_ATTACHED,
    FLOW_CONTEXT_ATTACHED,
    SUFFICIENT
}
