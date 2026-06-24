package pl.mkn.incidenttracker.features.flowexplorer.ai.preparation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.features.flowexplorer.ai.FlowExplorerAiResponse;
import pl.mkn.incidenttracker.features.flowexplorer.ai.FlowExplorerFollowUpChatRequest;
import pl.mkn.incidenttracker.features.flowexplorer.ai.FlowExplorerFollowUpChatTurn;
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
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerResultOverview;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerResultResponse;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerResultSection;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerResultSectionId;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerResultSectionMode;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowExplorerFollowUpPromptPreparationServiceTest {

    private final FlowExplorerFollowUpPromptPreparationService service =
            new FlowExplorerFollowUpPromptPreparationService(
                    new FlowExplorerArtifactService(new ObjectMapper())
            );

    @Test
    void shouldAlignFollowUpPromptWithGoalBasedInitialResult() {
        var preparation = service.prepare(request());
        var prompt = preparation.prompt();
        var initialResult = preparation.artifactContents().get("flow-explorer/initial-result.md");

        assertTrue(prompt.contains("# Flow Explorer follow-up prompt"));
        assertTrue(prompt.contains("Nie generuj ponownie calego initial resultu"));
        assertTrue(prompt.contains("Follow-up sluzy do wyjatkow, doprecyzowan i waskich dopytan"));
        assertTrue(prompt.contains("Uzywaj stalych punktow odniesienia: Overview oraz aktywne sekcje initial resultu"));
        assertTrue(prompt.contains("Sekcja OFF mogla byc celowo pominieta w initial result"));
        assertTrue(prompt.contains("Jezeli pytanie dotyczy sekcji `DEEP`, najpierw wykorzystaj initial result"));
        assertTrue(prompt.contains("Jezeli pytanie dotyczy sekcji `COMPACT`, mozesz dociagnac szczegoly przez dozwolone tools"));
        assertTrue(prompt.contains("reasoningEffort: medium"));
        assertTrue(prompt.contains("sectionModes: FUNCTIONAL_FLOW=DEEP"));
        assertTrue(prompt.contains("activeSectionIds: [FUNCTIONAL_FLOW, VALIDATIONS, PERSISTENCE, INTEGRATIONS]"));
        assertTrue(prompt.contains("## Current user follow-up"));
        assertTrue(prompt.contains("Ktore walidacje statusu klienta sa juz potwierdzone?"));
        assertTrue(prompt.contains("- user: Czy endpoint obsluguje brak klienta?"));
        assertTrue(prompt.contains("- assistant: Tak, zwraca 404 gdy CRM aggregate nie istnieje."));
        assertTrue(prompt.contains("- gitlab/follow-up-file-chunk: 1 items"));
        assertTrue(prompt.contains("Przed nowym GitLab albo operational context tool call sprawdz `canonical-tool-inputs.md`"));
        assertTrue(prompt.contains("Do not call repository discovery or endpoint context rebuild"));

        assertTrue(initialResult.contains("### Overview"));
        assertTrue(initialResult.contains("#### FUNCTIONAL_FLOW | Functional flow | mode=DEEP"));
        assertTrue(initialResult.contains("#### VALIDATIONS | Validations | mode=COMPACT"));
        assertTrue(initialResult.contains("CRM customer id is validated before repository lookup."));
        assertTrue(initialResult.contains("- visibilityLimits: Runtime database state was not queried."));
        assertTrue(initialResult.contains("- globalOpenQuestions: Confirm inactive CRM customer status mapping."));
        assertFalse(preparation.artifactContents().containsKey(FlowExplorerArtifactService.RESPONSE_CONTRACT_ARTIFACT));
    }

    private static FlowExplorerFollowUpChatRequest request() {
        return new FlowExplorerFollowUpChatRequest(
                initialRequest(),
                contextSnapshot(),
                initialResult(),
                List.of(new AnalysisEvidenceSection(
                        "gitlab",
                        "follow-up-file-chunk",
                        List.of(new AnalysisEvidenceItem(
                                "CustomerService.getCustomer",
                                List.of(new AnalysisEvidenceAttribute("lines", "L30-L44"))
                        ))
                )),
                List.of(
                        new FlowExplorerFollowUpChatTurn("user", "Czy endpoint obsluguje brak klienta?"),
                        new FlowExplorerFollowUpChatTurn(
                                "assistant",
                                "Tak, zwraca 404 gdy CRM aggregate nie istnieje."
                        )
                ),
                "Ktore walidacje statusu klienta sa juz potwierdzone?"
        );
    }

    private static FlowExplorerJobStartRequest initialRequest() {
        return new FlowExplorerJobStartRequest(
                "crm-service",
                "crm-service:GET:/api/customers/{id}",
                "GET",
                "/api/customers/{id}",
                "feature/FLOW-42",
                FlowExplorerAnalysisGoal.TEST_SCENARIOS,
                List.of(FlowExplorerFocusArea.FUNCTIONAL_FLOW),
                null,
                "Skup sie na walidacjach CRM.",
                "gpt-5.4-mini",
                "medium"
        );
    }

    private static FlowExplorerResultResponse initialResult() {
        return new FlowExplorerResultResponse(
                "COMPLETED",
                "crm-service",
                "crm-service:GET:/api/customers/{id}",
                "GET",
                "/api/customers/{id}",
                "feature/FLOW-42",
                FlowExplorerAnalysisGoal.TEST_SCENARIOS,
                "canonical prompt",
                new FlowExplorerAiResponse(
                        FlowExplorerAnalysisGoal.TEST_SCENARIOS,
                        "business_or_system_analyst_tester",
                        new FlowExplorerResultOverview(
                                "Tester wants to validate CRM customer lookup.",
                                "high",
                                List.of("CustomerController.getCustomer L12-L24")
                        ),
                        List.of(
                                new FlowExplorerResultSection(
                                        FlowExplorerResultSectionId.FUNCTIONAL_FLOW,
                                        "Functional flow",
                                        FlowExplorerResultSectionMode.DEEP,
                                        "Controller delegates CRM customer lookup to CustomerService.",
                                        List.of("CustomerController.getCustomer L12-L24"),
                                        List.of(),
                                        List.of()
                                ),
                                new FlowExplorerResultSection(
                                        FlowExplorerResultSectionId.VALIDATIONS,
                                        "Validations",
                                        FlowExplorerResultSectionMode.COMPACT,
                                        "CRM customer id is validated before repository lookup.",
                                        List.of("CustomerService.getCustomer L30-L44"),
                                        List.of("Runtime database state was not queried."),
                                        List.of("Confirm inactive CRM customer status mapping.")
                                ),
                                new FlowExplorerResultSection(
                                        FlowExplorerResultSectionId.PERSISTENCE,
                                        "Persistence",
                                        FlowExplorerResultSectionMode.COMPACT,
                                        "CustomerRepository.findById loads CRM aggregate.",
                                        List.of("CustomerRepository.findById L10-L18"),
                                        List.of(),
                                        List.of()
                                ),
                                new FlowExplorerResultSection(
                                        FlowExplorerResultSectionId.INTEGRATIONS,
                                        "Integrations",
                                        FlowExplorerResultSectionMode.COMPACT,
                                        "No outbound integration is visible in initial flow.",
                                        List.of(),
                                        List.of(),
                                        List.of()
                                )
                        ),
                        List.of("Runtime database state was not queried."),
                        List.of("Confirm inactive CRM customer status mapping."),
                        List.of("CustomerService.getCustomer L30-L44"),
                        "high"
                ),
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
                "crm-service:GET:/api/customers/{id}",
                "GET",
                "/api/customers/{id}",
                new FlowExplorerEndpointContext(
                        "crm-service:GET:/api/customers/{id}",
                        List.of("GET"),
                        "/api/customers/{id}",
                        "/api/customers/{id}",
                        "CustomerController",
                        "getCustomer",
                        "src/main/java/com/example/crm/CustomerController.java",
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
                        "src/main/java/com/example/crm/CustomerController.java",
                        "CONTROLLER",
                        "src/main/java/com/example/crm/CustomerController.java",
                        List.of(new FlowExplorerFlowMethod("getCustomer", 12, 24)),
                        "Endpoint handler.",
                        "HIGH",
                        List.of()
                )),
                List.of(),
                List.of(new FlowExplorerSnippetCard(
                        "crm-service:src/main/java/com/example/crm/CustomerController.java:L9-L27",
                        "crm-service",
                        "src/main/java/com/example/crm/CustomerController.java",
                        "CONTROLLER",
                        List.of(new FlowExplorerFlowMethod("getCustomer", 12, 24)),
                        9,
                        27,
                        9,
                        27,
                        100,
                        false,
                        "Endpoint handler.",
                        "public CustomerResponse getCustomer(String id) {}",
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
                        47,
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
