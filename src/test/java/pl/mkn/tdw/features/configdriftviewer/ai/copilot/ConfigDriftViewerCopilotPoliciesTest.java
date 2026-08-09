package pl.mkn.tdw.features.configdriftviewer.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.agenttools.gitlab.GitLabToolNames;
import pl.mkn.tdw.agenttools.operationalcontext.OperationalContextToolNames;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationPolicyRequest;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.CopilotToolInvocationRejectedException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigDriftViewerCopilotPoliciesTest {

    private final ConfigDriftViewerCopilotScopePolicy scopePolicy =
            new ConfigDriftViewerCopilotScopePolicy(new ObjectMapper());

    @Test
    void shouldDenyToolsForAnyNonDeepSessionEvenIfAccidentallyRegistered() {
        assertThatThrownBy(() -> scopePolicy.beforeInvocation(request(
                basicContext(),
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
                """
                        {"projectName":"crm-api","branchRef":"release-1","filePath":"src/main/java/A.java"}
                        """
        ))).isInstanceOf(CopilotToolInvocationRejectedException.class)
                .hasMessageContaining("DEEP");
    }

    @Test
    void shouldEnforceRepositoryRefPathAndOperationalEntityScope() {
        var context = deepContext("scope-session");
        assertThatCode(() -> scopePolicy.beforeInvocation(request(
                context,
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
                """
                        {"projectName":"crm-api","branchRef":"release-1","filePath":"src/main/java/A.java"}
                        """
        ))).doesNotThrowAnyException();
        assertThatThrownBy(() -> scopePolicy.beforeInvocation(request(
                context,
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
                """
                        {"projectName":"crm-api","branchRef":"main","filePath":"src/main/java/A.java"}
                        """
        ))).isInstanceOf(CopilotToolInvocationRejectedException.class)
                .hasMessageContaining("outside");
        assertThatThrownBy(() -> scopePolicy.beforeInvocation(request(
                context,
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
                """
                        {"projectName":"crm-api","branchRef":"release-1","filePath":"infra/secrets.yml"}
                        """
        ))).isInstanceOf(CopilotToolInvocationRejectedException.class)
                .hasMessageContaining("filePath");
        assertThatCode(() -> scopePolicy.beforeInvocation(request(
                context,
                OperationalContextToolNames.GET_ENTITY,
                """
                        {"type":"system","id":"crm-api"}
                        """
        ))).doesNotThrowAnyException();
        assertThatThrownBy(() -> scopePolicy.beforeInvocation(request(
                context,
                OperationalContextToolNames.GET_ENTITY,
                """
                        {"type":"system","id":"other-system"}
                        """
        ))).isInstanceOf(CopilotToolInvocationRejectedException.class)
                .hasMessageContaining("outside");
    }

    @Test
    void shouldEnforceFeatureSpecificToolBudget() {
        var policy = new ConfigDriftViewerCopilotBudgetPolicy();
        var context = deepContext("budget-session");
        for (var index = 0; index < ConfigDriftViewerCopilotBudgetPolicy.MAX_GITLAB_CALLS; index++) {
            policy.beforeInvocation(request(context, GitLabToolNames.READ_REPOSITORY_FILE_CHUNK, "{}"));
        }

        assertThatThrownBy(() ->
                policy.beforeInvocation(request(context, GitLabToolNames.READ_REPOSITORY_FILE_CHUNK, "{}"))
        ).isInstanceOf(CopilotToolInvocationRejectedException.class)
                .hasMessageContaining("budget");
    }

    private CopilotToolInvocationPolicyRequest request(
            CopilotToolSessionContext context,
            String toolName,
            String arguments
    ) {
        return new CopilotToolInvocationPolicyRequest(
                context,
                context.copilotSessionId(),
                "call-1",
                toolName,
                arguments
        );
    }

    private CopilotToolSessionContext basicContext() {
        return new CopilotToolSessionContext(
                "run-basic",
                "basic-session",
                Map.of(
                        ConfigDriftViewerCopilotToolContextKeys.FEATURE,
                        ConfigDriftViewerCopilotToolContextKeys.FEATURE_VALUE,
                        ConfigDriftViewerCopilotToolContextKeys.MODE,
                        "BASIC"
                )
        );
    }

    private CopilotToolSessionContext deepContext(String sessionId) {
        return new CopilotToolSessionContext(
                "run-deep",
                sessionId,
                Map.of(
                        ConfigDriftViewerCopilotToolContextKeys.FEATURE,
                        ConfigDriftViewerCopilotToolContextKeys.FEATURE_VALUE,
                        ConfigDriftViewerCopilotToolContextKeys.MODE,
                        "DEEP",
                        ConfigDriftViewerCopilotToolContextKeys.ALLOWED_REPOSITORIES,
                        List.of(Map.of(
                                "projectName", "crm-api",
                                "branchRef", "release-1",
                                "pathPrefixes", List.of("src/main/java")
                        )),
                        ConfigDriftViewerCopilotToolContextKeys.ALLOWED_OPERATIONAL_ENTITY_IDS,
                        List.of("crm-api")
                )
        );
    }
}
