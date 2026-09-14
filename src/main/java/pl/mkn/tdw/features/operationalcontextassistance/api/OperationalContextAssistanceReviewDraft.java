package pl.mkn.tdw.features.operationalcontextassistance.api;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Operator's unfinished review. No catalogue mutation is implied by saving it. */
public record OperationalContextAssistanceReviewDraft(
        @NotNull List<@Valid Selection> selections
) {
    public OperationalContextAssistanceReviewDraft {
        selections = selections != null ? List.copyOf(selections) : null;
    }

    @JsonAnySetter
    public void rejectUnknownField(String field, Object ignored) {
        throw new IllegalArgumentException("Unknown assistance review field: " + field);
    }

    public record Selection(
            @NotNull List<String> selectedPaths,
            @NotNull List<String> confirmedPaths,
            @NotNull Map<String, JsonNode> editedValues
    ) {
        public Selection {
            selectedPaths = selectedPaths != null ? List.copyOf(selectedPaths) : null;
            confirmedPaths = confirmedPaths != null ? List.copyOf(confirmedPaths) : null;
            if (editedValues != null) {
                var copy = new LinkedHashMap<String, JsonNode>();
                editedValues.forEach((path, value) -> copy.put(path, value != null ? value.deepCopy() : null));
                editedValues = Collections.unmodifiableMap(copy);
            }
        }

        @JsonAnySetter
        public void rejectUnknownField(String field, Object ignored) {
            throw new IllegalArgumentException("Unknown assistance review selection field: " + field);
        }
    }
}
