package pl.mkn.tdw.features.uxinspector.ai.copilot;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.GitLabRepositoryToolScope;
import pl.mkn.tdw.agenttools.gitlab.frontend.GitLabFrontendToolContextKeys;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.REVISION;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.targetContext;

class UxInspectorCopilotToolSessionContextFactoryTest {

    @Test void historyUsesRealScopedCallbacksInInitialAndFollowUp() {
        var factory = new UxInspectorCopilotToolSessionContextFactory();
        for (int turn = 0; turn < 3; turn++) {
            var session = turn == 0 ? factory.create("crm-history", targetContext())
                    : factory.createFollowUp("crm-history", "crm-history-session", targetContext());
            var policy = new UxInspectorCopilotScopePolicy(new com.fasterxml.jackson.databind.ObjectMapper());
            var fixture = new pl.mkn.tdw.testsupport.agenttools.GitLabHistoryRuntimeFixture(session, "crm-ui", "src/Phone.ts", policy);
            fixture.exercise(UxInspectorCopilotToolAccessPolicy.from(fixture.definitions()).enabledTools());
            fixture.verifyRepositoryReadBoundaries(UxInspectorCopilotToolAccessPolicy.from(fixture.definitions()).enabledTools());
        }
    }

    @Test
    void shouldBindRepositoryBranchAndLimitReportToAnswerSection() {
        var session = new UxInspectorCopilotToolSessionContextFactory().create("crm-run", targetContext());
        var hidden = session.hiddenContext();

        assertThat(hidden.get(UxInspectorCopilotContextKeys.FEATURE)).isEqualTo("ux-inspector");
        assertThat(hidden.get(UxInspectorCopilotContextKeys.SYSTEM_ID)).isEqualTo("crm-agent-portal");
        assertThat(hidden.get(AgentToolContextKeys.GITLAB_GROUP)).isEqualTo("CRM");
        assertThat(hidden.get(AgentToolContextKeys.GITLAB_BRANCH)).isEqualTo("main");
        assertThat(hidden.get(AgentToolContextKeys.ALLOWED_REPORT_SECTION_IDS)).isEqualTo(List.of("answer"));
        var repositoryScope = (GitLabRepositoryToolScope) hidden.get(AgentToolContextKeys.GITLAB_REPOSITORY_SCOPE);
        assertThat(repositoryScope.group()).isEqualTo("CRM");
        assertThat(repositoryScope.selectedProject()).isEqualTo("crm-ui");
        assertThat(repositoryScope.selectedBranch()).isEqualTo("main");
        assertThat(repositoryScope.selectedCommit()).isNull();
        assertThat(hidden).doesNotContainKeys(
                AgentToolContextKeys.GITLAB_ALLOWED_APPLICATION_NAMES,
                AgentToolContextKeys.TOOL_HARD_BUDGET
        );
    }
}
