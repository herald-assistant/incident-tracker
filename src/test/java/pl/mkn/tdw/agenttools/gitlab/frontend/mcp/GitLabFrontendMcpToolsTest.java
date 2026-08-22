package pl.mkn.tdw.agenttools.gitlab.frontend.mcp;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.frontend.GitLabFrontendToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.frontend.GitLabFrontendTypeScriptSliceTarget;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabAngularRouteBranchSliceRequest;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabAngularRouteBranchSliceResponse;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabAngularRouteBranchSliceService;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRepositoryScope;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendSourceRevision;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolKind;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolSelector;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolSliceRequest;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolSliceResponse;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolSliceService;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitLabFrontendMcpToolsTest {

    private static final String SCREEN_REF = "crm-contact-preferences";
    private static final String COMPONENT_REF = "component-crm-contact-preferences";
    private static final String COMPONENT_PATH =
            "apps/crm-agent/src/app/contact-preferences/crm-contact-preferences.component.ts";

    private final GitLabAngularRouteBranchSliceService routeService =
            mock(GitLabAngularRouteBranchSliceService.class);
    private final GitLabTypeScriptSymbolSliceService symbolService =
            mock(GitLabTypeScriptSymbolSliceService.class);
    private final GitLabFrontendMcpTools tools =
            new GitLabFrontendMcpTools(routeService, symbolService);

    @Test
    void shouldResolveRouteSliceExclusivelyFromHiddenCrmScope() throws Exception {
        when(routeService.readBranchSlice(org.mockito.ArgumentMatchers.any()))
                .thenReturn(routeResponse());

        var result = tools.readRouteBranchSlice(
                SCREEN_REF,
                "Potwierdzenie routingu preferencji syntetycznego CRM.",
                toolContext()
        );

        var request = ArgumentCaptor.forClass(GitLabAngularRouteBranchSliceRequest.class);
        verify(routeService).readBranchSlice(request.capture());
        assertScope(request.getValue().scope());
        assertThat(request.getValue().screenId()).isEqualTo(SCREEN_REF);
        assertThat(request.getValue().expectedRevision()).isEqualTo("crm-commit-abc123");
        assertThat(request.getValue().includeDescendantRoutes()).isTrue();
        assertThat(result.sliceRef()).isEqualTo(SCREEN_REF);
        assertThat(result.sourceRevision()).isEqualTo("crm-commit-abc123");
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(result))
                .doesNotContain("synthetic-crm", "crm-agent-portal", "\"ref\":\"main\"");
    }

    @Test
    void shouldResolveTypeScriptTargetByOpaqueCrmSliceReference() {
        when(symbolService.readSymbolSlice(org.mockito.ArgumentMatchers.any()))
                .thenReturn(symbolResponse());

        var result = tools.readTypeScriptSymbolSlice(
                COMPONENT_REF,
                "Potwierdzenie zachowania formularza syntetycznego CRM.",
                toolContext()
        );

        var request = ArgumentCaptor.forClass(GitLabTypeScriptSymbolSliceRequest.class);
        verify(symbolService).readSymbolSlice(request.capture());
        assertScope(request.getValue().scope());
        assertThat(request.getValue().filePath()).isEqualTo(COMPONENT_PATH);
        assertThat(request.getValue().declaringTypeName()).isEqualTo("CrmContactPreferencesComponent");
        assertThat(request.getValue().symbolSelectors()).extracting(GitLabTypeScriptSymbolSelector::name)
                .containsExactly("savePreferences");
        assertThat(result.sliceRef()).isEqualTo(COMPONENT_REF);
    }

    @Test
    void shouldRejectInventedSliceReferencesBeforeCallingGitLab() {
        assertThatThrownBy(() -> tools.readTypeScriptSymbolSlice(
                "component-outside-crm-scope",
                "Proba wyjscia poza syntetyczny CRM.",
                toolContext()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowed TypeScript target");
        assertThatThrownBy(() -> tools.readRouteBranchSlice(
                "other-screen",
                "Proba wyjscia poza wybrany ekran CRM.",
                toolContext()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("selected screen reference");
    }

    private ToolContext toolContext() {
        var target = new GitLabFrontendTypeScriptSliceTarget(
                COMPONENT_REF,
                COMPONENT_PATH,
                "CrmContactPreferencesComponent",
                "apps/crm-agent/src/app/contact-preferences/crm-contact-preferences.component.html",
                List.of(new GitLabTypeScriptSymbolSelector(
                        "savePreferences", GitLabTypeScriptSymbolKind.METHOD, 42
                ))
        );
        return new ToolContext(Map.of(
                AgentToolContextKeys.GITLAB_GROUP, "synthetic-crm",
                AgentToolContextKeys.GITLAB_BRANCH, "main",
                GitLabFrontendToolContextKeys.PROJECT_NAME, "crm-agent-portal",
                GitLabFrontendToolContextKeys.PATH_PREFIXES, List.of("apps/crm-agent"),
                GitLabFrontendToolContextKeys.SOURCE_REVISION, "crm-commit-abc123",
                GitLabFrontendToolContextKeys.SCREEN_SLICE_REF, SCREEN_REF,
                GitLabFrontendToolContextKeys.TYPESCRIPT_SLICE_TARGETS, Map.of(COMPONENT_REF, target)
        ));
    }

    private GitLabAngularRouteBranchSliceResponse routeResponse() {
        return new GitLabAngularRouteBranchSliceResponse(
                scope(), revision(), "RESOLVED", null, null, List.of(), List.of(),
                800, 320, 480, 3, 4, false, List.of(), List.of()
        );
    }

    private GitLabTypeScriptSymbolSliceResponse symbolResponse() {
        return new GitLabTypeScriptSymbolSliceResponse(
                scope(), COMPONENT_PATH, "RESOLVED", "CrmContactPreferencesComponent",
                1, 70, 90, 1400, null, 0, List.of(),
                "export class CrmContactPreferencesComponent {}", 520, 880, false,
                List.of(), List.of(), List.of(), List.of(), 2, 1, 4, List.of(), List.of(), List.of()
        );
    }

    private GitLabFrontendRepositoryScope scope() {
        return new GitLabFrontendRepositoryScope(
                "synthetic-crm", "crm-agent-portal", "main", List.of("apps/crm-agent")
        );
    }

    private GitLabFrontendSourceRevision revision() {
        return new GitLabFrontendSourceRevision("main", "crm-commit-abc123");
    }

    private void assertScope(GitLabFrontendRepositoryScope scope) {
        assertThat(scope.group()).isEqualTo("synthetic-crm");
        assertThat(scope.projectName()).isEqualTo("crm-agent-portal");
        assertThat(scope.ref()).isEqualTo("main");
        assertThat(scope.pathPrefixes()).containsExactly("apps/crm-agent");
    }
}
