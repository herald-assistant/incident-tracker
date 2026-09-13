package pl.mkn.tdw.features.operationalcontextassistance.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record OperationalContextAssistanceProposalDecisionRequest(
        @NotNull Action action,
        List<String> selectedPaths,
        List<String> confirmedPaths
) {
    public OperationalContextAssistanceProposalDecisionRequest {
        selectedPaths = selectedPaths != null ? List.copyOf(selectedPaths) : List.of();
        confirmedPaths = confirmedPaths != null ? List.copyOf(confirmedPaths) : List.of();
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object ignored) {
        throw new IllegalArgumentException("Unknown assistance decision field: " + field);
    }

    public enum Action { APPLY, SKIP }
}
