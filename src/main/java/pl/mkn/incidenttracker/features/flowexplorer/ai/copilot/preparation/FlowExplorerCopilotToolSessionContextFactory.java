package pl.mkn.incidenttracker.features.flowexplorer.ai.copilot.preparation;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.agenttools.context.AgentToolContextKeys;
import pl.mkn.incidenttracker.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.incidenttracker.features.flowexplorer.ai.preparation.FlowExplorerPromptPreparation;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerRepositoryContext;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerFocusArea;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerJobStartRequest;

import java.util.LinkedHashMap;
import java.util.List;
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
        var hiddenContext = hiddenContext(normalizedRunReference, request, contextSnapshot, preparation);

        return new CopilotToolSessionContext(
                normalizedRunReference,
                SESSION_ID_PREFIX + normalizedRunReference,
                hiddenContext
        );
    }

    private Map<String, Object> hiddenContext(
            String runReference,
            FlowExplorerJobStartRequest request,
            FlowExplorerContextSnapshot contextSnapshot,
            FlowExplorerPromptPreparation preparation
    ) {
        var context = new LinkedHashMap<String, Object>();
        var selectedRepository = selectedRepository(contextSnapshot);
        var gitLabRef = firstNonBlank(
                contextSnapshot != null ? contextSnapshot.resolvedRef() : null,
                contextSnapshot != null ? contextSnapshot.requestedBranch() : null,
                request != null ? request.branch() : null
        );
        var gitLabGroup = contextSnapshot != null ? contextSnapshot.gitLabGroup() : null;

        putIfUseful(context, AgentToolContextKeys.CORRELATION_ID, runReference);
        putIfUseful(context, AgentToolContextKeys.GITLAB_GROUP, gitLabGroup);
        putIfUseful(context, AgentToolContextKeys.GITLAB_BRANCH, gitLabRef);
        putIfUseful(context, FlowExplorerCopilotHiddenToolContextKeys.RUN_REFERENCE, runReference);
        putIfUseful(context, FlowExplorerCopilotHiddenToolContextKeys.SYSTEM_ID,
                contextSnapshot != null ? contextSnapshot.systemId() : request != null ? request.systemId() : null);
        putIfUseful(context, FlowExplorerCopilotHiddenToolContextKeys.SYSTEM_NAME,
                contextSnapshot != null ? contextSnapshot.systemName() : null);
        putIfUseful(context, FlowExplorerCopilotHiddenToolContextKeys.ENDPOINT_ID,
                contextSnapshot != null ? contextSnapshot.endpointId() : request != null ? request.endpointId() : null);
        putIfUseful(context, FlowExplorerCopilotHiddenToolContextKeys.HTTP_METHOD,
                contextSnapshot != null ? contextSnapshot.httpMethod() : request != null ? request.httpMethod() : null);
        putIfUseful(context, FlowExplorerCopilotHiddenToolContextKeys.ENDPOINT_PATH,
                contextSnapshot != null ? contextSnapshot.endpointPath() : request != null ? request.endpointPath() : null);
        putIfUseful(context, FlowExplorerCopilotHiddenToolContextKeys.GITLAB_GROUP, gitLabGroup);
        putIfUseful(context, FlowExplorerCopilotHiddenToolContextKeys.GITLAB_REF, gitLabRef);
        putIfUseful(context, FlowExplorerCopilotHiddenToolContextKeys.REPOSITORY_ID,
                selectedRepository != null ? selectedRepository.repositoryId() : null);
        putIfUseful(context, FlowExplorerCopilotHiddenToolContextKeys.PROJECT_NAME,
                selectedRepository != null ? selectedRepository.projectName() : null);
        putIfUseful(context, FlowExplorerCopilotHiddenToolContextKeys.PROJECT_PATH,
                selectedRepository != null ? selectedRepository.projectPath() : null);
        putIfUseful(context, FlowExplorerCopilotHiddenToolContextKeys.DOCUMENTATION_PRESET,
                request != null && request.documentationPreset() != null ? request.documentationPreset().name() : null);
        putIfUseful(context, FlowExplorerCopilotHiddenToolContextKeys.FOCUS_AREAS, focusAreaNames(request));
        putIfUseful(context, FlowExplorerCopilotHiddenToolContextKeys.ARTIFACT_NAMES, artifactNames(preparation));

        return context;
    }

    private static FlowExplorerRepositoryContext selectedRepository(FlowExplorerContextSnapshot contextSnapshot) {
        if (contextSnapshot == null || contextSnapshot.repositories().isEmpty()) {
            return null;
        }

        return contextSnapshot.repositories().stream()
                .filter(FlowExplorerRepositoryContext::selected)
                .findFirst()
                .orElse(contextSnapshot.repositories().get(0));
    }

    private static List<String> focusAreaNames(FlowExplorerJobStartRequest request) {
        if (request == null || request.focusAreas().isEmpty()) {
            return List.of();
        }

        return request.focusAreas().stream()
                .map(FlowExplorerFocusArea::name)
                .toList();
    }

    private static List<String> artifactNames(FlowExplorerPromptPreparation preparation) {
        if (preparation == null || preparation.artifactContents().isEmpty()) {
            return List.of();
        }

        return preparation.artifactContents().keySet().stream()
                .sorted()
                .toList();
    }

    private static String normalizeRunReference(String runReference) {
        if (!StringUtils.hasText(runReference)) {
            return UUID.randomUUID().toString();
        }
        return runReference.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static void putIfUseful(Map<String, Object> context, String key, Object value) {
        if (!StringUtils.hasText(key) || value == null) {
            return;
        }
        if (value instanceof String stringValue) {
            if (!StringUtils.hasText(stringValue)) {
                return;
            }
            context.put(key, stringValue.trim());
            return;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return;
        }
        context.put(key, value);
    }
}
