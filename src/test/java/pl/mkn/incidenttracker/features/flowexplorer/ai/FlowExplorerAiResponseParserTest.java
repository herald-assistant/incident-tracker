package pl.mkn.incidenttracker.features.flowexplorer.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerAnalysisGoal;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerFocusArea;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerResultSectionId;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerResultSectionMode;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerResultSectionModeAssignment;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowExplorerAiResponseParserTest {

    private final FlowExplorerAiResponseParser parser = new FlowExplorerAiResponseParser(new ObjectMapper());

    @Test
    void shouldParseValidJsonResponse() {
        var response = parser.parse("""
                {
                  "goal": "DEEP_DISCOVERY",
                  "audience": "business_or_system_analyst_tester",
                  "overview": {
                    "markdown": "Tester chce poznac endpoint klienta.",
                    "confidence": "HIGH",
                    "sourceRefs": ["crm-service:CustomerController.java:L12-L24"]
                  },
                  "sections": [
                    {
                      "id": "BUSINESS_FLOW_RULES",
                      "title": "Business flow/rules",
                      "mode": "deep",
                      "markdown": "Controller przyjmuje request i pobiera profil klienta.",
                      "sourceRefs": ["crm-service:CustomerController.java:L12-L24"],
                      "visibilityLimits": [],
                      "openQuestions": []
                    },
                    {
                      "id": "VALIDATIONS",
                      "title": "Validations",
                      "mode": "compact",
                      "markdown": "id jest wymagane.",
                      "sourceRefs": [],
                      "visibilityLimits": [],
                      "openQuestions": []
                    },
                    {
                      "id": "PERSISTENCE",
                      "title": "Persistence",
                      "mode": "compact",
                      "markdown": "Repository pobiera profil klienta.",
                      "sourceRefs": ["crm-service:CustomerRepository.java:L10-L18"],
                      "visibilityLimits": [],
                      "openQuestions": []
                    },
                    {
                      "id": "INTEGRATIONS",
                      "title": "Integrations",
                      "mode": "compact",
                      "markdown": "Initial evidence nie pokazuje integracji zewnetrznej.",
                      "sourceRefs": [],
                      "visibilityLimits": ["Nie widac klienta downstream w initial flow."],
                      "openQuestions": []
                    }
                  ],
                  "globalVisibilityLimits": ["Nie widac runtime data."],
                  "globalOpenQuestions": [],
                  "sourceReferences": ["crm-service:CustomerController.java:L12-L24"],
                  "confidence": "HIGH"
                }
                """);

        assertEquals(FlowExplorerAnalysisGoal.DEEP_DISCOVERY, response.goal());
        assertEquals("business_or_system_analyst_tester", response.audience());
        assertEquals("Tester chce poznac endpoint klienta.", response.overview().markdown());
        assertEquals("high", response.overview().confidence());
        assertEquals(4, response.sections().size());
        assertEquals(FlowExplorerResultSectionId.BUSINESS_FLOW_RULES, response.sections().get(0).id());
        assertEquals(FlowExplorerResultSectionMode.DEEP, response.sections().get(0).mode());
        assertEquals("Controller przyjmuje request i pobiera profil klienta.", response.sections().get(0).markdown());
        assertEquals("id jest wymagane.", response.sections().get(1).markdown());
        assertEquals("Nie widac runtime data.", response.globalVisibilityLimits().get(0));
        assertEquals("high", response.confidence());
    }

    @Test
    void shouldParseTestScenariosGoalResponse() {
        var response = parser.parse("""
                {
                  "goal": "TEST_SCENARIOS",
                  "audience": "business_or_system_analyst_tester",
                  "overview": {
                    "markdown": "Plan testow dla odczytu profilu klienta CRM powinien pokryc happy path, brak klienta i dane niepoprawne.",
                    "confidence": "medium",
                    "sourceRefs": ["crm-service:CustomerController.java:L12-L24"]
                  },
                  "sections": [
                    {
                      "id": "BUSINESS_FLOW_RULES",
                      "title": "Business flow/rules",
                      "mode": "deep",
                      "markdown": "- Setup: aktywny klient CRM istnieje.\\n- Akcja: tester pobiera profil po id.\\n- Oczekiwany rezultat: profil wraca bez zmiany stanu.",
                      "sourceRefs": ["crm-service:CustomerController.java:L12-L24"],
                      "visibilityLimits": [],
                      "openQuestions": []
                    },
                    {
                      "id": "VALIDATIONS",
                      "title": "Validations",
                      "mode": "deep",
                      "markdown": "- Setup: brak id albo id spoza katalogu CRM.\\n- Akcja: request GET.\\n- Oczekiwany rezultat: request zostaje odrzucony albo zwraca kontrolowany brak rekordu.",
                      "sourceRefs": ["crm-service:CustomerService.java:L30-L44"],
                      "visibilityLimits": [],
                      "openQuestions": ["Potwierdzic oczekiwany kod bledu dla nieistniejacego klienta."]
                    },
                    {
                      "id": "PERSISTENCE",
                      "title": "Persistence",
                      "mode": "compact",
                      "markdown": "Przygotowac rekord klienta CRM i sprawdzic, ze odczyt nie zmienia statusu profilu.",
                      "sourceRefs": ["crm-service:CustomerRepository.java:L10-L18"],
                      "visibilityLimits": [],
                      "openQuestions": []
                    },
                    {
                      "id": "INTEGRATIONS",
                      "title": "Integrations",
                      "mode": "compact",
                      "markdown": "Initial evidence nie pokazuje wywolania systemu zewnetrznego; test moze uzyc lokalnego fixture profilu.",
                      "sourceRefs": [],
                      "visibilityLimits": ["Nie widac klientow zewnetrznych w initial flow."],
                      "openQuestions": []
                    }
                  ],
                  "globalVisibilityLimits": ["Nie sprawdzono danych runtime."],
                  "globalOpenQuestions": ["Potwierdzic wymagany status klienta w CRM."],
                  "sourceReferences": ["crm-service:CustomerService.java:L30-L44"],
                  "confidence": "medium"
                }
                """);

        assertEquals(FlowExplorerAnalysisGoal.TEST_SCENARIOS, response.goal());
        assertEquals("medium", response.confidence());
        assertEquals(4, response.sections().size());
        assertEquals(FlowExplorerResultSectionId.VALIDATIONS, response.sections().get(1).id());
        assertEquals(FlowExplorerResultSectionMode.DEEP, response.sections().get(1).mode());
        assertTrue(response.sections().get(1).markdown().contains("brak id"));
        assertEquals(FlowExplorerResultSectionMode.COMPACT, response.sections().get(3).mode());
        assertTrue(response.globalOpenQuestions().contains("Potwierdzic wymagany status klienta w CRM."));
    }

    @Test
    void shouldParseRiskDetectionGoalResponse() {
        var response = parser.parse("""
                {
                  "goal": "RISK_DETECTION",
                  "audience": "business_or_system_analyst_tester",
                  "overview": {
                    "markdown": "Najwieksze ryzyko dla odczytu profilu klienta CRM to niejednoznaczne zachowanie dla brakujacego albo nieaktywnego klienta.",
                    "confidence": "medium",
                    "sourceRefs": ["crm-service:CustomerController.java:L12-L24"]
                  },
                  "sections": [
                    {
                      "id": "BUSINESS_FLOW_RULES",
                      "title": "Business flow/rules",
                      "mode": "compact",
                      "markdown": "- Typ: Inferencja\\n- Ryzyko: flow zaklada, ze klient CRM istnieje i moze byc pokazany.\\n- Skutek: tester musi potwierdzic zachowanie dla nieaktywnego klienta.",
                      "sourceRefs": ["crm-service:CustomerController.java:L12-L24"],
                      "visibilityLimits": [],
                      "openQuestions": ["Czy profil nieaktywnego klienta powinien byc widoczny?"]
                    },
                    {
                      "id": "VALIDATIONS",
                      "title": "Validations",
                      "mode": "deep",
                      "markdown": "- Typ: Fakt z evidence\\n- Ryzyko: request moze zostac odrzucony dla pustego id.\\n- Jak zamknac: przygotowac test pustego i nieznanego identyfikatora.",
                      "sourceRefs": ["crm-service:CustomerService.java:L30-L44"],
                      "visibilityLimits": [],
                      "openQuestions": []
                    },
                    {
                      "id": "PERSISTENCE",
                      "title": "Persistence",
                      "mode": "compact",
                      "markdown": "- Typ: Luka widocznosci\\n- Ryzyko: initial evidence pokazuje odczyt, ale nie pokazuje pelnego modelu statusow klienta.",
                      "sourceRefs": ["crm-service:CustomerRepository.java:L10-L18"],
                      "visibilityLimits": ["Nie widac encji klienta w initial snippetach."],
                      "openQuestions": []
                    },
                    {
                      "id": "INTEGRATIONS",
                      "title": "Integrations",
                      "mode": "deep",
                      "markdown": "- Typ: Luka widocznosci\\n- Ryzyko: nie widac wywolania systemu zewnetrznego, ale operational context moze wskazywac wlasciciela danych klienta.",
                      "sourceRefs": [],
                      "visibilityLimits": ["Nie widac klienta downstream w initial flow."],
                      "openQuestions": ["Czy dane profilu pochodza wylacznie z lokalnej bazy CRM?"]
                    }
                  ],
                  "globalVisibilityLimits": ["Nie sprawdzono runtime danych klienta."],
                  "globalOpenQuestions": ["Potwierdzic zachowanie dla nieaktywnego klienta CRM."],
                  "sourceReferences": ["crm-service:CustomerService.java:L30-L44"],
                  "confidence": "medium"
                }
                """);

        assertEquals(FlowExplorerAnalysisGoal.RISK_DETECTION, response.goal());
        assertEquals("medium", response.confidence());
        assertEquals(4, response.sections().size());
        assertEquals(FlowExplorerResultSectionMode.COMPACT, response.sections().get(0).mode());
        assertEquals(FlowExplorerResultSectionId.VALIDATIONS, response.sections().get(1).id());
        assertEquals(FlowExplorerResultSectionMode.DEEP, response.sections().get(1).mode());
        assertTrue(response.sections().get(2).markdown().contains("Luka widocznosci"));
        assertEquals(FlowExplorerResultSectionId.INTEGRATIONS, response.sections().get(3).id());
        assertTrue(response.globalOpenQuestions().contains("Potwierdzic zachowanie dla nieaktywnego klienta CRM."));
    }

    @Test
    void shouldValidateSectionModesAgainstFocusAreas() {
        var response = parser.parseForFocusAreas(
                responseJsonWithModes("TEST_SCENARIOS", "deep", "deep", "compact", "compact"),
                FlowExplorerAnalysisGoal.TEST_SCENARIOS,
                List.of(FlowExplorerFocusArea.BUSINESS_FLOW_RULES, FlowExplorerFocusArea.VALIDATIONS)
        );

        assertEquals(FlowExplorerAnalysisGoal.TEST_SCENARIOS, response.goal());
        assertEquals(FlowExplorerResultSectionMode.DEEP, response.sections().get(0).mode());
        assertEquals(FlowExplorerResultSectionMode.DEEP, response.sections().get(1).mode());
        assertEquals(FlowExplorerResultSectionMode.COMPACT, response.sections().get(2).mode());
        assertEquals(FlowExplorerResultSectionMode.COMPACT, response.sections().get(3).mode());
    }

    @Test
    void shouldAllowOffSectionToBeOmitted() {
        var response = parser.parse(
                responseJsonWithoutPersistence("DEEP_DISCOVERY", "deep", "compact", "compact"),
                FlowExplorerAnalysisGoal.DEEP_DISCOVERY,
                sectionModesWithPersistenceOff()
        );

        assertEquals(FlowExplorerAnalysisGoal.DEEP_DISCOVERY, response.goal());
        assertEquals(3, response.sections().size());
        assertEquals(FlowExplorerResultSectionId.BUSINESS_FLOW_RULES, response.sections().get(0).id());
        assertEquals(FlowExplorerResultSectionId.VALIDATIONS, response.sections().get(1).id());
        assertEquals(FlowExplorerResultSectionId.INTEGRATIONS, response.sections().get(2).id());
        assertTrue(response.sections().stream().noneMatch(section -> section.id() == FlowExplorerResultSectionId.PERSISTENCE));
    }

    @Test
    void shouldReturnFallbackWhenOffSectionIsReturned() {
        var response = parser.parse(
                responseJsonWithModes("DEEP_DISCOVERY", "deep", "compact", "off", "compact"),
                FlowExplorerAnalysisGoal.DEEP_DISCOVERY,
                sectionModesWithPersistenceOff()
        );

        assertFallback(response, "sections must contain exactly the active sectionModes items");
    }

    @Test
    void shouldReturnFallbackForFencedBlock() {
        var response = parser.parse("""
                Odpowiedz:
                ```json
                {"overview":{"markdown":"OK"},"confidence":"medium"}
                ```
                """);

        assertFallback(response, "not valid JSON");
    }

    @Test
    void shouldReturnFallbackForLegacyTopLevelFields() {
        var response = parser.parse(responseJsonWithModes(
                "DEEP_DISCOVERY",
                "deep",
                "compact",
                "compact",
                "compact",
                """
                ,
                  "testScenarios": []
                """
        ));

        assertFallback(response, "legacy top-level field: testScenarios");
    }

    @Test
    void shouldReturnFallbackWhenRequiredSectionIsMissing() {
        var response = parser.parse("""
                {
                  "goal": "DEEP_DISCOVERY",
                  "overview": {"markdown": "CRM customer lookup overview."},
                  "sections": [],
                  "confidence": "medium"
                }
                """);

        assertFallback(response, "sections must contain exactly the active sectionModes items");
    }

    @Test
    void shouldReturnFallbackWhenOverviewMarkdownIsMissing() {
        var response = parser.parse(responseJsonWithModes(
                        "DEEP_DISCOVERY",
                        "deep",
                        "compact",
                        "compact",
                        "compact"
                )
                .replace("\"markdown\": \"Overview dla CRM customer lookup.\",", "\"markdown\": \"\","));

        assertFallback(response, "overview.markdown is required");
    }

    @Test
    void shouldReturnFallbackWhenGoalIsUnknown() {
        var response = parser.parse(responseJsonWithModes("UNKNOWN", "deep", "compact", "compact", "compact"));

        assertFallback(response, "goal must be one of");
    }

    @Test
    void shouldReturnFallbackWhenSectionModeDoesNotMatchFocusAreas() {
        var response = parser.parseForFocusAreas(
                responseJsonWithModes("TEST_SCENARIOS", "compact", "deep", "compact", "compact"),
                FlowExplorerAnalysisGoal.TEST_SCENARIOS,
                List.of(FlowExplorerFocusArea.BUSINESS_FLOW_RULES, FlowExplorerFocusArea.VALIDATIONS)
        );

        assertFallback(response, "section.mode does not match request sectionModes");
    }

    @Test
    void shouldReturnFallbackForInvalidJson() {
        var response = parser.parse("to nie jest json");

        assertFallback(response, "not valid JSON");
    }

    private static void assertFallback(FlowExplorerAiResponse response, String expectedVisibilityLimitPart) {
        assertEquals("Nie udalo sie sparsowac odpowiedzi AI do kontraktu Flow Explorer.",
                response.overview().markdown());
        assertEquals("low", response.confidence());
        assertTrue(response.globalVisibilityLimits().stream()
                .anyMatch(limit -> limit.contains(expectedVisibilityLimitPart)));
        assertTrue(response.globalOpenQuestions().stream()
                .anyMatch(question -> question.contains("wymaganym formacie JSON")));
    }

    private static String responseJsonWithModes(
            String goal,
            String businessMode,
            String validationsMode,
            String persistenceMode,
            String integrationsMode
    ) {
        return responseJsonWithModes(goal, businessMode, validationsMode, persistenceMode, integrationsMode, "");
    }

    private static String responseJsonWithModes(
            String goal,
            String businessMode,
            String validationsMode,
            String persistenceMode,
            String integrationsMode,
            String extraTopLevelFields
    ) {
        return """
                {
                  "goal": "%s",
                  "audience": "business_or_system_analyst_tester",
                  "overview": {
                    "markdown": "Overview dla CRM customer lookup.",
                    "confidence": "high",
                    "sourceRefs": ["crm-service:CustomerController.java:L12-L24"]
                  },
                  "sections": [
                    {
                      "id": "BUSINESS_FLOW_RULES",
                      "title": "Business flow/rules",
                      "mode": "%s",
                      "markdown": "CRM customer lookup business behavior.",
                      "sourceRefs": ["crm-service:CustomerController.java:L12-L24"],
                      "visibilityLimits": [],
                      "openQuestions": []
                    },
                    {
                      "id": "VALIDATIONS",
                      "title": "Validations",
                      "mode": "%s",
                      "markdown": "CRM customer id validation.",
                      "sourceRefs": ["crm-service:CustomerService.java:L30-L44"],
                      "visibilityLimits": [],
                      "openQuestions": []
                    },
                    {
                      "id": "PERSISTENCE",
                      "title": "Persistence",
                      "mode": "%s",
                      "markdown": "CRM customer repository lookup.",
                      "sourceRefs": ["crm-service:CustomerRepository.java:L10-L18"],
                      "visibilityLimits": [],
                      "openQuestions": []
                    },
                    {
                      "id": "INTEGRATIONS",
                      "title": "Integrations",
                      "mode": "%s",
                      "markdown": "No external integration is visible in initial CRM flow.",
                      "sourceRefs": [],
                      "visibilityLimits": [],
                      "openQuestions": []
                    }
                  ],
                  "globalVisibilityLimits": [],
                  "globalOpenQuestions": [],
                  "sourceReferences": ["crm-service:CustomerController.java:L12-L24"],
                  "confidence": "high"%s
                }
                """.formatted(goal, businessMode, validationsMode, persistenceMode, integrationsMode, extraTopLevelFields);
    }

    private static String responseJsonWithoutPersistence(
            String goal,
            String businessMode,
            String validationsMode,
            String integrationsMode
    ) {
        return """
                {
                  "goal": "%s",
                  "audience": "business_or_system_analyst_tester",
                  "overview": {
                    "markdown": "Overview dla CRM customer lookup.",
                    "confidence": "high",
                    "sourceRefs": ["crm-service:CustomerController.java:L12-L24"]
                  },
                  "sections": [
                    {
                      "id": "BUSINESS_FLOW_RULES",
                      "title": "Business flow/rules",
                      "mode": "%s",
                      "markdown": "CRM customer lookup business behavior.",
                      "sourceRefs": ["crm-service:CustomerController.java:L12-L24"],
                      "visibilityLimits": [],
                      "openQuestions": []
                    },
                    {
                      "id": "VALIDATIONS",
                      "title": "Validations",
                      "mode": "%s",
                      "markdown": "CRM customer id validation.",
                      "sourceRefs": ["crm-service:CustomerService.java:L30-L44"],
                      "visibilityLimits": [],
                      "openQuestions": []
                    },
                    {
                      "id": "INTEGRATIONS",
                      "title": "Integrations",
                      "mode": "%s",
                      "markdown": "No external integration is visible in initial CRM flow.",
                      "sourceRefs": [],
                      "visibilityLimits": [],
                      "openQuestions": []
                    }
                  ],
                  "globalVisibilityLimits": [],
                  "globalOpenQuestions": [],
                  "sourceReferences": ["crm-service:CustomerController.java:L12-L24"],
                  "confidence": "high"
                }
                """.formatted(goal, businessMode, validationsMode, integrationsMode);
    }

    private static List<FlowExplorerResultSectionModeAssignment> sectionModesWithPersistenceOff() {
        return List.of(
                assignment(FlowExplorerResultSectionId.BUSINESS_FLOW_RULES, FlowExplorerResultSectionMode.DEEP),
                assignment(FlowExplorerResultSectionId.VALIDATIONS, FlowExplorerResultSectionMode.COMPACT),
                assignment(FlowExplorerResultSectionId.PERSISTENCE, FlowExplorerResultSectionMode.OFF),
                assignment(FlowExplorerResultSectionId.INTEGRATIONS, FlowExplorerResultSectionMode.COMPACT)
        );
    }

    private static FlowExplorerResultSectionModeAssignment assignment(
            FlowExplorerResultSectionId id,
            FlowExplorerResultSectionMode mode
    ) {
        return new FlowExplorerResultSectionModeAssignment(id, id.title(), mode);
    }
}
