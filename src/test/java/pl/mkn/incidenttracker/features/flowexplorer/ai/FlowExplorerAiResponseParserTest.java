package pl.mkn.incidenttracker.features.flowexplorer.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerAnalysisGoal;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerResultSectionId;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerResultSectionMode;

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
        assertEquals(2, response.sections().size());
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
    void shouldParseJsonFromFencedBlock() {
        var response = parser.parse("""
                Odpowiedz:
                ```json
                {"overview":{"markdown":"OK"},"confidence":"medium"}
                ```
                """);

        assertEquals("OK", response.overview().markdown());
        assertEquals("medium", response.confidence());
    }

    @Test
    void shouldReturnFallbackForInvalidJson() {
        var response = parser.parse("to nie jest json");

        assertEquals("Nie udalo sie sparsowac odpowiedzi AI do kontraktu Flow Explorer.",
                response.overview().markdown());
        assertEquals("low", response.confidence());
        assertTrue(response.globalVisibilityLimits().stream()
                .anyMatch(limit -> limit.contains("not valid JSON")));
    }
}
