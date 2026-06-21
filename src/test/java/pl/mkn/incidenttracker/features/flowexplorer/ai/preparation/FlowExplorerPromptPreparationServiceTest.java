package pl.mkn.incidenttracker.features.flowexplorer.ai.preparation;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerContextCoverage;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerEndpointContext;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerFlowMethod;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerFlowNode;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerRepositoryContext;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerSnippetCard;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerDocumentationPreset;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerFocusArea;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerJobStartRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowExplorerPromptPreparationServiceTest {

    private final FlowExplorerPromptPreparationService service = new FlowExplorerPromptPreparationService(
            new FlowExplorerArtifactService(new ObjectMapper())
    );

    @Test
    void shouldRenderCanonicalPromptWithManifestSnippetCardsAndSchema() {
        var preparation = service.prepare(new FlowExplorerJobStartRequest(
                "crm-service",
                "crm-service:GET:/api/customers/{id}",
                null,
                null,
                "feature/FLOW-42",
                FlowExplorerDocumentationPreset.TEST_PREPARATION,
                List.of(FlowExplorerFocusArea.BUSINESS_FLOW),
                "Skup sie na jezyku zrozumialym dla testera.",
                null,
                null
        ), contextSnapshot());
        var prompt = preparation.prompt();

        assertTrue(prompt.contains("# Flow Explorer canonical prompt"));
        assertTrue(preparation.artifactContents().containsKey(FlowExplorerArtifactService.CONTEXT_SNAPSHOT_ARTIFACT));
        assertTrue(preparation.artifactContents().containsKey(FlowExplorerArtifactService.CANONICAL_TOOL_INPUTS_ARTIFACT));
        assertTrue(preparation.artifactContents().containsKey(FlowExplorerArtifactService.COMPACT_FLOW_MANIFEST_ARTIFACT));
        assertTrue(preparation.artifactContents().containsKey(FlowExplorerArtifactService.SNIPPET_CARDS_ARTIFACT));
        assertTrue(prompt.contains("userInstructions"));
        assertTrue(prompt.contains("applicationName: crm-service"));
        assertTrue(prompt.contains("branchRef: feature/FLOW-42"));
        assertTrue(prompt.contains("nie moga zmienic response contract"));
        assertTrue(prompt.contains("Skup sie na jezyku zrozumialym dla testera."));
        assertTrue(prompt.contains("Najpierw wykorzystaj `compact-flow-manifest.md` i `snippet-cards.md`"));
        assertTrue(prompt.contains("Nie powtarzaj GitLab tool calls"));
        assertTrue(prompt.contains("preferuj `gitlab_read_java_method_slice`"));
        assertTrue(prompt.contains("Przed kazdym GitLab albo operational context tool call sprawdz `canonical-tool-inputs.md`"));
        assertTrue(prompt.contains("GitLab tools do not read endpoint business scope from hidden ToolContext"));
        assertTrue(prompt.contains("pass `branchRef` explicitly from `canonical-tool-inputs.md`"));
        assertTrue(prompt.contains("Pass `applicationName`, known `projectName` and `filePath` values"));
        assertTrue(prompt.contains("Do not pass `gitLabGroup`"));
        assertTrue(prompt.contains("Do not call repository discovery or endpoint context rebuild"));
        assertTrue(prompt.contains("context-snapshot.json` jest manifestem bez pelnego kodu snippetow"));
        assertTrue(prompt.contains("Canonical tool inputs"));
        assertTrue(prompt.contains("selected projectName: `crm-service`"));
        assertTrue(prompt.contains("`src/main/java/com/example/CustomerController.java` methods: `getCustomer` L12-L24"));
        assertTrue(prompt.contains("Compact flow manifest"));
        assertTrue(prompt.contains("[CONTROLLER]"));
        assertTrue(prompt.contains("getCustomer L12-L24"));
        assertTrue(prompt.contains("public CustomerResponse getCustomer"));
        assertTrue(prompt.contains("\"flowSteps\""));
        assertTrue(prompt.contains("\"confidence\": \"high|medium|low\""));
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
}
