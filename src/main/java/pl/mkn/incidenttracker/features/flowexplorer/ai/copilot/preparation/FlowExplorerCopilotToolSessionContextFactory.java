package pl.mkn.incidenttracker.features.flowexplorer.ai.copilot.preparation;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.incidenttracker.features.flowexplorer.ai.preparation.FlowExplorerPromptPreparation;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerJobStartRequest;

import java.util.Map;
import java.util.UUID;

@Component
public class FlowExplorerCopilotToolSessionContextFactory {

    private static final String SESSION_ID_PREFIX = "flow-explorer-";

    public CopilotToolSessionContext create(
            String runReference,
            FlowExplorerJobStartRequest request,
            FlowExplorerContextSnapshot contextSnapshot,
            FlowExplorerPromptPreparation preparation
    ) {
        var normalizedRunReference = normalizeRunReference(runReference);

        return new CopilotToolSessionContext(
                normalizedRunReference,
                SESSION_ID_PREFIX + normalizedRunReference,
                Map.of()
        );
    }

    private static String normalizeRunReference(String runReference) {
        if (!StringUtils.hasText(runReference)) {
            return UUID.randomUUID().toString();
        }
        return runReference.trim();
    }
}
