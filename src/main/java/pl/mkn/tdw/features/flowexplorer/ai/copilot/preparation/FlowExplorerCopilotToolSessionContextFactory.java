package pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.features.flowexplorer.ai.preparation.FlowExplorerPromptPreparation;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerRepositoryContext;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStartRequest;

import java.util.LinkedHashMap;
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
        return create(runReference, request, contextSnapshot, preparation, false);
    }

    public CopilotToolSessionContext create(
            String runReference,
            FlowExplorerJobStartRequest request,
            FlowExplorerContextSnapshot contextSnapshot,
            FlowExplorerPromptPreparation preparation,
            boolean followUp
    ) {
        return create(runReference, null, request, contextSnapshot, preparation, followUp);
    }

    public CopilotToolSessionContext create(
            String runReference,
            String copilotSessionId,
            FlowExplorerJobStartRequest request,
            FlowExplorerContextSnapshot contextSnapshot,
            FlowExplorerPromptPreparation preparation,
            boolean followUp
    ) {
        var normalizedRunReference = normalizeRunReference(runReference);

        return new CopilotToolSessionContext(
                normalizedRunReference,
                normalizeCopilotSessionId(copilotSessionId, normalizedRunReference),
                hiddenContext(contextSnapshot, followUp)
        );
    }

    private Map<String, Object> hiddenContext(FlowExplorerContextSnapshot contextSnapshot, boolean followUp) {
        var context = new LinkedHashMap<String, Object>();
        context.put(FlowExplorerCopilotToolContextKeys.FEATURE, FlowExplorerCopilotToolContextKeys.FEATURE_VALUE);
        context.put(
                FlowExplorerCopilotToolContextKeys.RUN_KIND,
                followUp
                        ? FlowExplorerCopilotToolContextKeys.RUN_KIND_FOLLOW_UP
                        : FlowExplorerCopilotToolContextKeys.RUN_KIND_INITIAL
        );
        context.put(
                FlowExplorerCopilotToolContextKeys.ENDPOINT_CONTEXT_EMBEDDED,
                endpointContextEmbedded(contextSnapshot)
        );
        context.put(
                FlowExplorerCopilotToolContextKeys.REPOSITORY_SCOPE_RESOLVED,
                repositoryScopeResolved(contextSnapshot)
        );
        return context;
    }

    private boolean endpointContextEmbedded(FlowExplorerContextSnapshot contextSnapshot) {
        return contextSnapshot != null
                && contextSnapshot.coverage() != null
                && contextSnapshot.coverage().endpointResolved();
    }

    private boolean repositoryScopeResolved(FlowExplorerContextSnapshot contextSnapshot) {
        return contextSnapshot != null
                && contextSnapshot.repositories().stream()
                .map(FlowExplorerRepositoryContext::projectName)
                .anyMatch(StringUtils::hasText);
    }

    private static String normalizeRunReference(String runReference) {
        if (!StringUtils.hasText(runReference)) {
            return UUID.randomUUID().toString();
        }
        return runReference.trim();
    }

    private static String normalizeCopilotSessionId(String copilotSessionId, String normalizedRunReference) {
        return StringUtils.hasText(copilotSessionId)
                ? copilotSessionId.trim()
                : SESSION_ID_PREFIX + normalizedRunReference;
    }
}
