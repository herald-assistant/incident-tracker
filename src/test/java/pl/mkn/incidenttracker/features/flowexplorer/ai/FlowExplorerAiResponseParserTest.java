package pl.mkn.incidenttracker.features.flowexplorer.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowExplorerAiResponseParserTest {

    private final FlowExplorerAiResponseParser parser = new FlowExplorerAiResponseParser(new ObjectMapper());

    @Test
    void shouldParseValidJsonResponse() {
        var response = parser.parse("""
                {
                  "userIntentSummary": "Tester chce poznac endpoint klienta.",
                  "audienceSummary": "Endpoint pobiera klienta po id.",
                  "endpointContract": {
                    "method": "GET",
                    "path": "/api/customers/{id}",
                    "purpose": "Pobranie klienta.",
                    "request": ["id w path"],
                    "response": ["CustomerResponse"],
                    "parameters": ["id"]
                  },
                  "flowSteps": [
                    {
                      "order": 1,
                      "title": "Controller",
                      "plainLanguage": "Przyjmuje request.",
                      "technicalGrounding": "CustomerController#getCustomer",
                      "sourceRefs": ["crm-service:CustomerController.java:L12-L24"]
                    }
                  ],
                  "businessRules": ["Klient musi istniec."],
                  "validations": ["id jest wymagane."],
                  "persistence": ["CustomerRepository"],
                  "externalIntegrations": [],
                  "testScenarios": ["Brak klienta zwraca 404."],
                  "risksAndEdgeCases": ["Brak uprawnien."],
                  "openQuestions": [],
                  "visibilityLimits": ["Nie widac runtime data."],
                  "sourceReferences": ["crm-service:CustomerController.java:L12-L24"],
                  "confidence": "HIGH"
                }
                """);

        assertEquals("Tester chce poznac endpoint klienta.", response.userIntentSummary());
        assertEquals("GET", response.endpointContract().method());
        assertEquals("/api/customers/{id}", response.endpointContract().path());
        assertEquals(1, response.flowSteps().size());
        assertEquals("Controller", response.flowSteps().get(0).title());
        assertEquals("Klient musi istniec.", response.businessRules().get(0));
        assertEquals("high", response.confidence());
    }

    @Test
    void shouldParseJsonFromFencedBlock() {
        var response = parser.parse("""
                Odpowiedz:
                ```json
                {"audienceSummary":"OK","confidence":"medium"}
                ```
                """);

        assertEquals("OK", response.audienceSummary());
        assertEquals("medium", response.confidence());
    }

    @Test
    void shouldReturnFallbackForInvalidJson() {
        var response = parser.parse("to nie jest json");

        assertNull(response.userIntentSummary());
        assertEquals("low", response.confidence());
        assertTrue(response.visibilityLimits().stream()
                .anyMatch(limit -> limit.contains("not valid JSON")));
    }
}
