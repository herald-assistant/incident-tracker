package pl.mkn.tdw.features.operationalcontextassistance.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record OperationalContextAssistanceProposalDecisionRequest(
        @NotNull Action action,
        List<String> selectedPaths,
        List<String> confirmedPaths,
        Map<String, JsonNode> editedValues
) {
    public OperationalContextAssistanceProposalDecisionRequest {
        selectedPaths = selectedPaths != null ? List.copyOf(selectedPaths) : List.of();
        confirmedPaths = confirmedPaths != null ? List.copyOf(confirmedPaths) : List.of();
        var copy = new LinkedHashMap<String, JsonNode>();
        if (editedValues != null) {
            editedValues.forEach((path, value) -> copy.put(path, value != null ? value.deepCopy() : null));
        }
        editedValues = Collections.unmodifiableMap(copy);
    }

    public OperationalContextAssistanceProposalDecisionRequest(
            Action action, List<String> selectedPaths, List<String> confirmedPaths
    ) {
        this(action, selectedPaths, confirmedPaths, Map.of());
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object ignored) {
        throw new IllegalArgumentException("Unknown assistance decision field: " + field);
    }

    public enum Action { APPLY, SKIP }
}
