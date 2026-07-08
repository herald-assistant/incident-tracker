package pl.mkn.tdw.features.flowexplorer.ai.preparation;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextCoverage;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerEndpointContext;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerFlowMethod;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerFlowNode;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerRepositoryContext;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerSnippetCard;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerAnalysisGoal;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerFocusArea;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStartRequest;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionId;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionMode;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerSectionModeRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowExplorerPromptPreparationServiceTest {

    private final FlowExplorerPromptPreparationService service = new FlowExplorerPromptPreparationService(
            new FlowExplorerArtifactService(new ObjectMapper())
    );
    private final FlowExplorerFollowUpPromptPreparationService followUpService =
            new FlowExplorerFollowUpPromptPreparationService();

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
        assertTrue(prompt.contains("Runtime envelope"));
        assertTrue(prompt.contains("Ten prompt przekazuje dane biezacego runu"));
        assertTrue(prompt.contains("Zasady pracy, wybor tools, report tools, fallback JSON i format wyniku pochodza z runtime skilli"));
        assertTrue(prompt.contains("`flow-explorer-write-report` jest jedynym wlascicielem finalnego `AnalysisReport`"));
        assertFalse(prompt.contains("report_upsert_section"));
        assertFalse(prompt.contains("report_update_meta"));
        assertFalse(prompt.contains("awaryjny poprawny JSON"));
        assertTrue(prompt.contains("`sectionModes` jest zrodlem prawdy dla sekcji wyniku"));
        assertTrue(prompt.contains("`userInstructions` doprecyzowuja intencje"));
        assertTrue(prompt.contains("Najpierw wykorzystaj artefakty osadzone w tym promptcie"));
        assertTrue(prompt.contains("`searchMode/pathPrefixes` z `flow-explorer/canonical-tool-inputs.md` sa domyslnym discovery scope"));
        assertTrue(prompt.contains("Skup sie na jezyku zrozumialym dla testera."));
        assertTrue(prompt.contains("Runtime skills usage contract"));
        assertTrue(prompt.contains("Pobierz i zastosuj wymagane skille przez built-in tool `skill`"));
        assertTrue(prompt.contains("Ten prompt nie powiela playbookow"));
        assertTrue(prompt.contains("MUST: flow-explorer-orchestrator"));
        assertTrue(prompt.contains("MUST: flow-explorer-write-report"));
        assertTrue(prompt.contains("Jedyny wlasciciel finalnego `AnalysisReport`, report tools i fallback JSON"));
        assertTrue(prompt.contains("MUST: flow-explorer-test-scenario-design"));
        assertTrue(prompt.contains("SHOULD: flow-explorer-operational-grounding"));
        assertTrue(prompt.contains("SHOULD: flow-explorer-code-grounding"));
        assertFalse(prompt.contains("SHOULD: flow-explorer-map-persistence-section"));
        assertFalse(prompt.contains("SHOULD: flow-explorer-map-integrations-section"));
        assertTrue(prompt.contains("MUST: flow-explorer-map-persistence-section"));
        assertTrue(prompt.contains("MUST: flow-explorer-map-integrations-section"));
        assertTrue(prompt.contains("`sectionModes.PERSISTENCE` ma tryb `COMPACT`"));
        assertTrue(prompt.contains("`sectionModes.INTEGRATIONS` ma tryb `COMPACT`"));
        assertTrue(prompt.contains("COULD: record_tool_feedback"));
        assertTrue(prompt.contains("sectionModes"));
        assertTrue(prompt.contains("activeSectionIds"));
        assertTrue(prompt.contains("activeReportSectionIds: [OVERVIEW, FUNCTIONAL_FLOW, VALIDATIONS, PERSISTENCE, INTEGRATIONS]"));
        assertTrue(prompt.contains("reasoningEffort: high"));
        assertTrue(prompt.contains("Context clipping notes"));
        assertTrue(prompt.contains("Response contract artifact"));
        assertFalse(prompt.contains("preferuj `gitlab_read_java_method_slice`"));
        assertFalse(prompt.contains("Tool scope guidance"));
        assertFalse(prompt.contains("GitLab tools do not read endpoint functional scope from hidden ToolContext"));
        assertTrue(prompt.contains("Canonical tool inputs"));
        assertTrue(prompt.contains("selected projectName: `crm-service`"));
        assertTrue(prompt.contains("searchMode: `path-prefixes`"));
        assertTrue(prompt.contains("pathPrefixes: `src/main/java/com/example/customer`"));
        assertTrue(prompt.contains("Discovery Boundary Policy"));
        assertTrue(prompt.contains("File And Method Scope"));
        assertTrue(prompt.contains("Use `flow-explorer/compact-flow-manifest.md` as the canonical filePath + methodSelector list"));
        assertTrue(prompt.contains("Compact flow manifest"));
        assertTrue(prompt.contains("[CONTROLLER]"));
        assertTrue(prompt.contains("getCustomer L12-L24"));
        assertTrue(prompt.contains("embedded: flow-explorer/snippet-cards.md"));
        assertTrue(prompt.contains("public CustomerResponse getCustomer"));
        assertTrue(prompt.contains("\"overview\""));
        assertTrue(prompt.contains("\"sections\""));
        assertTrue(prompt.contains("FUNCTIONAL_FLOW"));
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
        assertTrue(prompt.contains("MUST: flow-explorer-risk-assessment"));
        assertTrue(prompt.contains("focusAreas: [VALIDATIONS, INTEGRATIONS]"));
        assertTrue(prompt.contains("reasoningEffort: high"));
        assertTrue(prompt.contains("Skup sie na ryzykach regresji CRM."));
    }

    @Test
    void shouldRenderDeepDiscoveryGoalSkillInCanonicalPrompt() {
        var preparation = service.prepare(deepDiscoveryRequest(), contextSnapshot());
        var prompt = preparation.prompt();

        assertTrue(prompt.contains("goal: DEEP_DISCOVERY"));
        assertTrue(prompt.contains("MUST: flow-explorer-deep-discovery"));
        assertTrue(prompt.contains("Obowiazuje dla celu DEEP_DISCOVERY"));
    }

    @Test
    void shouldOmitSectionSkillUsageWhenSectionModeIsOff() {
        var preparation = service.prepare(requestWithPersistenceOff(), contextSnapshot());
        var prompt = preparation.prompt();

        assertTrue(prompt.contains("sectionModes: FUNCTIONAL_FLOW=DEEP, VALIDATIONS=COMPACT, PERSISTENCE=OFF, INTEGRATIONS=COMPACT"));
        assertTrue(prompt.contains("activeSectionIds: [FUNCTIONAL_FLOW, VALIDATIONS, INTEGRATIONS]"));
        assertFalse(prompt.contains("flow-explorer-map-persistence-section"));
        assertTrue(prompt.contains("MUST: flow-explorer-map-integrations-section"));
        assertTrue(prompt.contains("`sectionModes.INTEGRATIONS` ma tryb `COMPACT`"));
    }

    @Test
    void shouldRenderFollowUpPromptAsMarkdownChatWithExplorationGuidance() {
        var preparation = followUpService.prepare(
                deepDiscoveryRequest(),
                contextSnapshot(),
                "Doprecyzuj walidacje i sprawdz, czy initial wynik niczego nie pominal."
        );
        var prompt = preparation.prompt();

        assertTrue(preparation.artifacts().isEmpty());
        assertTrue(preparation.artifactContents().isEmpty());
        assertTrue(prompt.contains("# Flow Explorer follow-up chat"));
        assertTrue(prompt.contains("Domyslnie odpowiedz w Markdown"));
        assertTrue(prompt.contains("Nie zwracaj pelnego JSON `flow-explorer-write-report`"));
        assertTrue(prompt.contains("Nie zakladaj, ze initial analysis przeczytala cala implementacje endpointu"));
        assertTrue(prompt.contains("domyslnie uzyj dostepnych Flow Explorer tools przed odpowiedzia"));
        assertTrue(prompt.contains("Repository `searchMode/pathPrefixes` sa domyslnym discovery scope"));
        assertTrue(prompt.contains("flow-explorer-map-persistence-section"));
        assertTrue(prompt.contains("flow-explorer-map-integrations-section"));
        assertTrue(prompt.contains("Docelowy odbiorca to analityk albo tester"));
        assertTrue(prompt.contains("Nie zaczynaj odpowiedzi od nazw klas, metod, beanow"));
        assertTrue(prompt.contains("systemId: crm-service"));
        assertTrue(prompt.contains("branchRef: feature/FLOW-42"));
        assertTrue(prompt.contains("repositories:"));
        assertTrue(prompt.contains("searchMode: `path-prefixes`"));
        assertTrue(prompt.contains("pathPrefixes: `src/main/java/com/example/customer`"));
        assertTrue(prompt.contains("goal: DEEP_DISCOVERY"));
        assertTrue(prompt.contains("Doprecyzuj walidacje"));
        assertFalse(prompt.contains("## Required JSON response contract"));
        assertFalse(prompt.contains("\"sections\""));
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
                List.of(FlowExplorerFocusArea.FUNCTIONAL_FLOW),
                null,
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
                null,
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
                null,
                "Pokaz flow funkcjonalny.",
                "gpt-5.4-mini",
                "high"
        );
    }

    private static FlowExplorerJobStartRequest requestWithPersistenceOff() {
        return new FlowExplorerJobStartRequest(
                "crm-service",
                "crm-service:GET:/api/customers/{id}",
                null,
                null,
                "feature/FLOW-42",
                FlowExplorerAnalysisGoal.TEST_SCENARIOS,
                List.of(FlowExplorerFocusArea.FUNCTIONAL_FLOW),
                List.of(
                        new FlowExplorerSectionModeRequest(
                                FlowExplorerResultSectionId.FUNCTIONAL_FLOW,
                                FlowExplorerResultSectionMode.DEEP
                        ),
                        new FlowExplorerSectionModeRequest(
                                FlowExplorerResultSectionId.VALIDATIONS,
                                FlowExplorerResultSectionMode.COMPACT
                        ),
                        new FlowExplorerSectionModeRequest(
                                FlowExplorerResultSectionId.PERSISTENCE,
                                FlowExplorerResultSectionMode.OFF
                        ),
                        new FlowExplorerSectionModeRequest(
                                FlowExplorerResultSectionId.INTEGRATIONS,
                                FlowExplorerResultSectionMode.COMPACT
                        )
                ),
                "Skup sie na jezyku zrozumialym dla testera.",
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
                        "src/main/java/com/example/CustomerProfileController.java",
                        12,
                        24,
                        "HIGH"
                ),
                List.of(new FlowExplorerRepositoryContext(
                        "crm-service",
                        "crm-service",
                        "platform/backend/crm-service",
                        "feature/FLOW-42",
                        "path-prefixes",
                        List.of("src/main/java/com/example/customer"),
                        true,
                        true,
                        List.of()
                )),
                List.of(new FlowExplorerFlowNode(
                        "src/main/java/com/example/CustomerProfileController.java",
                        "CONTROLLER",
                        "src/main/java/com/example/CustomerProfileController.java",
                        List.of(new FlowExplorerFlowMethod("getCustomer", 12, 24)),
                        "Endpoint handler.",
                        "HIGH",
                        List.of()
                )),
                List.of(),
                List.of(new FlowExplorerSnippetCard(
                        "crm-service:src/main/java/com/example/CustomerProfileController.java:L9-L27",
                        "crm-service",
                        "src/main/java/com/example/CustomerProfileController.java",
                        "CONTROLLER",
                        List.of(new FlowExplorerFlowMethod("getCustomer", 12, 24)),
                        9,
                        27,
                        9,
                        27,
                        100,
                        snippetCardTruncated,
                        "Endpoint handler.",
                        "// file: src/main/java/com/example/CustomerProfileController.java\npublic CustomerResponse getCustomer() {}",
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
