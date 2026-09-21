package pl.mkn.tdw.features.uxinspector.api;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorContextException;
import pl.mkn.tdw.frontendcatalog.*;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiscoveryException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static pl.mkn.tdw.features.uxinspector.UxInspectorTestFixtures.frontendCatalog;

class UxInspectorInputOptionsServiceTest {

    @Test
    void shouldExposeTrustedFrontendAndPinnedViewRevision() {
        var applications = mock(FrontendApplicationCatalogService.class);
        var views = mock(FrontendViewCatalogService.class);
        when(applications.loadCatalog()).thenReturn(frontendCatalog());
        when(views.loadCatalog("crm-agent-portal", "main", false)).thenReturn(viewCatalog());
        var service = new UxInspectorInputOptionsService(applications, views);

        assertThat(service.inputOptions().systems()).singleElement().satisfies(system -> {
            assertThat(system.systemId()).isEqualTo("crm-agent-portal");
            assertThat(system.defaultBranch()).isEqualTo("main");
        });
        var result = service.views("crm-agent-portal", "main", false);
        assertThat(result.sourceRevision().revision()).isEqualTo("abc123crm");
        assertThat(result.views()).singleElement().satisfies(view -> {
            assertThat(view.viewId()).isEqualTo("crm-contact-create");
            assertThat(view.componentSelectors()).containsExactly("crm-contact-create");
        });
        verify(views).loadCatalog("crm-agent-portal", "main", false);
    }

    @Test
    void shouldForwardAnExplicitCacheRefreshToTheSharedCatalog() {
        var applications = mock(FrontendApplicationCatalogService.class);
        var views = mock(FrontendViewCatalogService.class);
        when(applications.loadCatalog()).thenReturn(frontendCatalog());
        when(views.loadCatalog("crm-agent-portal", "main", true)).thenReturn(viewCatalog());
        var service = new UxInspectorInputOptionsService(applications, views);

        service.views("crm-agent-portal", "main", true);

        verify(views).loadCatalog("crm-agent-portal", "main", true);
    }

    @Test
    void shouldMapInvalidUnknownAndMissingRefWithoutLeakingIntegrationErrors() {
        var applications = mock(FrontendApplicationCatalogService.class);
        var views = mock(FrontendViewCatalogService.class);
        when(applications.loadCatalog()).thenReturn(frontendCatalog());
        when(views.loadCatalog("crm-agent-portal", "missing", false))
                .thenThrow(new GitLabFrontendDiscoveryException("FRONTEND_REF_NOT_FOUND", "Synthetic GitLab detail"));
        var service = new UxInspectorInputOptionsService(applications, views);

        assertThatThrownBy(() -> service.views(" ", "main", false))
                .isInstanceOf(UxInspectorContextException.class).hasMessageContaining("required");
        assertThatThrownBy(() -> service.views("unknown-crm", "main", false))
                .isInstanceOf(UxInspectorContextException.class).hasMessageContaining("not registered");
        assertThatThrownBy(() -> service.views("crm-agent-portal", "missing", false))
                .isInstanceOf(UxInspectorContextException.class).hasMessageContaining("does not exist")
                .hasMessageNotContaining("Synthetic GitLab detail");
    }

    private FrontendViewCatalog viewCatalog() {
        return new FrontendViewCatalog("crm-agent-portal", "CRM Agent Portal",
                new FrontendViewCatalog.SourceRevision("main", "abc123crm"), FrontendViewCatalog.Status.READY,
                List.of(new FrontendViewCatalog.View("crm-contact-create", "Nowy kontakt", "/contacts/new", "/contacts",
                        List.of("crm-contact-create"), "RESOLVED", false, List.of(), List.of(), List.of())),
                List.of(), List.of(),
                new FrontendViewCatalog.Boundary(1, 1, 4, 0, 0, false, 400, 80, 300, 500, 12));
    }
}
