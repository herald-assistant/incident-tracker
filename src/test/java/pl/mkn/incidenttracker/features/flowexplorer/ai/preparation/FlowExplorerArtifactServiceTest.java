package pl.mkn.incidenttracker.features.flowexplorer.ai.preparation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerContextCoverage;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerEndpointContext;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerFlowMethod;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerFlowNode;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerRepositoryContext;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerSnippetCard;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerAnalysisGoal;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerFocusArea;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerJobStartRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowExplorerArtifactServiceTest {

    private final FlowExplorerArtifactService service = new FlowExplorerArtifactService(new ObjectMapper());

    @Test
    void shouldRenderStableFlowExplorerArtifacts() throws Exception {
        var artifacts = service.renderArtifacts(request(), contextSnapshot());
        var artifactContents = service.toArtifactContentMap(artifacts);

        assertEquals(List.of(
                FlowExplorerArtifactService.CONTEXT_SNAPSHOT_ARTIFACT,
                FlowExplorerArtifactService.CANONICAL_TOOL_INPUTS_ARTIFACT,
                FlowExplorerArtifactService.COMPACT_FLOW_MANIFEST_ARTIFACT,
                FlowExplorerArtifactService.SNIPPET_CARDS_ARTIFACT,
                FlowExplorerArtifactService.COVERAGE_ARTIFACT,
                FlowExplorerArtifactService.RESPONSE_CONTRACT_ARTIFACT
        ), artifacts.stream().map(artifact -> artifact.displayName()).toList());

        var contextJson = new ObjectMapper().readTree(artifactContents.get(
                FlowExplorerArtifactService.CONTEXT_SNAPSHOT_ARTIFACT
        ));
        assertEquals("flow-explorer-artifacts-v1", contextJson.get("artifactFormatVersion").asText());
        assertEquals("crm-service", contextJson.get("applicationName").asText());
        assertEquals("crm-service", contextJson.get("systemId").asText());
        assertEquals("TEST_SCENARIOS", contextJson.get("goal").asText());
        assertEquals("feature/FLOW-42", contextJson.get("branchRef").asText());
        assertEquals("feature/FLOW-42", contextJson.at("/contextSnapshot/branchRef").asText());
        assertEquals("DEEP", contextJson.at("/sectionModes/0/mode").asText());
        assertTrue(contextJson.at("/contextSnapshot/gitLabGroup").isMissingNode());
        assertEquals("crm-service:src/main/java/com/example/CustomerController.java:L9-L27",
                contextJson.at("/contextSnapshot/snippetCards/0/id").asText());
        assertTrue(contextJson.at("/contextSnapshot/snippetCards/0/characterCount").asInt() > 0);
        assertEquals(FlowExplorerArtifactService.SNIPPET_CARDS_ARTIFACT,
                contextJson.at("/contextSnapshot/snippetCards/0/contentArtifact").asText());
        assertFalse(contextJson.toString().contains("public CustomerResponse getCustomer"));

        var canonicalToolInputs = artifactContents.get(FlowExplorerArtifactService.CANONICAL_TOOL_INPUTS_ARTIFACT);
        assertTrue(canonicalToolInputs.contains("# Canonical Tool Inputs"));
        assertTrue(canonicalToolInputs.contains("applicationName: `crm-service`"));
        assertTrue(canonicalToolInputs.contains("branchRef: `feature/FLOW-42`"));
        assertTrue(canonicalToolInputs.contains("selected projectName: `crm-service`"));
        assertTrue(canonicalToolInputs.contains("projectPath: `platform/backend/crm-service`"));
        assertTrue(canonicalToolInputs.contains("## File And Method Scope"));
        assertTrue(canonicalToolInputs.contains("compact-flow-manifest.md` as the canonical filePath + methodSelector list"));
        assertTrue(canonicalToolInputs.contains("manifest `filePath` values"));
        assertFalse(canonicalToolInputs.contains("`src/main/java/com/example/CustomerController.java` methods"));
        assertFalse(canonicalToolInputs.contains("already embedded in flow-explorer/snippet-cards.md"));
        assertTrue(canonicalToolInputs.contains("Do not call `gitlab_list_available_repositories`"));
        assertFalse(canonicalToolInputs.contains("gitLabGroup:"));

        var manifest = artifactContents.get(FlowExplorerArtifactService.COMPACT_FLOW_MANIFEST_ARTIFACT);
        assertTrue(manifest.contains("[CONTROLLER]"));
        assertTrue(manifest.contains("getCustomer L12-L24"));
        assertTrue(manifest.contains("embedded: flow-explorer/snippet-cards.md"));
        assertTrue(!manifest.contains("public CustomerResponse getCustomer"));

        var snippets = artifactContents.get(FlowExplorerArtifactService.SNIPPET_CARDS_ARTIFACT);
        assertTrue(snippets.contains("public CustomerResponse getCustomer"));
        assertEquals("- none", service.renderContextClippingNotes(contextSnapshot()));

        var coverageJson = new ObjectMapper().readTree(artifactContents.get(
                FlowExplorerArtifactService.COVERAGE_ARTIFACT
        ));
        assertEquals(1, coverageJson.at("/coverage/snippetCardCount").asInt());
        assertEquals(0, coverageJson.at("/contextClippingNotes").size());
        assertTrue(artifactContents.get(FlowExplorerArtifactService.RESPONSE_CONTRACT_ARTIFACT)
                .contains("\"overview\""));
        assertTrue(artifactContents.get(FlowExplorerArtifactService.RESPONSE_CONTRACT_ARTIFACT)
                .contains("\"sections\""));
    }

    @Test
    void shouldLimitCompactFlowManifestAndRenderClippingNotes() throws Exception {
        var snapshot = contextSnapshotWithFlowNodeCount(101);

        var manifest = service.renderCompactFlowManifest(snapshot);
        assertEquals(100L, manifest.lines()
                .filter(line -> line.startsWith("- ["))
                .count());
        assertTrue(manifest.contains("compact flow manifest truncated from 101 to maxFlowNodes=100"));

        var clippingNotes = service.renderContextClippingNotes(snapshot);
        assertTrue(clippingNotes.contains("compact-flow-manifest.md was truncated from 101 to maxFlowNodes=100"));
        assertTrue(clippingNotes.contains("maxFiles=100"));
        assertTrue(clippingNotes.contains("snippet-cards.md was truncated to maxCards=20"));

        var artifacts = service.renderArtifacts(request(), snapshot);
        var artifactContents = service.toArtifactContentMap(artifacts);
        var coverageJson = new ObjectMapper().readTree(artifactContents.get(
                FlowExplorerArtifactService.COVERAGE_ARTIFACT
        ));
        assertEquals(3, coverageJson.at("/contextClippingNotes").size());
    }

    private static FlowExplorerJobStartRequest request() {
        return new FlowExplorerJobStartRequest(
                "crm-service",
                "crm-service:GET:/api/customers/{id}",
                null,
                null,
                "feature/FLOW-42",
                FlowExplorerAnalysisGoal.TEST_SCENARIOS,
                List.of(FlowExplorerFocusArea.BUSINESS_FLOW_RULES),
                "Skup sie na jezyku zrozumialym dla testera.",
                null,
                null
        );
    }

    private static FlowExplorerContextSnapshot contextSnapshot() {
        return new FlowExplorerContextSnapshot(
                "crm-service",
                "CRM Service",
                "feature/FLOW-42",
                "feature/FLOW-42",
                "platform/backend",
                "GET:/api/customers/{id}",
                "GET",
                "/api/customers/{id}",
                new FlowExplorerEndpointContext(
                        "GET:/api/customers/{id}",
                        List.of("GET"),
                        "/api/customers/{id}",
                        "/api/customers/{id}",
                        "CustomerController",
                        "getCustomer",
                        "src/main/java/com/example/CustomerController.java",
                        12,
                        24,
                        "HIGH"
                ),
                List.of(new FlowExplorerRepositoryContext(
                        "crm-service",
                        "crm-service",
                        "platform/backend/crm-service",
                        "feature/FLOW-42",
                        true,
                        true,
                        List.of()
                )),
                List.of(new FlowExplorerFlowNode(
                        "src/main/java/com/example/CustomerController.java",
                        "CONTROLLER",
                        "src/main/java/com/example/CustomerController.java",
                        List.of(new FlowExplorerFlowMethod("getCustomer", 12, 24)),
                        "Endpoint handler.",
                        "HIGH",
                        List.of()
                )),
                List.of(),
                List.of(new FlowExplorerSnippetCard(
                        "crm-service:src/main/java/com/example/CustomerController.java:L9-L27",
                        "crm-service",
                        "src/main/java/com/example/CustomerController.java",
                        "CONTROLLER",
                        List.of(new FlowExplorerFlowMethod("getCustomer", 12, 24)),
                        9,
                        27,
                        9,
                        27,
                        100,
                        false,
                        "Endpoint handler.",
                        "// file: src/main/java/com/example/CustomerController.java\npublic CustomerResponse getCustomer() {}",
                        0,
                        List.of()
                )),
                List.of(),
                List.of(),
                new FlowExplorerContextCoverage(
                        true,
                        1,
                        1,
                        1,
                        1,
                        0,
                        1,
                        103,
                        false,
                        0,
                        0,
                        false,
                        false,
                        false,
                        "HIGH"
                )
        );
    }

    private static FlowExplorerContextSnapshot contextSnapshotWithFlowNodeCount(int nodeCount) {
        var flowNodes = java.util.stream.IntStream.rangeClosed(1, nodeCount)
                .mapToObj(index -> new FlowExplorerFlowNode(
                        "src/main/java/com/example/CustomerFlowStep" + index + ".java",
                        "USE_CASE_SERVICE",
                        "src/main/java/com/example/CustomerFlowStep" + index + ".java",
                        List.of(new FlowExplorerFlowMethod("step" + index, index * 10, index * 10 + 5)),
                        "CRM flow step " + index + ".",
                        "MEDIUM",
                        List.of()
                ))
                .toList();

        return new FlowExplorerContextSnapshot(
                "crm-service",
                "CRM Service",
                "feature/FLOW-42",
                "feature/FLOW-42",
                "platform/backend",
                "GET:/api/customers/{id}",
                "GET",
                "/api/customers/{id}",
                new FlowExplorerEndpointContext(
                        "GET:/api/customers/{id}",
                        List.of("GET"),
                        "/api/customers/{id}",
                        "/api/customers/{id}",
                        "CustomerController",
                        "getCustomer",
                        "src/main/java/com/example/CustomerController.java",
                        12,
                        24,
                        "HIGH"
                ),
                List.of(new FlowExplorerRepositoryContext(
                        "crm-service",
                        "crm-service",
                        "platform/backend/crm-service",
                        "feature/FLOW-42",
                        true,
                        true,
                        List.of()
                )),
                flowNodes,
                List.of(),
                List.of(new FlowExplorerSnippetCard(
                        "crm-service:src/main/java/com/example/CustomerController.java:L9-L27",
                        "crm-service",
                        "src/main/java/com/example/CustomerController.java",
                        "CONTROLLER",
                        List.of(new FlowExplorerFlowMethod("getCustomer", 12, 24)),
                        9,
                        27,
                        9,
                        27,
                        100,
                        false,
                        "Endpoint handler.",
                        "// file: src/main/java/com/example/CustomerController.java\npublic CustomerResponse getCustomer() {}",
                        0,
                        List.of()
                )),
                List.of(),
                List.of(),
                new FlowExplorerContextCoverage(
                        true,
                        1,
                        1,
                        nodeCount,
                        nodeCount,
                        0,
                        1,
                        103,
                        true,
                        0,
                        0,
                        false,
                        true,
                        false,
                        "HIGH"
                )
        );
    }
}
