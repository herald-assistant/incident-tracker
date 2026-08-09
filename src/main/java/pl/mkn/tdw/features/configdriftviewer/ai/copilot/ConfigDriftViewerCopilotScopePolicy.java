package pl.mkn.tdw.features.configdriftviewer.ai.copilot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.agenttools.operationalcontext.OperationalContextToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicy;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicyRequest;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationRejectedException;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerMode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
@RequiredArgsConstructor
public class ConfigDriftViewerCopilotScopePolicy implements CopilotToolInvocationPolicy {

    private final ObjectMapper objectMapper;

    @Override
    public void beforeInvocation(CopilotToolInvocationPolicyRequest request) {
        if (!configDriftViewerRun(request)) {
            return;
        }
        var toolName = request.toolName();
        if (!isGitLab(toolName) && !isOperationalContext(toolName)) {
            return;
        }
        if (mode(request) != ConfigDriftViewerMode.DEEP) {
            reject(request, "Only DEEP mode allows Config Drift Viewer AI tools.");
        }
        var arguments = arguments(request.rawArguments());
        if (arguments == null) {
            reject(request, "Tool arguments are not valid JSON.");
        }
        if (isGitLab(toolName)) {
            validateGitLab(request, arguments);
        } else {
            validateOperationalContext(request, arguments);
        }
    }

    private void validateGitLab(CopilotToolInvocationPolicyRequest request, JsonNode arguments) {
        var scopes = repositoryScopes(request);
        if (scopes.isEmpty()) {
            reject(request, "No source-code repository scope is available.");
        }
        var projectNames = textList(arguments.get("projectNames"));
        var projectName = text(arguments, "projectName");
        if (StringUtils.hasText(projectName)) {
            projectNames = List.of(projectName);
        }
        if (projectNames.isEmpty()) {
            reject(request, "A scoped projectName is required.");
        }
        var branchRef = text(arguments, "branchRef");
        if (!StringUtils.hasText(branchRef)) {
            reject(request, "The resolved branchRef is required.");
        }
        for (var project : projectNames) {
            var scope = scopes.stream()
                    .filter(candidate -> project.equals(candidate.projectName()))
                    .filter(candidate -> branchRef.equals(candidate.branchRef()))
                    .findFirst()
                    .orElse(null);
            if (scope == null) {
                reject(request, "Repository or ref is outside the selected internal-service code-search scope.");
            }
            var requestedPrefixes = textList(arguments.get("pathPrefixes"));
            if (!requestedPrefixes.isEmpty()
                    && !scope.pathPrefixes().containsAll(requestedPrefixes)) {
                reject(request, "Requested pathPrefixes are outside the selected code-search scope.");
            }
            var filePath = normalizePath(text(arguments, "filePath"));
            if (StringUtils.hasText(filePath)
                    && !scope.pathPrefixes().isEmpty()
                    && scope.pathPrefixes().stream()
                    .map(ConfigDriftViewerCopilotScopePolicy::normalizePath)
                    .noneMatch(prefix -> filePath.equals(prefix) || filePath.startsWith(prefix + "/"))) {
                reject(request, "Requested filePath is outside the selected code-search pathPrefixes.");
            }
        }
    }

    private void validateOperationalContext(CopilotToolInvocationPolicyRequest request, JsonNode arguments) {
        if (!OperationalContextToolNames.GET_ENTITY.equals(request.toolName())) {
            reject(request, "Broad Operational Context discovery is not enabled for this run.");
        }
        var id = text(arguments, "id");
        if (!allowedOperationalIds(request).contains(id)) {
            reject(request, "Operational Context entity is outside the prepared DEEP context.");
        }
    }

    private boolean configDriftViewerRun(CopilotToolInvocationPolicyRequest request) {
        return request != null
                && request.sessionContext() != null
                && ConfigDriftViewerCopilotToolContextKeys.FEATURE_VALUE.equals(
                request.sessionContext().hiddenContext().get(ConfigDriftViewerCopilotToolContextKeys.FEATURE)
        );
    }

    private ConfigDriftViewerMode mode(CopilotToolInvocationPolicyRequest request) {
        var value = request.sessionContext().hiddenContext().get(ConfigDriftViewerCopilotToolContextKeys.MODE);
        try {
            return ConfigDriftViewerMode.valueOf(String.valueOf(value));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private JsonNode arguments(String rawArguments) {
        try {
            var node = objectMapper.readTree(StringUtils.hasText(rawArguments) ? rawArguments : "{}");
            return node != null && node.isObject() ? node : null;
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private List<RepositoryScope> repositoryScopes(CopilotToolInvocationPolicyRequest request) {
        var value = request.sessionContext().hiddenContext()
                .get(ConfigDriftViewerCopilotToolContextKeys.ALLOWED_REPOSITORIES);
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(scope -> new RepositoryScope(
                        stringValue(scope.get("projectName")),
                        stringValue(scope.get("branchRef")),
                        stringList(scope.get("pathPrefixes"))
                ))
                .filter(scope -> StringUtils.hasText(scope.projectName())
                        && StringUtils.hasText(scope.branchRef()))
                .toList();
    }

    private List<String> allowedOperationalIds(CopilotToolInvocationPolicyRequest request) {
        return stringList(request.sessionContext().hiddenContext()
                .get(ConfigDriftViewerCopilotToolContextKeys.ALLOWED_OPERATIONAL_ENTITY_IDS));
    }

    private static List<String> textList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        var result = new java.util.ArrayList<String>();
        node.forEach(item -> {
            if (item.isTextual() && StringUtils.hasText(item.asText())) {
                result.add(item.asText().trim());
            }
        });
        return List.copyOf(result);
    }

    private static String text(JsonNode node, String field) {
        var value = node != null ? node.get(field) : null;
        return value != null && value.isTextual() && StringUtils.hasText(value.asText())
                ? value.asText().trim()
                : null;
    }

    private static String stringValue(Object value) {
        return value instanceof String string && StringUtils.hasText(string) ? string.trim() : null;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .toList();
    }

    private static boolean isGitLab(String toolName) {
        return toolName != null && toolName.startsWith(GitLabToolNames.PREFIX);
    }

    private static boolean isOperationalContext(String toolName) {
        return toolName != null && toolName.startsWith(OperationalContextToolNames.PREFIX);
    }

    private static String normalizePath(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private void reject(CopilotToolInvocationPolicyRequest request, String reason) {
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "denied_by_runtime_configuration_scope");
        result.put("toolName", request.toolName());
        result.put("reason", reason);
        result.put("retryableWithChangedArguments", true);
        throw new CopilotToolInvocationRejectedException(reason, result);
    }

    private record RepositoryScope(String projectName, String branchRef, List<String> pathPrefixes) {
    }
}
