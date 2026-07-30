package pl.mkn.tdw.features.runtimeconfigurationverification.ai.copilot;

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

class RuntimeConfigurationCopilotPoliciesTest {

    private final RuntimeConfigurationCopilotScopePolicy scopePolicy =
            new RuntimeConfigurationCopilotScopePolicy(new ObjectMapper());

    @Test
    void shouldDenyDeepCapabilitiesInBasicEvenIfToolWasAccidentallyRegistered() {
        assertThatThrownBy(() -> scopePolicy.beforeInvocation(request(
                basicContext(),
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
                """
                        {"projectName":"billing-api","branchRef":"release-1","filePath":"src/main/java/A.java"}
                        """
        ))).isInstanceOf(CopilotToolInvocationRejectedException.class)
                .hasMessageContaining("BASIC");
    }

    @Test
    void shouldEnforceRepositoryRefPathAndOperationalEntityScope() {
        var context = deepContext("scope-session");
        assertThatCode(() -> scopePolicy.beforeInvocation(request(
                context,
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
                """
                        {"projectName":"billing-api","branchRef":"release-1","filePath":"src/main/java/A.java"}
                        """
        ))).doesNotThrowAnyException();
        assertThatThrownBy(() -> scopePolicy.beforeInvocation(request(
                context,
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
                """
                        {"projectName":"billing-api","branchRef":"main","filePath":"src/main/java/A.java"}
                        """
        ))).isInstanceOf(CopilotToolInvocationRejectedException.class)
                .hasMessageContaining("outside");
        assertThatThrownBy(() -> scopePolicy.beforeInvocation(request(
                context,
                GitLabToolNames.READ_REPOSITORY_FILE_CHUNK,
                """
                        {"projectName":"billing-api","branchRef":"release-1","filePath":"infra/secrets.yml"}
                        """
        ))).isInstanceOf(CopilotToolInvocationRejectedException.class)
                .hasMessageContaining("filePath");
        assertThatCode(() -> scopePolicy.beforeInvocation(request(
                context,
                OperationalContextToolNames.GET_ENTITY,
                """
                        {"type":"system","id":"billing-api"}
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
        var policy = new RuntimeConfigurationCopilotBudgetPolicy();
        var context = deepContext("budget-session");
        for (var index = 0; index < RuntimeConfigurationCopilotBudgetPolicy.MAX_GITLAB_CALLS; index++) {
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
                        RuntimeConfigurationCopilotToolContextKeys.FEATURE,
                        RuntimeConfigurationCopilotToolContextKeys.FEATURE_VALUE,
                        RuntimeConfigurationCopilotToolContextKeys.MODE,
                        "BASIC"
                )
        );
    }

    private CopilotToolSessionContext deepContext(String sessionId) {
        return new CopilotToolSessionContext(
                "run-deep",
                sessionId,
                Map.of(
                        RuntimeConfigurationCopilotToolContextKeys.FEATURE,
                        RuntimeConfigurationCopilotToolContextKeys.FEATURE_VALUE,
                        RuntimeConfigurationCopilotToolContextKeys.MODE,
                        "DEEP",
                        RuntimeConfigurationCopilotToolContextKeys.ALLOWED_REPOSITORIES,
                        List.of(Map.of(
                                "projectName", "billing-api",
                                "branchRef", "release-1",
                                "pathPrefixes", List.of("src/main/java")
                        )),
                        RuntimeConfigurationCopilotToolContextKeys.ALLOWED_OPERATIONAL_ENTITY_IDS,
                        List.of("billing-api")
                )
        );
    }
}
