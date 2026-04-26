package pl.mkn.incidenttracker.analysis.ai.copilot.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotResponseParserTest {

    private final CopilotResponseParser parser = new CopilotResponseParser(new ObjectMapper());

    @Test
    void shouldParsePlainValidJson() {
        var result = parser.parse("""
                {
                  "detectedProblem": "DOWNSTREAM_TIMEOUT",
                  "summary": "Widoczny timeout w `CatalogClient`.\\n- Log wskazuje `SocketTimeoutException`.",
                  "recommendedAction": "- Zweryfikuj timeout downstream.",
                  "rationale": "- Log i kod wskazuja ten sam klient HTTP.",
                  "affectedFunction": "Pobranie katalogu dla procesu zamowienia.",
                  "affectedProcess": "Obsluga zamowienia",
                  "affectedBoundedContext": "Ordering",
                  "affectedTeam": "Orders Team",
                  "confidence": "high",
                  "evidenceReferences": [
                    {
                      "field": "detectedProblem",
                      "artifactId": "01-elasticsearch-logs.md",
                      "itemId": "log-1",
                      "claim": "Log pokazuje timeout."
                    }
                  ],
                  "visibilityLimits": ["Brak potwierdzenia metryk downstream."]
                }
                """);

        assertTrue(result.structuredResponse());
        assertFalse(result.fallbackResponseUsed());
        assertEquals("DOWNSTREAM_TIMEOUT", result.response().detectedProblem());
        assertTrue(result.response().summary().contains("`CatalogClient`"));
        assertEquals("high", result.response().confidence());
        assertEquals(1, result.response().evidenceReferences().size());
        assertEquals("01-elasticsearch-logs.md", result.response().evidenceReferences().get(0).artifactId());
        assertEquals(1, result.response().visibilityLimits().size());
    }

    @Test
    void shouldParseFencedJsonBlock() {
        var result = parser.parse("""
                Here is the result:

                ```json
                {
                  "detectedProblem": "DATA_NOT_FOUND",
                  "summary": "Brak danych dla `caseId`.",
                  "recommendedAction": "- Sprawdz rekord w DB.",
                  "rationale": "- Repozytorium zwrocilo pusty wynik.",
                  "affectedFunction": "Odczyt aktywnej sprawy.",
                  "affectedProcess": "nieustalone",
                  "affectedBoundedContext": "nieustalone",
                  "affectedTeam": "nieustalone",
                  "confidence": "medium",
                  "evidenceReferences": [
                    {
                      "field": "summary",
                      "artifactId": "01-elasticsearch-logs.md",
                      "itemId": "log-2",
                      "claim": "Log pokazuje brak danych."
                    }
                  ],
                  "visibilityLimits": []
                }
                ```
                """);

        assertTrue(result.structuredResponse());
        assertEquals("DATA_NOT_FOUND", result.response().detectedProblem());
        assertEquals("medium", result.response().confidence());
        assertEquals(1, result.response().evidenceReferences().size());
    }

    @Test
    void shouldParseJsonWithMarkdownStrings() {
        var result = parser.parse("""
                {
                  "detectedProblem": "JPA_QUERY_EMPTY",
                  "summary": "**Potwierdzone:** `orElseThrow()` nie znalazlo rekordu.\\n- Widoczny `EntityNotFoundException`.",
                  "recommendedAction": "- Zespol backend: sprawdz predykat repozytorium.",
                  "rationale": "- Stacktrace i kod sa spojne.",
                  "affectedFunction": "`ActiveCaseRecordController.getActiveCaseRecordForCaseId` odczytuje aktywna sprawe.",
                  "affectedProcess": "Obsluga sprawy",
                  "affectedBoundedContext": "Case Management",
                  "affectedTeam": "Case API Team",
                  "confidence": "medium",
                  "evidenceReferences": [],
                  "visibilityLimits": ["Brak weryfikacji DB."]
                }
                """);

        assertTrue(result.structuredResponse());
        assertTrue(result.response().summary().contains("**Potwierdzone:**"));
        assertTrue(result.response().affectedFunction().contains("`ActiveCaseRecordController.getActiveCaseRecordForCaseId`"));
    }

    @Test
    void shouldFallbackWhenMalformedJsonHasNoParseableJson() {
        var raw = "{ \"detectedProblem\": \"BROKEN\",";

        var result = parser.parse(raw);

        assertFalse(result.structuredResponse());
        assertTrue(result.fallbackResponseUsed());
        assertEquals("AI_UNSTRUCTURED_RESPONSE", result.response().detectedProblem());
        assertEquals(raw, result.response().summary());
        assertEquals("", result.response().affectedFunction());
    }

    @Test
    void shouldNotParseLabeledFieldsWhenResponseIsNotJson() {
        var raw = """
                detectedProblem: DOWNSTREAM_TIMEOUT
                summary: Timeout w kliencie HTTP.
                recommendedAction: Sprawdz downstream.
                rationale: Logi wskazuja timeout.
                affectedFunction: Pobranie katalogu.
                """.trim();

        var result = parser.parse(raw);

        assertFalse(result.structuredResponse());
        assertTrue(result.fallbackResponseUsed());
        assertEquals("AI_UNSTRUCTURED_RESPONSE", result.response().detectedProblem());
        assertEquals(raw, result.response().summary());
        assertEquals("", result.response().affectedFunction());
    }

    @Test
    void shouldPreservePartiallyParsedFieldsWhenRequiredFieldIsMissing() {
        var result = parser.parse("""
                {
                  "detectedProblem": "DOWNSTREAM_TIMEOUT",
                  "summary": "Timeout widoczny w logach.",
                  "recommendedAction": "- Sprawdz klienta HTTP.",
                  "rationale": "- Log wskazuje downstream.",
                  "affectedProcess": "Zamowienia",
                  "affectedBoundedContext": "Ordering",
                  "affectedTeam": "Orders Team",
                  "confidence": "medium",
                  "evidenceReferences": [],
                  "visibilityLimits": ["Brak affectedFunction."]
                }
                """);

        assertFalse(result.structuredResponse());
        assertTrue(result.fallbackResponseUsed());
        assertEquals("DOWNSTREAM_TIMEOUT", result.response().detectedProblem());
        assertEquals("Timeout widoczny w logach.", result.response().summary());
        assertEquals("- Sprawdz klienta HTTP.", result.response().recommendedAction());
        assertEquals("", result.response().affectedFunction());
        assertEquals("Orders Team", result.response().affectedTeam());
        assertEquals("medium", result.response().confidence());
        assertEquals(1, result.response().visibilityLimits().size());
    }
}
