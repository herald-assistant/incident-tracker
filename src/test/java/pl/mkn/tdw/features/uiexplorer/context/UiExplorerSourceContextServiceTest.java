package pl.mkn.tdw.features.uiexplorer.context;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerFrontendCatalogService;
import pl.mkn.tdw.features.uiexplorer.catalog.error.UiExplorerFrontendNotEligibleException;
import pl.mkn.tdw.features.uiexplorer.catalog.error.UiExplorerSourceRefNotFoundException;
import pl.mkn.tdw.features.uiexplorer.context.error.UiExplorerScreenSelectionStaleException;
import pl.mkn.tdw.features.uiexplorer.context.error.UiExplorerSourceRevisionChangedException;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerCoverageStatus;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionMode;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionModeAssignment;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendContextCoverage;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendCoverageStatus;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiagnostic;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiagnosticSeverity;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiscoveryException;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiscoveryStatus;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRepositoryScope;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteEntry;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteEntryKind;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenContextRequest;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenSourceContext;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendSignalConfidence;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendSourceDiscoveryService;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendSourceFile;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendSourceReference;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendSourceRevision;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendSourceRole;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendTechnicalSignal;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendTechnicalSignalKind;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendWorkspaceSignal;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerOperationalContextTestCatalog.crmCatalogWithoutPrimary;
import static pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerOperationalContextTestCatalog.eligibleCrmPathPrefixCatalog;
import static pl.mkn.tdw.features.uiexplorer.catalog.UiExplorerOperationalContextTestCatalog.port;

class UiExplorerSourceContextServiceTest {

    @Test
    void shouldBuildFeatureOwnedCrmSnapshotForActiveSectionsWithinHiddenScope() {
        var discovery = mock(GitLabFrontendSourceDiscoveryService.class);
        when(discovery.buildScreenContext(any())).thenReturn(sourceContext(false));
        var service = service(eligibleCrmPathPrefixCatalog(), discovery);

        var result = service.buildContext(
                "crm-agent-portal",
                "release/2026.08",
                "crm-contact-preferences",
                "crm-ui-revision-20260815",
                List.of(
                        new UiExplorerSectionModeAssignment(
                                UiExplorerSectionId.OVERVIEW,
                                UiExplorerSectionMode.DEEP
                        ),
                        new UiExplorerSectionModeAssignment(
                                UiExplorerSectionId.FORMS_AND_RULES,
                                UiExplorerSectionMode.COMPACT
                        ),
                        new UiExplorerSectionModeAssignment(
                                UiExplorerSectionId.DATA_AND_SERVICES,
                                UiExplorerSectionMode.OFF
                        )
                )
        );

        assertThat(result.systemLabel()).isEqualTo("CRM Agent Portal");
        assertThat(result.screen().routePattern()).isEqualTo("/contacts/:contactId/preferences");
        assertThat(result.sourceRevision().revision()).isEqualTo("crm-ui-revision-20260815");
        assertThat(result.status()).isEqualTo(UiExplorerCoverageStatus.READY);
        assertThat(result.sourceFiles()).singleElement().satisfies(file -> {
            assertThat(file.path()).endsWith("crm-contact-preferences.component.ts");
            assertThat(file.content()).contains("CrmContactPreferencesComponent");
            assertThat(file.roles()).contains("VIEW_COMPONENT", "FORM_LOGIC");
        });
        assertThat(result.sectionCoverage())
                .extracting(UiExplorerSectionContextCoverage::sectionId)
                .containsExactly(UiExplorerSectionId.OVERVIEW, UiExplorerSectionId.FORMS_AND_RULES);
        assertThat(result.boundary().maxContextFiles()).isEqualTo(40);
        assertThat(result.boundary().maxTotalCharacters()).isEqualTo(500_000);

        var request = ArgumentCaptor.forClass(GitLabFrontendScreenContextRequest.class);
        verify(discovery).buildScreenContext(request.capture());
        assertThat(request.getValue().screenId()).isEqualTo("crm-contact-preferences");
        assertThat(request.getValue().expectedCommitId()).isEqualTo("crm-ui-revision-20260815");
        assertThat(request.getValue().scope()).satisfies(scope -> {
            assertThat(scope.group()).isEqualTo("crm");
            assertThat(scope.projectName()).isEqualTo("agent-portal");
            assertThat(scope.ref()).isEqualTo("release/2026.08");
            assertThat(scope.pathPrefixes()).containsExactly("apps/crm-agent", "libs/crm-ui");
        });
    }

    @Test
    void shouldDowngradeActiveCoverageWhenCrmContextIsTruncatedAndRouteIsAmbiguous() {
        var discovery = mock(GitLabFrontendSourceDiscoveryService.class);
        when(discovery.buildScreenContext(any())).thenReturn(sourceContext(true));

        var result = service(eligibleCrmPathPrefixCatalog(), discovery).buildContext(
                "crm-agent-portal",
                "release/2026.08",
                "crm-contact-preferences",
                "crm-ui-revision-20260815",
                List.of(new UiExplorerSectionModeAssignment(
                        UiExplorerSectionId.OVERVIEW,
                        UiExplorerSectionMode.DEEP
                ))
        );

        assertThat(result.status()).isEqualTo(UiExplorerCoverageStatus.PARTIAL);
        assertThat(result.sectionCoverage()).singleElement()
                .extracting(UiExplorerSectionContextCoverage::status)
                .isEqualTo(UiExplorerCoverageStatus.PARTIAL);
        assertThat(result.visibilityLimits())
                .anyMatch(limit -> limit.contains("reached"))
                .anyMatch(limit -> limit.contains("unambiguous"));
        assertThat(result.boundary().contextTruncated()).isTrue();
    }

    @Test
    void shouldMapRevisionChangeBeforeStaleCrmScreenToFeatureConflict() {
        var discovery = mock(GitLabFrontendSourceDiscoveryService.class);
        when(discovery.buildScreenContext(any())).thenThrow(new GitLabFrontendDiscoveryException(
                "FRONTEND_SOURCE_REVISION_CHANGED",
                "Synthetic CRM revision changed"
        ));

        assertThatThrownBy(() -> service(eligibleCrmPathPrefixCatalog(), discovery).buildContext(
                "crm-agent-portal",
                "main",
                "crm-contact-preferences",
                "crm-old-revision",
                activeOverview()
        ))
                .isInstanceOf(UiExplorerSourceRevisionChangedException.class)
                .hasMessageContaining("Refresh the screen catalog");
    }

    @Test
    void shouldMapMissingCrmScreenAndRefToFeatureOwnedErrors() {
        var discovery = mock(GitLabFrontendSourceDiscoveryService.class);
        when(discovery.buildScreenContext(any()))
                .thenThrow(new GitLabFrontendDiscoveryException(
                        "FRONTEND_SCREEN_NOT_FOUND",
                        "Synthetic CRM screen is stale"
                ))
                .thenThrow(new GitLabFrontendDiscoveryException(
                        "FRONTEND_REF_NOT_FOUND",
                        "Synthetic CRM ref is missing"
                ));
        var service = service(eligibleCrmPathPrefixCatalog(), discovery);

        assertThatThrownBy(() -> service.buildContext(
                "crm-agent-portal", "main", "crm-stale-screen", "crm-ui-revision", activeOverview()
        )).isInstanceOf(UiExplorerScreenSelectionStaleException.class);
        assertThatThrownBy(() -> service.buildContext(
                "crm-agent-portal", "release/crm-missing", "crm-screen", "crm-ui-revision", activeOverview()
        )).isInstanceOf(UiExplorerSourceRefNotFoundException.class);
    }

    @Test
    void shouldRejectIncompleteCrmRegistrationBeforeCallingDiscovery() {
        var discovery = mock(GitLabFrontendSourceDiscoveryService.class);

        assertThatThrownBy(() -> service(crmCatalogWithoutPrimary(), discovery).buildContext(
                "crm-agent-portal", "main", "crm-screen", "crm-ui-revision", activeOverview()
        )).isInstanceOf(UiExplorerFrontendNotEligibleException.class);

        verifyNoInteractions(discovery);
    }

    private static UiExplorerSourceContextService service(
            pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog catalog,
            GitLabFrontendSourceDiscoveryService discovery
    ) {
        return new UiExplorerSourceContextService(
                new UiExplorerFrontendCatalogService(port(catalog)),
                discovery
        );
    }

    private static List<UiExplorerSectionModeAssignment> activeOverview() {
        return List.of(new UiExplorerSectionModeAssignment(
                UiExplorerSectionId.OVERVIEW,
                UiExplorerSectionMode.DEEP
        ));
    }

    private static GitLabFrontendScreenSourceContext sourceContext(boolean partial) {
        var scope = new GitLabFrontendRepositoryScope(
                "crm", "agent-portal", "release/2026.08", List.of("apps/crm-agent", "libs/crm-ui")
        );
        var routeSource = new GitLabFrontendSourceReference(
                "apps/crm-agent/src/app/app.routes.ts", "crmContactRoutes", 10, 18
        );
        var screen = new GitLabFrontendRouteEntry(
                "crm-contact-preferences",
                "Contact preferences",
                "/contacts/:contactId/preferences",
                "/contacts/:contactId",
                GitLabFrontendRouteEntryKind.SCREEN,
                partial ? GitLabFrontendDiscoveryStatus.AMBIGUOUS : GitLabFrontendDiscoveryStatus.RESOLVED,
                true,
                List.of("CrmAuthGuard"),
                List.of("contactId"),
                null,
                "CrmContactPreferencesComponent",
                "apps/crm-agent/src/app/contact-preferences/crm-contact-preferences.component.ts",
                routeSource,
                partial ? List.of("Synthetic CRM view source is ambiguous.") : List.of()
        );
        var sourcePath = "apps/crm-agent/src/app/contact-preferences/crm-contact-preferences.component.ts";
        return new GitLabFrontendScreenSourceContext(
                scope,
                new GitLabFrontendSourceRevision("release/2026.08", "crm-ui-revision-20260815"),
                screen,
                List.of(new GitLabFrontendWorkspaceSignal(
                        "FRAMEWORK", "ANGULAR", "apps/crm-agent/project.json"
                )),
                List.of(new GitLabFrontendSourceFile(
                        sourcePath,
                        List.of(GitLabFrontendSourceRole.VIEW_COMPONENT, GitLabFrontendSourceRole.FORM_LOGIC),
                        "export class CrmContactPreferencesComponent { readonly syntheticCrmForm = true; }",
                        81,
                        partial
                )),
                List.of(new GitLabFrontendTechnicalSignal(
                        GitLabFrontendTechnicalSignalKind.REACTIVE_FORM,
                        "A strongly anonymized CRM reactive form is present.",
                        GitLabFrontendSignalConfidence.HIGH,
                        new GitLabFrontendSourceReference(sourcePath, "FormBuilder", 7, 7)
                )),
                List.of(
                        new GitLabFrontendContextCoverage(
                                "ROUTING", GitLabFrontendCoverageStatus.READY, "Synthetic CRM route resolved."
                        ),
                        new GitLabFrontendContextCoverage(
                                "VIEW", GitLabFrontendCoverageStatus.READY, "Synthetic CRM view resolved."
                        ),
                        new GitLabFrontendContextCoverage(
                                "FORMS", GitLabFrontendCoverageStatus.READY, "Synthetic CRM form signal resolved."
                        )
                ),
                partial ? List.of(new GitLabFrontendDiagnostic(
                        GitLabFrontendDiagnosticSeverity.WARNING,
                        "CRM_CONTEXT_LIMIT_REACHED",
                        "Synthetic CRM context reached a hard limit.",
                        sourcePath
                )) : List.of(),
                42,
                1,
                partial,
                false,
                81,
                partial
        );
    }
}
