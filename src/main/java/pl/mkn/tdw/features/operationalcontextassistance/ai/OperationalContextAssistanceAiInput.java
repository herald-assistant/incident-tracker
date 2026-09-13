package pl.mkn.tdw.features.operationalcontextassistance.ai;

import com.fasterxml.jackson.databind.JsonNode;
import pl.mkn.tdw.features.operationalcontextassistance.source.OperationalContextGitLabSourceSnapshot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record OperationalContextAssistanceAiInput(
        OperationalContextAssistanceMode mode,
        String description,
        JsonNode catalogContext,
        Map<String, String> maintenanceGuidance,
        JsonNode targetContext,
        OperationalContextGitLabSourceSnapshot gitLabSource,
        OperationalContextAssistanceRepositoryFacts repositoryFacts,
        List<String> visibilityLimits
) {
    public OperationalContextAssistanceAiInput {
        if (mode == null) {
            throw new IllegalArgumentException("Assistance mode is required.");
        }
        if (description == null || description.isBlank() || description.length() > 4_000) {
            throw new IllegalArgumentException("Operator description must contain 1-4000 characters.");
        }
        if (mode == OperationalContextAssistanceMode.CREATE_AREA && targetContext != null && !targetContext.isNull()) {
            throw new IllegalArgumentException("CREATE_AREA cannot have a target.");
        }
        if (mode != OperationalContextAssistanceMode.CREATE_AREA
                && (targetContext == null || targetContext.isNull() || targetContext.isEmpty())) {
            throw new IllegalArgumentException("Existing entity or finding target is required.");
        }
        if (repositoryFacts != null && (mode != OperationalContextAssistanceMode.CREATE_AREA || gitLabSource == null)) {
            throw new IllegalArgumentException("Repository facts require CREATE_AREA with GitLab source.");
        }
        maintenanceGuidance = maintenanceGuidance != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(maintenanceGuidance)) : Map.of();
        visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
    }
}
