package pl.mkn.tdw.integrations.gitlab.frontend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitLabFrontendBootstrapDiscoveryServiceTest {

    @Mock
    private GitLabRepositoryPort repositoryPort;

    private GitLabFrontendBootstrapDiscoveryService service;

    @BeforeEach
    void setUp() {
        service = new GitLabFrontendBootstrapDiscoveryService(repositoryPort);
        when(repositoryPort.branchExists("crm-platform", "crm-agent-frontend", "main")).thenReturn(true);
    }

    @Test
    void shouldConfirmOneImportedCrmRouterRootWithAliasedAngularImports() {
        var files = new LinkedHashMap<String, String>();
        files.put("apps/crm-agent/src/main.ts", """
                import { bootstrapApplication as startCrmApplication } from '@angular/platform-browser';
                import { CRM_APP_CONFIG as crmConfig } from './app/app.config';
                import { CrmAgentComponent } from './app/crm-agent.component';

                // bootstrapApplication(FakeComponent, fakeConfig);
                startCrmApplication(CrmAgentComponent, crmConfig);
                """);
        files.put("apps/crm-agent/src/app/app.config.ts", """
                import { ApplicationConfig } from '@angular/core';
                import { provideRouter as configureCrmRouter } from '@angular/router';
                import { CRM_ROUTES } from './crm.routes';

                export const CRM_APP_CONFIG: ApplicationConfig = {
                  providers: [configureCrmRouter(CRM_ROUTES)]
                };
                """);
        files.put("apps/crm-agent/src/app/app.config.spec.ts", """
                import { provideRouter } from '@angular/router';
                provideRouter(CRM_TEST_ROUTES);
                """);
        stubRepository(files);

        var result = service.discover(scope(), GitLabFrontendGraphLimits.defaults());

        assertThat(result.status()).isEqualTo(GitLabFrontendCoverageStatus.READY);
        assertThat(result.root()).satisfies(root -> {
            assertThat(root.bootstrapSymbol()).isEqualTo("bootstrapApplication");
            assertThat(root.bootstrapSource().path()).isEqualTo("apps/crm-agent/src/main.ts");
            assertThat(root.applicationConfigSource().symbol()).isEqualTo("CRM_APP_CONFIG");
            assertThat(root.routerProviderSource().path())
                    .isEqualTo("apps/crm-agent/src/app/app.config.ts");
            assertThat(root.routeCollectionSymbol()).isEqualTo("CRM_ROUTES");
        });
        assertThat(result.candidateCount()).isEqualTo(2);
        assertThat(result.inspectedSourceCount()).isEqualTo(2);
        verify(repositoryPort, never()).readFile(
                "crm-platform",
                "crm-agent-frontend",
                "main",
                "apps/crm-agent/src/app/app.config.spec.ts",
                GitLabFrontendGraphLimits.defaults().maxFileCharacters()
        );
    }

    @Test
    void shouldConfirmAnInlineCrmRouterConfiguration() {
        var files = Map.of(
                "apps/crm-agent/src/main.ts", """
                        import { bootstrapApplication } from '@angular/platform-browser';
                        import { provideRouter } from '@angular/router';
                        import { CrmAgentComponent } from './app/crm-agent.component';

                        bootstrapApplication(CrmAgentComponent, {
                          providers: [provideRouter([{ path: 'contacts' }])]
                        });
                        """
        );
        stubRepository(files);

        var result = service.discover(scope(), GitLabFrontendGraphLimits.defaults());

        assertThat(result.status()).isEqualTo(GitLabFrontendCoverageStatus.READY);
        assertThat(result.root().applicationConfigSource().path()).isEqualTo("apps/crm-agent/src/main.ts");
        assertThat(result.root().routeCollectionSymbol()).isNull();
    }

    @Test
    void shouldBlockCommentAndStringFalsePositivesWithoutGuessingARoot() {
        var files = Map.of(
                "apps/crm-agent/src/main.ts", """
                        // import { bootstrapApplication } from '@angular/platform-browser';
                        // bootstrapApplication(CrmAgentComponent, CRM_CONFIG);
                        const note = "provideRouter(CRM_ROUTES)";
                        /* import { provideRouter } from '@angular/router'; */
                        """
        );
        stubRepository(files);

        var result = service.discover(scope(), GitLabFrontendGraphLimits.defaults());

        assertThat(result.status()).isEqualTo(GitLabFrontendCoverageStatus.BLOCKED);
        assertThat(result.root()).isNull();
        assertThat(result.diagnostics()).extracting(GitLabFrontendGraphDiagnostic::code)
                .contains(GitLabFrontendGraphDiagnosticCode.BOOTSTRAP_ROOT_NOT_FOUND);
    }

    @Test
    void shouldBlockMoreThanOneReachableCrmRouterRoot() {
        var files = new LinkedHashMap<String, String>();
        addImportedRoot(files, "crm-a", "CRM_A_CONFIG", "CRM_A_ROUTES");
        addImportedRoot(files, "crm-b", "CRM_B_CONFIG", "CRM_B_ROUTES");
        stubRepository(files);

        var result = service.discover(scope(), GitLabFrontendGraphLimits.defaults());

        assertThat(result.status()).isEqualTo(GitLabFrontendCoverageStatus.BLOCKED);
        assertThat(result.root()).isNull();
        assertThat(result.diagnostics()).extracting(GitLabFrontendGraphDiagnostic::code)
                .contains(GitLabFrontendGraphDiagnosticCode.BOOTSTRAP_ROOT_AMBIGUOUS);
    }

    @Test
    void shouldBlockWhenTheBoundedCrmCandidateSetCannotProveUniqueness() {
        var files = Map.of(
                "apps/crm-agent/src/main.ts", """
                        import { bootstrapApplication } from '@angular/platform-browser';
                        bootstrapApplication(CrmAgentComponent, CRM_APP_CONFIG);
                        """,
                "apps/crm-agent/src/app/app.config.ts", """
                        import { provideRouter } from '@angular/router';
                        export const CRM_APP_CONFIG = { providers: [provideRouter(CRM_ROUTES)] };
                        """
        );
        stubRepository(files, graphLimits(1));

        var result = service.discover(scope(), graphLimits(1));

        assertThat(result.status()).isEqualTo(GitLabFrontendCoverageStatus.BLOCKED);
        assertThat(result.candidateLimitReached()).isTrue();
        assertThat(result.inspectedSourceCount()).isZero();
        assertThat(result.diagnostics()).extracting(GitLabFrontendGraphDiagnostic::code)
                .contains(GitLabFrontendGraphDiagnosticCode.ROOT_CANDIDATE_LIMIT_REACHED);
    }

    @Test
    void shouldApplyCrmCodeSearchPrefixesBeforeReadingCandidates() {
        var files = Map.of(
                "apps/crm-agent/src/main.ts", """
                        import { bootstrapApplication } from '@angular/platform-browser';
                        import { provideRouter } from '@angular/router';
                        bootstrapApplication(CrmAgentComponent, {
                          providers: [provideRouter(CRM_ROUTES)]
                        });
                        """,
                "tools/crm-preview/src/main.ts", """
                        import { bootstrapApplication } from '@angular/platform-browser';
                        import { provideRouter } from '@angular/router';
                        bootstrapApplication(CrmPreviewComponent, {
                          providers: [provideRouter(CRM_PREVIEW_ROUTES)]
                        });
                        """
        );
        stubRepository(files);
        var scoped = new GitLabFrontendRepositoryScope(
                "crm-platform",
                "crm-agent-frontend",
                "main",
                List.of("apps/crm-agent")
        );

        var result = service.discover(scoped, GitLabFrontendGraphLimits.defaults());

        assertThat(result.status()).isEqualTo(GitLabFrontendCoverageStatus.READY);
        assertThat(result.root().bootstrapSource().path()).isEqualTo("apps/crm-agent/src/main.ts");
        verify(repositoryPort, never()).readFile(
                "crm-platform",
                "crm-agent-frontend",
                "main",
                "tools/crm-preview/src/main.ts",
                GitLabFrontendGraphLimits.defaults().maxFileCharacters()
        );
    }

    private void addImportedRoot(
            Map<String, String> files,
            String application,
            String configSymbol,
            String routesSymbol
    ) {
        files.put("apps/" + application + "/src/main.ts", """
                import { bootstrapApplication } from '@angular/platform-browser';
                import { %s } from './app/app.config';
                bootstrapApplication(CrmAgentComponent, %s);
                """.formatted(configSymbol, configSymbol));
        files.put("apps/" + application + "/src/app/app.config.ts", """
                import { provideRouter } from '@angular/router';
                export const %s = { providers: [provideRouter(%s)] };
                """.formatted(configSymbol, routesSymbol));
    }

    private void stubRepository(Map<String, String> files) {
        stubRepository(files, GitLabFrontendGraphLimits.defaults());
    }

    private void stubRepository(Map<String, String> files, GitLabFrontendGraphLimits limits) {
        var candidates = files.keySet().stream().map(this::candidate).toList();
        when(repositoryPort.searchRepositoryFilesByContent(
                "crm-platform",
                "crm-agent-frontend",
                "main",
                List.of("bootstrapApplication", "provideRouter"),
                limits.maxRootCandidates() + 1
        )).thenReturn(candidates);
        lenient().when(repositoryPort.readFile(
                eq("crm-platform"),
                eq("crm-agent-frontend"),
                eq("main"),
                anyString(),
                anyInt()
        )).thenAnswer(invocation -> {
            var path = invocation.getArgument(3, String.class);
            var content = files.get(path);
            return content != null ? new GitLabRepositoryFileContent(
                    "crm-platform",
                    "crm-agent-frontend",
                    "main",
                    path,
                    content,
                    false
            ) : null;
        });
    }

    private GitLabRepositoryFileCandidate candidate(String path) {
        return new GitLabRepositoryFileCandidate(
                "crm-platform",
                "crm-agent-frontend",
                "main",
                path,
                "synthetic CRM bootstrap candidate",
                1
        );
    }

    private GitLabFrontendRepositoryScope scope() {
        return new GitLabFrontendRepositoryScope(
                "crm-platform",
                "crm-agent-frontend",
                "main",
                List.of()
        );
    }

    private GitLabFrontendGraphLimits graphLimits(int maxRootCandidates) {
        var defaults = GitLabFrontendGraphLimits.defaults();
        return new GitLabFrontendGraphLimits(
                maxRootCandidates,
                defaults.maxRouteNodes(),
                defaults.maxRouteFiles(),
                defaults.maxSourceReads(),
                defaults.maxAliasResolutions(),
                defaults.maxImportDepth(),
                defaults.maxComponentDepth(),
                defaults.maxContextFiles(),
                defaults.maxFileCharacters(),
                defaults.maxTotalCharacters()
        );
    }
}
