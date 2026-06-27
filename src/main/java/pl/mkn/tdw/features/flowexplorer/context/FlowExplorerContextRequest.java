package pl.mkn.tdw.features.flowexplorer.context;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerAnalysisGoal;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerFocusArea;

import java.util.List;
import java.util.Objects;

public record FlowExplorerContextRequest(
        String systemId,
        String endpointId,
        String httpMethod,
        String endpointPath,
        String branch,
        FlowExplorerAnalysisGoal goal,
        List<FlowExplorerFocusArea> focusAreas
) {

    public FlowExplorerContextRequest {
        systemId = normalize(systemId);
        endpointId = normalize(endpointId);
        httpMethod = normalize(httpMethod);
        endpointPath = normalize(endpointPath);
        branch = normalize(branch);
        goal = goal != null
                ? goal
                : FlowExplorerAnalysisGoal.DEEP_DISCOVERY;
        focusAreas = focusAreas != null
                ? focusAreas.stream().filter(Objects::nonNull).toList()
                : List.of();
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
