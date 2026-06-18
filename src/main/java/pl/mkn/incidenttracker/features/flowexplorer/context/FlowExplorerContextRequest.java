package pl.mkn.incidenttracker.features.flowexplorer.context;

import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerDocumentationPreset;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerFocusArea;

import java.util.List;
import java.util.Objects;

public record FlowExplorerContextRequest(
        String systemId,
        String endpointId,
        String httpMethod,
        String endpointPath,
        String branch,
        FlowExplorerDocumentationPreset documentationPreset,
        List<FlowExplorerFocusArea> focusAreas,
        String reason
) {

    public FlowExplorerContextRequest {
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
        reason = normalize(reason);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
