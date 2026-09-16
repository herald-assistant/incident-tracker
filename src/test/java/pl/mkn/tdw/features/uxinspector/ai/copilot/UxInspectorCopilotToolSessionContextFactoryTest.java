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

    @Test
    void shouldPinRepositoryRevisionAndLimitReportToAnswerSection() {
        var session = new UxInspectorCopilotToolSessionContextFactory().create("crm-run", targetContext());
        var hidden = session.hiddenContext();

        assertThat(hidden.get(UxInspectorCopilotContextKeys.FEATURE)).isEqualTo("ux-inspector");
        assertThat(hidden.get(UxInspectorCopilotContextKeys.SYSTEM_ID)).isEqualTo("crm-agent-portal");
        assertThat(hidden.get(AgentToolContextKeys.GITLAB_GROUP)).isEqualTo("CRM");
        assertThat(hidden.get(AgentToolContextKeys.GITLAB_BRANCH)).isEqualTo("main");
        assertThat(hidden.get(GitLabFrontendToolContextKeys.SOURCE_REVISION)).isEqualTo(REVISION);
        assertThat(hidden.get(AgentToolContextKeys.ALLOWED_REPORT_SECTION_IDS)).isEqualTo(List.of("answer"));
        var repositoryScope = (GitLabRepositoryToolScope) hidden.get(AgentToolContextKeys.GITLAB_REPOSITORY_SCOPE);
        assertThat(repositoryScope.group()).isEqualTo("CRM");
        assertThat(repositoryScope.selectedProject()).isEqualTo("crm-ui");
        assertThat(repositoryScope.selectedBranch()).isEqualTo("main");
        assertThat(repositoryScope.selectedCommit()).isEqualTo(REVISION);
        assertThat(hidden).doesNotContainKeys(
                AgentToolContextKeys.GITLAB_ALLOWED_APPLICATION_NAMES,
                AgentToolContextKeys.TOOL_HARD_BUDGET
        );
    }
}
