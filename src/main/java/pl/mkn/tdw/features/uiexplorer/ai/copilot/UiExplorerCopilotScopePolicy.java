package pl.mkn.tdw.features.uiexplorer.ai.copilot;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicy;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicyRequest;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationRejectedException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 45)
@RequiredArgsConstructor
public class UiExplorerCopilotScopePolicy implements CopilotToolInvocationPolicy {

    private static final Set<String> ALLOWED_GITLAB_TOOLS = Set.of(
            GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES,
            GitLabToolNames.READ_REPOSITORY_FILE,
            GitLabToolNames.READ_REPOSITORY_FILE_CHUNK
    );
    private static final int MAX_READ_CHARACTERS = 20_000;
    private static final int MAX_CHUNK_LINES = 600;

    private final ObjectMapper objectMapper;

    @Override
    public void beforeInvocation(CopilotToolInvocationPolicyRequest request) {
        if (!uiExplorerRun(request) || !gitLabTool(request.toolName())) {
            return;
        }
        if (!ALLOWED_GITLAB_TOOLS.contains(request.toolName())) {
            reject(request, "GitLab tool is not enabled for UI Explorer targeted fallback.", false);
        }
        var arguments = arguments(request.rawArguments());
        if (arguments == null) {
            reject(request, "Tool arguments are not a valid JSON object.", true);
        }
        var repository = repository(request);
        if (repository == null) {
            reject(request, "Resolved UI Explorer repository scope is unavailable.", false);
        }
        validateReason(request, arguments);
        validateBranchAndApplication(request, arguments, repository);
        if (GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES.equals(request.toolName())) {
            validateSearch(request, arguments, repository);
        } else {
            validateRead(request, arguments, repository);
        }
    }

    private void validateReason(CopilotToolInvocationPolicyRequest request, JsonNode arguments) {
        var reason = text(arguments, "reason");
        if (!StringUtils.hasText(reason) || reason.length() > 500) {
            reject(request, "A short reason is required for every UI Explorer fallback tool call.", true);
        }
    }

    private void validateBranchAndApplication(
            CopilotToolInvocationPolicyRequest request,
            JsonNode arguments,
            RepositoryScope repository
    ) {
        if (!repository.branchRef().equals(text(arguments, "branchRef"))) {
            reject(request, "branchRef is outside the validated UI Explorer source revision.", true);
        }
        var applicationNames = textList(arguments.get("applicationNames"));
        var expectedSystemId = stringValue(request.sessionContext().hiddenContext()
                .get(UiExplorerCopilotToolContextKeys.SYSTEM_ID));
        if (!applicationNames.isEmpty()
                && (applicationNames.size() != 1 || !applicationNames.contains(expectedSystemId))) {
            reject(request, "applicationNames is outside the selected UI Explorer system.", true);
        }
    }

    private void validateSearch(
            CopilotToolInvocationPolicyRequest request,
            JsonNode arguments,
            RepositoryScope repository
    ) {
        var projectNames = textList(arguments.get("projectNames"));
        if (!projectNames.isEmpty()
                && (projectNames.size() != 1 || !projectNames.contains(repository.projectName()))) {
            reject(request, "projectNames is outside the selected UI Explorer repository.", true);
        }
        var requestedPrefixes = normalizedPaths(textList(arguments.get("pathPrefixes")));
        if (!new java.util.LinkedHashSet<>(requestedPrefixes)
                .equals(new java.util.LinkedHashSet<>(repository.pathPrefixes()))) {
            reject(request, "pathPrefixes must exactly match the validated UI Explorer code-search boundary.", true);
        }
        var terms = new java.util.ArrayList<String>();
        terms.addAll(textList(arguments.get("keywords")));
        terms.addAll(textList(arguments.get("operationNames")));
        if (terms.isEmpty() || terms.size() > 10) {
            reject(request, "Targeted search requires between one and ten keywords or operationNames.", true);
        }
    }

    private void validateRead(
            CopilotToolInvocationPolicyRequest request,
            JsonNode arguments,
            RepositoryScope repository
    ) {
        if (!repository.projectName().equals(text(arguments, "projectName"))) {
            reject(request, "projectName is outside the selected UI Explorer repository.", true);
        }
        var filePath = normalizePath(text(arguments, "filePath"));
        if (!StringUtils.hasText(filePath) || !withinPrefixes(filePath, repository.pathPrefixes())) {
            reject(request, "filePath is outside the validated UI Explorer code-search boundary.", true);
        }
        var maxCharacters = integer(arguments, "maxCharacters");
        if (maxCharacters != null && (maxCharacters < 1 || maxCharacters > MAX_READ_CHARACTERS)) {
            reject(request, "maxCharacters exceeds the UI Explorer targeted-read limit.", true);
        }
        if (GitLabToolNames.READ_REPOSITORY_FILE_CHUNK.equals(request.toolName())) {
            var startLine = integer(arguments, "startLine");
            var endLine = integer(arguments, "endLine");
            if (startLine == null || endLine == null || startLine < 1
                    || endLine < startLine || endLine - startLine + 1 > MAX_CHUNK_LINES) {
                reject(request, "Chunk line range must contain at most 600 positive, ordered lines.", true);
            }
        }
    }

    private RepositoryScope repository(CopilotToolInvocationPolicyRequest request) {
        var value = request.sessionContext().hiddenContext().get(UiExplorerCopilotToolContextKeys.ALLOWED_REPOSITORY);
        if (!(value instanceof Map<?, ?> scope)) {
            return null;
        }
        var projectName = stringValue(scope.get("projectName"));
        var branchRef = stringValue(scope.get("branchRef"));
        if (!StringUtils.hasText(projectName) || !StringUtils.hasText(branchRef)) {
            return null;
        }
        return new RepositoryScope(projectName, branchRef, normalizedPaths(stringList(scope.get("pathPrefixes"))));
    }

    private boolean withinPrefixes(String path, List<String> prefixes) {
        return prefixes.isEmpty() || prefixes.stream()
                .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    private JsonNode arguments(String rawArguments) {
        try {
            var node = objectMapper.readTree(StringUtils.hasText(rawArguments) ? rawArguments : "{}");
            return node != null && node.isObject() ? node : null;
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private boolean uiExplorerRun(CopilotToolInvocationPolicyRequest request) {
        return request != null && request.sessionContext() != null
                && UiExplorerCopilotToolContextKeys.FEATURE_VALUE.equals(
                request.sessionContext().hiddenContext().get(UiExplorerCopilotToolContextKeys.FEATURE));
    }

    private boolean gitLabTool(String toolName) {
        return toolName != null && toolName.startsWith(GitLabToolNames.PREFIX);
    }

    private static String text(JsonNode node, String field) {
        var value = node != null ? node.get(field) : null;
        return value != null && value.isTextual() && StringUtils.hasText(value.asText())
                ? value.asText().trim()
                : null;
    }

    private static Integer integer(JsonNode node, String field) {
        var value = node != null ? node.get(field) : null;
        return value != null && value.canConvertToInt() ? value.asInt() : null;
    }

    private static List<String> textList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        var values = new java.util.ArrayList<String>();
        node.forEach(item -> {
            if (item.isTextual() && StringUtils.hasText(item.asText())) {
                values.add(item.asText().trim());
            }
        });
        return List.copyOf(values);
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

    private static List<String> normalizedPaths(List<String> values) {
        return values.stream().map(UiExplorerCopilotScopePolicy::normalizePath).distinct().toList();
    }

    private static String normalizePath(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim().replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "");
    }

    private void reject(CopilotToolInvocationPolicyRequest request, String reason, boolean retryable) {
        var result = new LinkedHashMap<String, Object>();
        result.put("status", "denied_by_ui_explorer_scope");
        result.put("toolName", request.toolName());
        result.put("reason", reason);
        result.put("retryableWithChangedArguments", retryable);
        throw new CopilotToolInvocationRejectedException(reason, result);
    }

    private record RepositoryScope(String projectName, String branchRef, List<String> pathPrefixes) {
    }
}
