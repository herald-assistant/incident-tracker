package pl.mkn.tdw.integrations.gitlab.frontend;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitLabFrontendRouteGraphContractTest {

    @Test
    void shouldRepresentAnEffectiveCrmScreenChainWithoutRepositoryInventory() {
        var routeConfiguration = new ArrayList<>(List.of(new GitLabFrontendRouteConfiguration(
                GitLabFrontendRouteConfigurationKind.CAN_ACTIVATE,
                null,
                List.of("CrmContactAccessGuard"),
                null,
                GitLabFrontendDiscoveryStatus.RESOLVED,
                source("apps/crm-agent/src/app/crm.routes.ts", "CrmContactAccessGuard", 18),
                List.of()
        )));
        var screen = new GitLabFrontendScreenIdentity(
                "screen-crm-contact-preferences",
                "route-crm-contact-preferences",
                "/contacts/:contactId/preferences",
                null,
                new GitLabFrontendRouteTarget(
                        "CrmContactPreferencesComponent",
                        "libs/crm-agent/feature-contact/src/lib/preferences/crm-contact-preferences.component.ts"
                )
        );
        var rootNode = routeNode(
                "route-crm-contacts",
                null,
                null,
                "/contacts/:contactId",
                GitLabFrontendRouteNodeKind.ROUTE,
                routeConfiguration
        );
        var screenNode = routeNode(
                screen.routeNodeId(),
                rootNode.nodeId(),
                screen,
                screen.routePattern(),
                GitLabFrontendRouteNodeKind.SCREEN,
                List.of()
        );
        var nodes = new ArrayList<>(List.of(rootNode, screenNode));
        var chain = new GitLabFrontendEffectiveRouteChain(
                screen,
                List.of(
                        segment(rootNode, routeConfiguration),
                        segment(screenNode, List.of())
                ),
                List.of("contactId")
        );

        var graph = new GitLabFrontendRouteGraph(
                scope(),
                new GitLabFrontendSourceRevision("main", "crm-commit-001"),
                bootstrapRoot(),
                List.of(rootNode.nodeId()),
                nodes,
                List.of(new GitLabFrontendRouteGraphEdge(
                        "edge-crm-contact-preferences",
                        rootNode.nodeId(),
                        screenNode.nodeId(),
                        GitLabFrontendRouteGraphEdgeKind.CHILDREN,
                        GitLabFrontendRouteGraphEdgeStatus.RESOLVED,
                        null,
                        source("apps/crm-agent/src/app/crm.routes.ts", "CRM_ROUTES", 22),
                        List.of()
                )),
                List.of(chain),
                List.of(),
                new GitLabFrontendGraphCoverage(
                        GitLabFrontendCoverageStatus.READY,
                        2,
                        1,
                        4,
                        1,
                        0,
                        false,
                        List.of()
                ),
                List.of()
        );

        nodes.clear();
        routeConfiguration.clear();

        assertThat(graph.nodes()).hasSize(2);
        assertThat(graph.effectiveRouteChains()).singleElement()
                .satisfies(result -> {
                    assertThat(result.screen()).isEqualTo(screen);
                    assertThat(result.segments()).extracting(GitLabFrontendRouteChainSegment::nodeId)
                            .containsExactly(rootNode.nodeId(), screenNode.nodeId());
                    assertThat(result.routeParameters()).containsExactly("contactId");
                });
        assertThat(rootNode.configuration()).singleElement()
                .extracting(GitLabFrontendRouteConfiguration::kind)
                .isEqualTo(GitLabFrontendRouteConfigurationKind.CAN_ACTIVATE);
        assertThat(graph.coverage().visitedRouteFileCount()).isEqualTo(1);
    }

    @Test
    void shouldAllowABlockedCrmGraphWithoutGuessingBootstrapRoot() {
        var graph = new GitLabFrontendRouteGraph(
                scope(),
                new GitLabFrontendSourceRevision("main", null),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new GitLabFrontendGraphCoverage(
                        GitLabFrontendCoverageStatus.BLOCKED,
                        0,
                        0,
                        2,
                        0,
                        0,
                        false,
                        List.of("No production CRM router root was confirmed.")
                ),
                List.of(new GitLabFrontendGraphDiagnostic(
                        GitLabFrontendDiagnosticSeverity.ERROR,
                        GitLabFrontendGraphDiagnosticCode.BOOTSTRAP_ROOT_NOT_FOUND,
                        "No production CRM router root was confirmed.",
                        null,
                        null,
                        null
                ))
        );

        assertThat(graph.bootstrapRoot()).isNull();
        assertThat(graph.coverage().status()).isEqualTo(GitLabFrontendCoverageStatus.BLOCKED);
        assertThat(graph.diagnostics()).singleElement()
                .extracting(GitLabFrontendGraphDiagnostic::code)
                .isEqualTo(GitLabFrontendGraphDiagnosticCode.BOOTSTRAP_ROOT_NOT_FOUND);
    }

    @Test
    void shouldRejectAnInconsistentCrmScreenAndEffectiveChain() {
        var target = new GitLabFrontendRouteTarget("CrmContactComponent", null);
        var inconsistentScreen = new GitLabFrontendScreenIdentity(
                "screen-crm-contact",
                "route-crm-contact-other",
                "/contacts/:contactId",
                "primary",
                target
        );

        assertThatThrownBy(() -> routeNode(
                "route-crm-contact",
                null,
                inconsistentScreen,
                "/contacts/:contactId",
                GitLabFrontendRouteNodeKind.SCREEN,
                List.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("routeNodeId");

        var screen = new GitLabFrontendScreenIdentity(
                "screen-crm-contact",
                "route-crm-contact",
                "/contacts/:contactId",
                "primary",
                target
        );
        var differentSegment = new GitLabFrontendRouteChainSegment(
                "route-crm-dashboard",
                "dashboard",
                "/dashboard",
                "primary",
                List.of(),
                source("apps/crm-agent/src/app/crm.routes.ts", "CRM_ROUTES", 9)
        );

        assertThatThrownBy(() -> new GitLabFrontendEffectiveRouteChain(
                screen,
                List.of(differentSegment),
                List.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("screen route node");
    }

    @Test
    void shouldExposeOnlySemanticGraphLimits() {
        var limits = GitLabFrontendGraphLimits.defaults();
        var componentNames = Arrays.stream(GitLabFrontendGraphLimits.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName)
                .toList();

        assertThat(componentNames)
                .contains(
                        "maxRootCandidates",
                        "maxRouteNodes",
                        "maxSourceReads",
                        "maxAliasResolutions",
                        "maxImportDepth",
                        "maxComponentDepth"
                )
                .doesNotContain("maxInventoryFiles");
        assertThat(limits.maxRouteNodes()).isEqualTo(400);
        assertThat(limits.maxSourceReads()).isEqualTo(1_000);
        assertThat(limits.maxAliasResolutions()).isEqualTo(1_000);
        assertThat(limits.maxComponentDepth()).isEqualTo(8);
        assertThat(limits.maxContextFiles()).isEqualTo(120);
        assertThatThrownBy(() -> new GitLabFrontendGraphLimits(
                0, 400, 80, 300, 500, 12, 5, 40, 50_000, 500_000
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxRootCandidates");
    }

    private static GitLabFrontendRouteNode routeNode(
            String nodeId,
            String parentNodeId,
            GitLabFrontendScreenIdentity screen,
            String routePattern,
            GitLabFrontendRouteNodeKind kind,
            List<GitLabFrontendRouteConfiguration> configuration
    ) {
        return new GitLabFrontendRouteNode(
                nodeId,
                parentNodeId,
                screen,
                null,
                routePattern.substring(routePattern.lastIndexOf('/') + 1),
                routePattern,
                "primary",
                kind,
                GitLabFrontendDiscoveryStatus.RESOLVED,
                false,
                routePattern.contains(":contactId") ? List.of("contactId") : List.of(),
                screen != null ? screen.viewTarget() : null,
                null,
                null,
                configuration,
                source("apps/crm-agent/src/app/crm.routes.ts", "CRM_ROUTES", 12),
                List.of()
        );
    }

    private static GitLabFrontendRouteChainSegment segment(
            GitLabFrontendRouteNode node,
            List<GitLabFrontendRouteConfiguration> configuration
    ) {
        return new GitLabFrontendRouteChainSegment(
                node.nodeId(),
                node.pathSegment(),
                node.routePattern(),
                node.outlet(),
                configuration,
                node.routeSource()
        );
    }

    private static GitLabFrontendBootstrapRoot bootstrapRoot() {
        return new GitLabFrontendBootstrapRoot(
                "bootstrap-crm-agent",
                "bootstrapApplication",
                source("apps/crm-agent/src/main.ts", "bootstrapApplication", 7),
                source("apps/crm-agent/src/app/app.config.ts", "CRM_APP_CONFIG", 11),
                "provideRouter",
                source("apps/crm-agent/src/app/app.config.ts", "provideRouter", 15),
                "CRM_ROUTES"
        );
    }

    private static GitLabFrontendRepositoryScope scope() {
        return new GitLabFrontendRepositoryScope("crm-platform", "crm-agent-frontend", "main", List.of());
    }

    private static GitLabFrontendSourceReference source(String path, String symbol, int line) {
        return new GitLabFrontendSourceReference(path, symbol, line, line);
    }
}
