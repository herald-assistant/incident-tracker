package pl.mkn.tdw.features.uxinspector.ai.copilot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.GitLabRepositoryToolScope;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicy;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicyRequest;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationRejectedException;
import pl.mkn.tdw.integrations.gitlab.GitLabVerifiedRepositoryFileReader;

import java.util.LinkedHashMap;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 45)
@RequiredArgsConstructor
public class UxInspectorCopilotScopePolicy implements CopilotToolInvocationPolicy {

    private static final Set<String> REPOSITORY_TOOLS = Set.of(
            GitLabToolNames.LIST_REPOSITORY_TREE,
            GitLabToolNames.LIST_REPOSITORY_FILES,
            GitLabToolNames.SEARCH_REPOSITORY_FILES,
            GitLabToolNames.READ_REPOSITORY_FILE,
            GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
            GitLabToolNames.READ_OPENAPI_ENDPOINT_SLICE
    );

    private final ObjectMapper objectMapper;

    @Override
    public void beforeInvocation(CopilotToolInvocationPolicyRequest request) {
        if (!uxInspectorRun(request) || !REPOSITORY_TOOLS.contains(request.toolName())) {
            return;
        }

        var arguments = arguments(request.rawArguments());
        if (arguments == null) {
            reject(request, "Tool arguments are not a valid JSON object.", true);
        }

        var hidden = request.sessionContext().hiddenContext();
        var bound = hidden.get(AgentToolContextKeys.GITLAB_REPOSITORY_SCOPE);
        if (!(bound instanceof GitLabRepositoryToolScope repositoryScope)) {
            reject(request, "Resolved UX Inspector repository scope is unavailable.", false);
            return;
        }

        var projectName = text(arguments, "projectName");
        if (!repositoryScope.selectedProject().equals(projectName)
                && !repositoryScope.selectedProjectPath().equals(projectName)) {
            reject(request, "projectName is outside the selected UX Inspector frontend.", true);
        }
        if (!repositoryScope.selectedBranch().equals(text(arguments, "branchRef"))) {
            reject(request, "branchRef must be the selected UX Inspector branch pinned by the session.", true);
        }

        if (Set.of(
                GitLabToolNames.READ_REPOSITORY_FILE,
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
                GitLabToolNames.READ_OPENAPI_ENDPOINT_SLICE
        )
                .contains(request.toolName())) {
            requireSafePath(request, text(arguments, "filePath"), false, "filePath");
        } else if (GitLabToolNames.LIST_REPOSITORY_TREE.equals(request.toolName())) {
            requireSafePath(request, optionalText(arguments, "path"), true, "path");
        } else {
            requireSafePath(request, optionalText(arguments, "pathPrefix"), true, "pathPrefix");
        }

        var reason = text(arguments, "reason");
        if (!StringUtils.hasText(reason) || reason.length() > 500) {
            reject(request, "A short reason is required for every UX Inspector repository read.", true);
        }

        var applicationNames = arguments.get("applicationNames");
        if (applicationNames != null && (!applicationNames.isArray() || applicationNames.size() != 0)) {
            reject(request, "applicationNames must be omitted because the selected repository is session-bound.", true);
        }

        if (GitLabToolNames.READ_OPENAPI_ENDPOINT_SLICE.equals(request.toolName())) {
            validateOpenApiLocator(request, arguments);
        }
    }

    private void validateOpenApiLocator(CopilotToolInvocationPolicyRequest request, JsonNode arguments) {
        var filePath = text(arguments, "filePath");
        var normalized = filePath != null ? filePath.toLowerCase(java.util.Locale.ROOT) : "";
        if (!(normalized.endsWith(".json") || normalized.endsWith(".yaml") || normalized.endsWith(".yml"))) {
            reject(request, "OpenAPI filePath must end with .json, .yaml or .yml.", true);
        }
        var httpMethod = text(arguments, "httpMethod");
        var endpointPath = text(arguments, "endpointPath");
        var operationId = text(arguments, "operationId");
        if (!StringUtils.hasText(operationId)
                && (!StringUtils.hasText(httpMethod) || !StringUtils.hasText(endpointPath))) {
            reject(request, "OpenAPI reads require operationId or both httpMethod and endpointPath.", true);
        }
        var schemaDepth = integer(arguments, "schemaDepth");
        if (schemaDepth != null && (schemaDepth < 0 || schemaDepth > 4)) {
            reject(request, "schemaDepth must be between 0 and 4.", true);
        }
        var maxCharacters = integer(arguments, "maxCharacters");
        if (maxCharacters != null && (maxCharacters < 1_000 || maxCharacters > 50_000)) {
            reject(request, "maxCharacters must be between 1000 and 50000.", true);
        }
    }

    private boolean uxInspectorRun(CopilotToolInvocationPolicyRequest request) {
        return request != null && request.sessionContext() != null
                && UxInspectorCopilotContextKeys.FEATURE_VALUE.equals(
                request.sessionContext().hiddenContext().get(UxInspectorCopilotContextKeys.FEATURE));
    }

    private JsonNode arguments(String rawArguments) {
        try {
            var node = objectMapper.readTree(StringUtils.hasText(rawArguments) ? rawArguments : "{}");
            return node != null && node.isObject() ? node : null;
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private void requireSafePath(
            CopilotToolInvocationPolicyRequest request,
            String path,
            boolean allowRoot,
            String field
    ) {
        if (!GitLabVerifiedRepositoryFileReader.isSafePath(path, allowRoot)) {
            reject(request, field + " must be a safe path inside the selected UX Inspector repository.", true);
        }
    }

    private static String text(JsonNode node, String field) {
        var value = node != null ? node.get(field) : null;
        return value != null && value.isTextual() && StringUtils.hasText(value.asText())
                ? value.asText().trim() : null;
    }

    private static String optionalText(JsonNode node, String field) {
        var value = node != null ? node.get(field) : null;
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private static Integer integer(JsonNode node, String field) {
        var value = node != null ? node.get(field) : null;
        return value != null && value.canConvertToInt() ? value.asInt() : null;
    }

    private void reject(CopilotToolInvocationPolicyRequest request, String reason, boolean retryable) {
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "denied_by_ux_inspector_scope");
        result.put("toolName", request.toolName());
        result.put("reason", reason);
        result.put("retryableWithChangedArguments", retryable);
        throw new CopilotToolInvocationRejectedException(reason, result);
    }
}
