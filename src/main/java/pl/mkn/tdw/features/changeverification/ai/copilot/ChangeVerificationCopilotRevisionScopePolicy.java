package pl.mkn.tdw.features.changeverification.ai.copilot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.GitLabDiffToolScope;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicy;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicyRequest;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationRejectedException;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class ChangeVerificationCopilotRevisionScopePolicy implements CopilotToolInvocationPolicy {
    private final ObjectMapper objectMapper;

    @Override
    public void beforeInvocation(CopilotToolInvocationPolicyRequest request) {
        if (request.sessionContext() == null || request.toolName() == null
                || !request.toolName().startsWith(GitLabToolNames.PREFIX)
                || !ChangeVerificationCopilotToolContextKeys.FEATURE_VALUE.equals(request.sessionContext().hiddenContext()
                        .get(ChangeVerificationCopilotToolContextKeys.FEATURE))) {
            return;
        }
        try {
            var bound = request.sessionContext().hiddenContext().get(AgentToolContextKeys.GITLAB_DIFF_SCOPE);
            if (!(bound instanceof GitLabDiffToolScope scope)) {
                throw new IllegalArgumentException("Prepared revision scope is unavailable.");
            }
            var args = objectMapper.readTree(request.rawArguments());
            if (args == null || !args.isObject()) {
                throw new IllegalArgumentException("Tool arguments must be a JSON object.");
            }
            if (GitLabToolNames.READ_MERGE_REQUEST_DIFF.equals(request.toolName())) {
                scope.requireMergeRequest(text(args, "projectName"), args.path("mergeRequestIid").asLong(-1));
                return;
            }
            var sha = text(args, "branchRef");
            if (java.util.Set.of(GitLabToolNames.SEARCH_REPOSITORY_CANDIDATES,
                    GitLabToolNames.FIND_CLASS_REFERENCES, GitLabToolNames.FIND_FLOW_CONTEXT).contains(request.toolName())) {
                var projects = args.get("projectNames");
                if (!projects.isArray() || projects.isEmpty()) {
                    throw new IllegalArgumentException("Explicit projectNames are required.");
                }
                for (var project : projects) {
                    scope.requireRevision(project.asText(), sha);
                }
            } else if (GitLabToolNames.READ_REPOSITORY_FILE_CHUNKS.equals(request.toolName())) {
                var chunks = args.path("chunks");
                if (!chunks.isArray() || chunks.isEmpty()) {
                    throw new IllegalArgumentException("Explicit chunk projects are required.");
                }
                for (var chunk : chunks) {
                    scope.requireRevision(text(chunk, "projectName"), sha);
                }
            } else {
                scope.requireRevision(text(args, "projectName"), sha);
            }
        } catch (Exception exception) {
            var reason = exception.getMessage() == null ? "Invalid revision scope." : exception.getMessage();
            throw new CopilotToolInvocationRejectedException(reason,
                    Map.of("status", "denied_by_revision_scope", "reason", reason, "retryableWithChangedArguments", true));
        }
    }

    private String text(JsonNode node, String field) {
        return node.path(field).isTextual() ? node.path(field).asText() : null;
    }
}
