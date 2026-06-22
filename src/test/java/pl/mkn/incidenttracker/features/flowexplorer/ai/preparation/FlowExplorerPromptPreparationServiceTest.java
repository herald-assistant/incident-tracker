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
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerAnalysisGoal;
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
        var preparation = service.prepare(request(), contextSnapshot());
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
        assertTrue(prompt.contains("Runtime skills usage contract"));
        assertTrue(prompt.contains("uzyj built-in tool `skill`"));
        assertTrue(prompt.contains("nie czekaj, az uzytkownik poprosi o skille"));
        assertTrue(prompt.contains("MUST = pobierz przez `skill` i zastosuj"));
        assertTrue(prompt.contains("MUST: flow-explorer-orchestrator"));
        assertTrue(prompt.contains("MUST: flow-explorer-result-contract"));
        assertTrue(prompt.contains("MUST: flow-explorer-goal-test-scenarios"));
        assertTrue(prompt.contains("SHOULD: flow-explorer-operational-context-tools"));
        assertTrue(prompt.contains("SHOULD: flow-explorer-gitlab-tools"));
        assertTrue(prompt.contains("COULD: record_tool_feedback"));
        assertTrue(prompt.contains("Najpierw wykorzystaj `compact-flow-manifest.md`, `snippet-cards.md` i, jezeli jest dostepny, `openapi-endpoint-contract.md`"));
        assertTrue(prompt.contains("Nie powtarzaj GitLab tool calls"));
        assertTrue(prompt.contains("`focusAreas` traktuja tylko o tym, ktore sekcje maja tryb `deep`"));
        assertTrue(prompt.contains("Glebokosc eksploracji wynika z `reasoningEffort`"));
        assertTrue(prompt.contains("sectionModes"));
        assertTrue(prompt.contains("reasoningEffort: high"));
        assertTrue(prompt.contains("Context clipping notes"));
        assertTrue(prompt.contains("preferuj `gitlab_read_java_method_slice`"));
        assertTrue(prompt.contains("Przed kazdym GitLab albo operational context tool call sprawdz `canonical-tool-inputs.md`"));
        assertTrue(prompt.contains("GitLab tools do not read endpoint business scope from hidden ToolContext"));
        assertTrue(prompt.contains("pass `branchRef` explicitly from `canonical-tool-inputs.md`"));
        assertTrue(prompt.contains("Pass `applicationName`, known `projectName` and `branchRef` values"));
        assertTrue(prompt.contains("Pass `filePath` and method selectors from `compact-flow-manifest.md`"));
        assertTrue(prompt.contains("Do not pass `gitLabGroup`"));
        assertTrue(prompt.contains("Do not call repository discovery or endpoint context rebuild"));
        assertTrue(prompt.contains("context-snapshot.json` jest manifestem bez pelnego kodu snippetow"));
        assertTrue(prompt.contains("Canonical tool inputs"));
        assertTrue(prompt.contains("selected projectName: `crm-service`"));
        assertTrue(prompt.contains("File And Method Scope"));
        assertTrue(prompt.contains("Use `flow-explorer/compact-flow-manifest.md` as the canonical filePath + methodSelector list"));
        assertTrue(prompt.contains("Compact flow manifest"));
        assertTrue(prompt.contains("[CONTROLLER]"));
        assertTrue(prompt.contains("getCustomer L12-L24"));
        assertTrue(prompt.contains("embedded: flow-explorer/snippet-cards.md"));
        assertTrue(prompt.contains("public CustomerResponse getCustomer"));
        assertTrue(prompt.contains("\"overview\""));
        assertTrue(prompt.contains("\"sections\""));
        assertTrue(prompt.contains("BUSINESS_FLOW_RULES"));
        assertTrue(prompt.contains("\"confidence\": \"high|medium|low\""));
    }

    @Test
    void shouldWarnModelWhenInitialContextWasClipped() {
        var preparation = service.prepare(request(), contextSnapshot(true, true, true));
        var prompt = preparation.prompt();

        assertTrue(prompt.contains("Context clipping notes"));
        assertTrue(prompt.contains("GitLab endpoint use-case context reached maxFiles=100"));
        assertTrue(prompt.contains("snippet-cards.md was truncated to maxCards=20"));
        assertTrue(prompt.contains("1 snippet card(s) were truncated by GitLab read output limits"));
    }

    @Test
    void shouldRenderRiskDetectionGoalInCanonicalPrompt() {
        var preparation = service.prepare(riskDetectionRequest(), contextSnapshot());
        var prompt = preparation.prompt();

        assertTrue(prompt.contains("goal: RISK_DETECTION"));
        assertTrue(prompt.contains("MUST: flow-explorer-goal-risk-detection"));
        assertTrue(prompt.contains("focusAreas: [VALIDATIONS, INTEGRATIONS]"));
        assertTrue(prompt.contains("reasoningEffort: high"));
        assertTrue(prompt.contains("Skup sie na ryzykach regresji CRM."));
    }

    @Test
    void shouldRenderDeepDiscoveryGoalSkillInCanonicalPrompt() {
        var preparation = service.prepare(deepDiscoveryRequest(), contextSnapshot());
        var prompt = preparation.prompt();

        assertTrue(prompt.contains("goal: DEEP_DISCOVERY"));
        assertTrue(prompt.contains("MUST: flow-explorer-goal-deep-discovery"));
        assertTrue(prompt.contains("Szukaj pelnego przeplywu biznesowo-systemowego"));
    }

    private static FlowExplorerContextSnapshot contextSnapshot() {
        return contextSnapshot(false, false, false);
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
                "gpt-5.4-mini",
                "high"
        );
    }

    private static FlowExplorerJobStartRequest riskDetectionRequest() {
        return new FlowExplorerJobStartRequest(
                "crm-service",
                "crm-service:GET:/api/customers/{id}",
                null,
                null,
                "feature/FLOW-42",
                FlowExplorerAnalysisGoal.RISK_DETECTION,
                List.of(FlowExplorerFocusArea.VALIDATIONS, FlowExplorerFocusArea.INTEGRATIONS),
                "Skup sie na ryzykach regresji CRM.",
                "gpt-5.4-mini",
                "high"
        );
    }

    private static FlowExplorerJobStartRequest deepDiscoveryRequest() {
        return new FlowExplorerJobStartRequest(
                "crm-service",
                "crm-service:GET:/api/customers/{id}",
                null,
                null,
                "feature/FLOW-42",
                FlowExplorerAnalysisGoal.DEEP_DISCOVERY,
                List.of(FlowExplorerFocusArea.PERSISTENCE),
                "Pokaz flow biznesowo.",
                "gpt-5.4-mini",
                "high"
        );
    }

    private static FlowExplorerContextSnapshot contextSnapshot(
            boolean snippetBudgetReached,
            boolean maxFilesReached,
            boolean snippetCardTruncated
    ) {
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
                        snippetCardTruncated,
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
                        snippetBudgetReached,
                        0,
                        0,
                        false,
                        maxFilesReached,
                        false,
                        "HIGH"
                )
        );
    }
}
