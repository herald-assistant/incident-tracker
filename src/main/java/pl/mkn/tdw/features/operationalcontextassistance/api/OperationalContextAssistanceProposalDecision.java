package pl.mkn.tdw.features.operationalcontextassistance.api;

import java.time.Instant;
import java.util.List;

public record OperationalContextAssistanceProposalDecision(
        int proposalIndex,
        OperationalContextAssistanceProposalDecisionRequest.Action action,
        List<String> selectedPaths,
        Instant completedAt,
        String catalogDigest
) {
    public OperationalContextAssistanceProposalDecision {
        selectedPaths = selectedPaths != null ? List.copyOf(selectedPaths) : List.of();
    }
}
