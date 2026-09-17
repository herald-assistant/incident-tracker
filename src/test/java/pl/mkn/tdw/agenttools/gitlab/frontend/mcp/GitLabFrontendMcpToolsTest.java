package pl.mkn.tdw.agenttools.gitlab.frontend.mcp;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.model.ToolContext;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.agenttools.gitlab.frontend.GitLabFrontendToolContextKeys;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabAngularRouteBranchSliceRequest;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabAngularRouteBranchSliceResponse;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabAngularRouteBranchSliceService;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRepositoryScope;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendSourceRevision;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendTypeScriptImportResolverService;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolKind;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolSelector;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolSliceRequest;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolSliceResponse;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptSymbolSliceService;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptDownstreamReference;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabTypeScriptDownstreamReferenceKind;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitLabFrontendMcpToolsTest {

    private static final String SCREEN_REF = "crm-contact-preferences";
    private static final String COMPONENT_PATH =
            "apps/crm-agent/src/app/contact-preferences/crm-contact-preferences.component.ts";
    private static final String FACADE_PATH =
            "apps/crm-agent/src/app/contact-preferences/crm-contact-preferences.facade.ts";

    private final GitLabAngularRouteBranchSliceService routeService =
            mock(GitLabAngularRouteBranchSliceService.class);
    private final GitLabTypeScriptSymbolSliceService symbolService =
            mock(GitLabTypeScriptSymbolSliceService.class);
    private final GitLabFrontendTypeScriptImportResolverService importResolverService =
            mock(GitLabFrontendTypeScriptImportResolverService.class);
    private final GitLabFrontendMcpTools tools =
            new GitLabFrontendMcpTools(routeService, symbolService, importResolverService);

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
    void shouldResolveDirectTypeScriptTargetByNaturalCodeCoordinates() {
        when(symbolService.readSymbolSlice(org.mockito.ArgumentMatchers.any()))
                .thenReturn(symbolResponse());

        var result = tools.readTypeScriptSymbolSlice(
                COMPONENT_PATH,
                "CrmContactPreferencesComponent",
                null,
                null,
                null,
                List.of("savePreferences"),
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
        assertThat(result.filePath()).isEqualTo(COMPONENT_PATH);
        assertThat(result.downstreamReferences()).singleElement().satisfies(reference ->
                assertThat(reference.targetSourcePath()).isNull());
    }

    @Test
    void shouldResolveAnOriginalImportOnDemandWithoutPreparedTargets() {
        when(symbolService.readSymbolSlice(org.mockito.ArgumentMatchers.any()))
                .thenReturn(facadeResponse());
        when(importResolverService.resolve(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(COMPONENT_PATH),
                org.mockito.ArgumentMatchers.eq("./crm-contact-preferences.facade"),
                org.mockito.ArgumentMatchers.eq("CrmContactPreferencesFacade")))
                .thenReturn(new GitLabFrontendTypeScriptImportResolverService.ResolvedImport(
                        FACADE_PATH, "CrmContactPreferencesFacade"));

        var imported = tools.readTypeScriptSymbolSlice(
                null,
                null,
                COMPONENT_PATH,
                "./crm-contact-preferences.facade",
                "CrmContactPreferencesFacade",
                List.of("savePreferences"),
                "Potwierdzenie przejscia po oryginalnym imporcie CRM.",
                toolContext()
        );

        assertThat(imported.filePath()).isEqualTo(FACADE_PATH);
        assertThat(imported.content()).contains(
                "import { CrmContactService } from './crm-contact.service';");
        assertThat(imported.includedImports()).containsExactly(
                "import { CrmContactService } from './crm-contact.service';");
        assertThatThrownBy(() -> tools.readRouteBranchSlice(
                "other-screen",
                "Proba wyjscia poza wybrany ekran CRM.",
                toolContext()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("selected screen reference");
    }

    private ToolContext toolContext() {
        return new ToolContext(Map.of(
                AgentToolContextKeys.GITLAB_GROUP, "synthetic-crm",
                AgentToolContextKeys.GITLAB_BRANCH, "main",
                GitLabFrontendToolContextKeys.PROJECT_NAME, "crm-agent-portal",
                GitLabFrontendToolContextKeys.PATH_PREFIXES, List.of("apps/crm-agent"),
                GitLabFrontendToolContextKeys.SOURCE_REVISION, "crm-commit-abc123",
                GitLabFrontendToolContextKeys.SCREEN_SLICE_REF, SCREEN_REF
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
                List.of(), List.of(), List.of(), List.of(), 2, 1, 4,
                List.of(new GitLabTypeScriptDownstreamReference(
                        GitLabTypeScriptDownstreamReferenceKind.METHOD_CALL,
                        "savePreferences", "facade", "savePreferences", "CrmContactPreferencesFacade",
                        "./crm-contact-preferences.facade", null)),
                List.of(), List.of()
        );
    }

    private GitLabTypeScriptSymbolSliceResponse facadeResponse() {
        return new GitLabTypeScriptSymbolSliceResponse(
                scope(), FACADE_PATH, "RESOLVED", "CrmContactPreferencesFacade",
                1, 55, 70, 1200, null, 0, List.of(),
                "import { CrmContactService } from './crm-contact.service';\n"
                        + "export class CrmContactPreferencesFacade { savePreferences() {} }",
                480, 720, false,
                List.of("import { CrmContactService } from './crm-contact.service';"),
                List.of(), List.of(), List.of(), 1, 0, 2, List.of(), List.of(), List.of()
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
        assertThat(scope.ref()).isEqualTo("crm-commit-abc123");
        assertThat(scope.pathPrefixes()).containsExactly("apps/crm-agent");
    }
}
