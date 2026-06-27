package pl.mkn.tdw.features.flowexplorer.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionId;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowExplorerSectionRefineAiResponseParserTest {

    private final FlowExplorerSectionRefineAiResponseParser parser =
            new FlowExplorerSectionRefineAiResponseParser(new ObjectMapper());

    @Test
    void shouldParseDeepPersistenceRefineResponse() {
        var response = parser.parse("""
                {
                  "section": {
                    "id": "PERSISTENCE",
                    "title": "Persistence",
                    "mode": "deep",
                    "markdown": "| TABLE_NAME | COLUMN | SOURCE | SOURCE DETAILS |\\n| --- | --- | --- | --- |\\n| CUSTOMER | CUSTOMER_ID | REQUEST | Id klienta z requestu. |",
                    "sourceRefs": ["crm-service:CustomerRepository.java:L10-L22"],
                    "visibilityLimits": ["Nie widac triggerow DB."],
                    "openQuestions": ["Czy CUSTOMER_STATUS jest wyliczany poza endpointem?"]
                  },
                  "globalVisibilityLimits": ["Nie sprawdzono danych runtime."],
                  "globalOpenQuestions": ["Potwierdzic wlasciciela danych klienta."],
                  "sourceReferences": ["crm-service:CustomerRepository.java:L10-L22"],
                  "followUpPrompts": ["Sprawdz, czy status klienta ma osobna regule biznesowa."],
                  "confidence": "HIGH",
                  "changeSummary": ["Dodano mapowanie kolumn persistence.", "Zostawiono limit widocznosci dla runtime DB."]
                }
                """, FlowExplorerResultSectionId.PERSISTENCE, FlowExplorerResultSectionMode.DEEP);

        assertEquals(FlowExplorerResultSectionId.PERSISTENCE, response.section().id());
        assertEquals(FlowExplorerResultSectionMode.DEEP, response.section().mode());
        assertTrue(response.section().markdown().contains("TABLE_NAME"));
        assertEquals("crm-service:CustomerRepository.java:L10-L22", response.section().sourceRefs().get(0));
        assertEquals("Nie sprawdzono danych runtime.", response.globalVisibilityLimits().get(0));
        assertEquals("high", response.confidence());
        assertEquals("Dodano mapowanie kolumn persistence.", response.changeSummary().get(0));
    }

    @Test
    void shouldParseCompactFunctionalFlowRefineResponse() {
        var response = parser.parse("""
                {
                  "section": {
                    "id": "FUNCTIONAL_FLOW",
                    "title": "Functional flow",
                    "mode": "compact",
                    "markdown": "**Cel funkcjonalny:** Zwrocic profil klienta.\\n**Flow krok po kroku:** 1. Przyjecie requestu. 2. Odczyt profilu.\\n**Koordynacja i routing:** Brak alternatywnego routingu w widocznym kodzie.\\n**Kalkulacje i reguly funkcjonalne:** Brak kalkulacji w widocznym kodzie.\\n**Rozgalezienia zalezne od kontekstu:** Brak klienta konczy sie kontrolowanym bledem.\\n**Handoffy i efekty uboczne:** Odczyt bez zapisu.\\n**Akcent goal:** Doprecyzowano wariant braku klienta.",
                    "sourceRefs": [],
                    "visibilityLimits": [],
                    "openQuestions": []
                  },
                  "globalVisibilityLimits": [],
                  "globalOpenQuestions": [],
                  "sourceReferences": [],
                  "followUpPrompts": [],
                  "confidence": "medium",
                  "changeSummary": ["Doprecyzowano wariant braku klienta."]
                }
                """, FlowExplorerResultSectionId.FUNCTIONAL_FLOW, FlowExplorerResultSectionMode.COMPACT);

        assertEquals(FlowExplorerResultSectionId.FUNCTIONAL_FLOW, response.section().id());
        assertEquals(FlowExplorerResultSectionMode.COMPACT, response.section().mode());
        assertTrue(response.section().markdown().contains("**Cel funkcjonalny:**"));
        assertEquals("medium", response.confidence());
    }

    @Test
    void shouldRejectDifferentSectionId() {
        var exception = assertThrows(IllegalArgumentException.class, () -> parser.parse("""
                {
                  "section": {
                    "id": "INTEGRATIONS",
                    "title": "Integrations",
                    "mode": "deep",
                    "markdown": "Brak integracji zewnetrznej.",
                    "sourceRefs": [],
                    "visibilityLimits": [],
                    "openQuestions": []
                  },
                  "globalVisibilityLimits": [],
                  "globalOpenQuestions": [],
                  "sourceReferences": [],
                  "followUpPrompts": [],
                  "confidence": "medium",
                  "changeSummary": []
                }
                """, FlowExplorerResultSectionId.PERSISTENCE, FlowExplorerResultSectionMode.DEEP));

        assertTrue(exception.getMessage().contains("section.id does not match target section"));
    }

    @Test
    void shouldRejectModeChange() {
        var exception = assertThrows(IllegalArgumentException.class, () -> parser.parse("""
                {
                  "section": {
                    "id": "PERSISTENCE",
                    "title": "Persistence",
                    "mode": "compact",
                    "markdown": "Repository zapisuje profil klienta.",
                    "sourceRefs": [],
                    "visibilityLimits": [],
                    "openQuestions": []
                  },
                  "globalVisibilityLimits": [],
                  "globalOpenQuestions": [],
                  "sourceReferences": [],
                  "followUpPrompts": [],
                  "confidence": "medium",
                  "changeSummary": []
                }
                """, FlowExplorerResultSectionId.PERSISTENCE, FlowExplorerResultSectionMode.DEEP));

        assertTrue(exception.getMessage().contains("section.mode does not match target section mode"));
    }

    @Test
    void shouldRejectInvalidJson() {
        var exception = assertThrows(
                IllegalArgumentException.class,
                () -> parser.parse("not-json", FlowExplorerResultSectionId.PERSISTENCE, FlowExplorerResultSectionMode.DEEP)
        );

        assertTrue(exception.getMessage().contains("not valid JSON"));
    }

    @Test
    void shouldRejectOverviewUpdateAttempt() {
        var exception = assertThrows(IllegalArgumentException.class, () -> parser.parse("""
                {
                  "overview": {
                    "markdown": "Nie wolno aktualizowac overview przez refine sekcji."
                  },
                  "section": {
                    "id": "PERSISTENCE",
                    "title": "Persistence",
                    "mode": "deep",
                    "markdown": "Persistence details.",
                    "sourceRefs": [],
                    "visibilityLimits": [],
                    "openQuestions": []
                  },
                  "globalVisibilityLimits": [],
                  "globalOpenQuestions": [],
                  "sourceReferences": [],
                  "followUpPrompts": [],
                  "confidence": "medium",
                  "changeSummary": []
                }
                """, FlowExplorerResultSectionId.PERSISTENCE, FlowExplorerResultSectionMode.DEEP));

        assertTrue(exception.getMessage().contains("unexpected response field: overview"));
    }

    @Test
    void shouldNormalizeNullListsToEmptyLists() {
        var response = parser.parse("""
                {
                  "section": {
                    "id": "PERSISTENCE",
                    "title": "Persistence",
                    "mode": "deep",
                    "markdown": "Persistence details.",
                    "sourceRefs": null,
                    "visibilityLimits": null,
                    "openQuestions": null
                  },
                  "globalVisibilityLimits": null,
                  "globalOpenQuestions": null,
                  "sourceReferences": null,
                  "followUpPrompts": null,
                  "confidence": null,
                  "changeSummary": null
                }
                """, FlowExplorerResultSectionId.PERSISTENCE, FlowExplorerResultSectionMode.DEEP);

        assertTrue(response.section().sourceRefs().isEmpty());
        assertTrue(response.section().visibilityLimits().isEmpty());
        assertTrue(response.section().openQuestions().isEmpty());
        assertTrue(response.globalVisibilityLimits().isEmpty());
        assertTrue(response.globalOpenQuestions().isEmpty());
        assertTrue(response.sourceReferences().isEmpty());
        assertTrue(response.followUpPrompts().isEmpty());
        assertTrue(response.changeSummary().isEmpty());
        assertEquals("low", response.confidence());
    }
}
