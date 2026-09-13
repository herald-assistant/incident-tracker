package pl.mkn.tdw.features.operationalcontextassistance.api;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record OperationalContextAssistanceProposalDecision(
        int proposalIndex,
        OperationalContextAssistanceProposalDecisionRequest.Action action,
        List<String> selectedPaths,
        Map<String, JsonNode> editedValues,
        Instant completedAt,
        String catalogDigest
) {
    public OperationalContextAssistanceProposalDecision {
        selectedPaths = selectedPaths != null ? List.copyOf(selectedPaths) : List.of();
        var copy = new LinkedHashMap<String, JsonNode>();
        if (editedValues != null) {
            editedValues.forEach((path, value) -> copy.put(path, value != null ? value.deepCopy() : null));
        }
        editedValues = Collections.unmodifiableMap(copy);
    }
}
