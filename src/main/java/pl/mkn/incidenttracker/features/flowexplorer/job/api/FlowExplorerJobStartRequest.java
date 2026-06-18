package pl.mkn.incidenttracker.features.flowexplorer.job.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.shared.ai.AnalysisAiOptions;

import java.util.List;
import java.util.Objects;

public record FlowExplorerJobStartRequest(
        @NotBlank(message = "systemId must not be blank")
        @Size(max = 120, message = "systemId must not exceed 120 characters")
        String systemId,
        @Size(max = 240, message = "endpointId must not exceed 240 characters")
        String endpointId,
        @Size(max = 20, message = "httpMethod must not exceed 20 characters")
        String httpMethod,
        @Size(max = 500, message = "endpointPath must not exceed 500 characters")
        String endpointPath,
        @Size(max = 160, message = "branch must not exceed 160 characters")
        String branch,
        FlowExplorerDocumentationPreset documentationPreset,
        @Size(max = 6, message = "focusAreas must contain at most 6 values")
        List<FlowExplorerFocusArea> focusAreas,
        @Size(max = 4000, message = "userInstructions must not exceed 4000 characters")
        String userInstructions,
        @Size(max = 80, message = "model must not exceed 80 characters")
        String model,
        @Size(max = 40, message = "reasoningEffort must not exceed 40 characters")
        String reasoningEffort
) {

    public FlowExplorerJobStartRequest {
        systemId = normalize(systemId);
        endpointId = normalize(endpointId);
        httpMethod = normalize(httpMethod);
        endpointPath = normalize(endpointPath);
        branch = normalize(branch);
        documentationPreset = documentationPreset != null
                ? documentationPreset
                : FlowExplorerDocumentationPreset.ANALYST_OVERVIEW;
        focusAreas = focusAreas != null
                ? focusAreas.stream().filter(Objects::nonNull).toList()
                : List.of();
        userInstructions = normalize(userInstructions);
        model = normalize(model);
        reasoningEffort = normalize(reasoningEffort);
    }

    @AssertTrue(message = "endpointId or both httpMethod and endpointPath must be provided")
    public boolean isEndpointSelectorPresent() {
        return StringUtils.hasText(endpointId)
                || (StringUtils.hasText(httpMethod) && StringUtils.hasText(endpointPath));
    }

    public AnalysisAiOptions aiOptions() {
        return new AnalysisAiOptions(model, reasoningEffort);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
