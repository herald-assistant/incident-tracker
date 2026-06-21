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
