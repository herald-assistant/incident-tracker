package pl.mkn.tdw.features.operationalcontextassistance.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** One operator choice per proposed entity, in draft order. */
public record OperationalContextAssistanceBatchReviewRequest(
        @NotNull List<@Valid OperationalContextAssistanceProposalDecisionRequest> decisions,
        String candidateDigest
) {
    public OperationalContextAssistanceBatchReviewRequest {
        decisions = decisions != null ? List.copyOf(decisions) : null;
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object ignored) {
        throw new IllegalArgumentException("Unknown assistance batch review field: " + field);
    }
}
