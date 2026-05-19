package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.response;

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
                  "affectedProcess": "Obsluga zamowienia",
                  "affectedBoundedContext": "Ordering",
                  "affectedTeam": "Orders Team",
                  "functionalAnalysis": "Timeout dotyka procesu zamowienia, ktory pobiera katalog przed finalizacja.",
                  "technicalAnalysis": "Sprawdz `CatalogClient`, latency downstream i konfiguracje timeoutu.",
                  "confidence": "high",
                  "visibilityLimits": ["Brak potwierdzenia metryk downstream."]
                }
                """);

        assertTrue(result.structuredResponse());
        assertFalse(result.fallbackResponseUsed());
        assertEquals("DOWNSTREAM_TIMEOUT", result.response().detectedProblem());
        assertEquals("Obsluga zamowienia", result.response().affectedProcess());
        assertTrue(result.response().functionalAnalysis().contains("procesu zamowienia"));
        assertTrue(result.response().technicalAnalysis().contains("`CatalogClient`"));
        assertEquals("high", result.response().confidence());
        assertEquals(1, result.response().visibilityLimits().size());
    }

    @Test
    void shouldParseFencedJsonBlock() {
        var result = parser.parse("""
                Here is the result:

                ```json
                {
                  "detectedProblem": "DATA_NOT_FOUND",
                  "affectedProcess": "nieustalone",
                  "affectedBoundedContext": "nieustalone",
                  "affectedTeam": "nieustalone",
                  "functionalAnalysis": "Brak danych przerywa odczyt aktywnej sprawy.",
                  "technicalAnalysis": "Sprawdz repozytorium i rekord w DB.",
                  "confidence": "medium",
                  "visibilityLimits": []
                }
                ```
                """);

        assertTrue(result.structuredResponse());
        assertEquals("DATA_NOT_FOUND", result.response().detectedProblem());
        assertEquals("medium", result.response().confidence());
        assertEquals("nieustalone", result.response().affectedTeam());
    }

    @Test
    void shouldParseJsonObjectEmbeddedAfterProse() {
        var result = parser.parse("""
                I have all the evidence needed. Let me compose the final analysis.

                {
                  "detectedProblem": "CLASS_CAST_EXCEPTION",
                  "affectedProcess": "credit-decision-process",
                  "affectedBoundedContext": "product-configuration",
                  "affectedTeam": "nieustalone",
                  "functionalAnalysis": "Blad mapowania daty dotyka pobierania produktow klienta.",
                  "technicalAnalysis": "Obsluz `LocalDateTime` w mapperze wskazanym przez stacktrace.",
                  "confidence": "high",
                  "visibilityLimits": []
                }
                """);

        assertTrue(result.structuredResponse());
        assertFalse(result.fallbackResponseUsed());
        assertEquals("CLASS_CAST_EXCEPTION", result.response().detectedProblem());
        assertEquals("high", result.response().confidence());
    }

    @Test
    void shouldIgnoreNonJsonBracesBeforeEmbeddedJsonObject() {
        var result = parser.parse("""
                Note {not json} before the final result.

                {
                  "detectedProblem": "DOWNSTREAM_TIMEOUT",
                  "affectedProcess": "nieustalone",
                  "affectedBoundedContext": "nieustalone",
                  "affectedTeam": "nieustalone",
                  "functionalAnalysis": "Timeout dotyka pobrania katalogu.",
                  "technicalAnalysis": "Sprawdz klienta HTTP i downstream.",
                  "confidence": "medium",
                  "visibilityLimits": []
                }
                """);

        assertTrue(result.structuredResponse());
        assertEquals("DOWNSTREAM_TIMEOUT", result.response().detectedProblem());
    }

    @Test
    void shouldPreferLaterCompleteEmbeddedJsonOverEarlierPartialJsonObject() {
        var result = parser.parse("""
                {"status": "ready"}

                {
                  "detectedProblem": "DATA_NOT_FOUND",
                  "affectedProcess": "nieustalone",
                  "affectedBoundedContext": "nieustalone",
                  "affectedTeam": "nieustalone",
                  "functionalAnalysis": "Brak danych dla caseId przerywa odczyt aktywnej sprawy.",
                  "technicalAnalysis": "Sprawdz predykat repozytorium i rekord w DB.",
                  "confidence": "medium",
                  "visibilityLimits": []
                }
                """);

        assertTrue(result.structuredResponse());
        assertFalse(result.fallbackResponseUsed());
        assertEquals("DATA_NOT_FOUND", result.response().detectedProblem());
    }

    @Test
    void shouldParseJsonWithMarkdownStrings() {
        var result = parser.parse("""
                {
                  "detectedProblem": "JPA_QUERY_EMPTY",
                  "affectedProcess": "Obsluga sprawy",
                  "affectedBoundedContext": "Case Management",
                  "affectedTeam": "Case API Team",
                  "functionalAnalysis": "**Potwierdzone:** `caseId` trafia do odczytu aktywnej sprawy.\\n- Incydent przerywa proces w momencie oczekiwanego rekordu.",
                  "technicalAnalysis": "`ActiveCaseRecordController.getActiveCaseRecordForCaseId` odczytuje aktywna sprawe.\\n- Sprawdz `orElseThrow()` i predykat statusow.",
                  "confidence": "medium",
                  "visibilityLimits": ["Brak weryfikacji DB."]
                }
                """);

        assertTrue(result.structuredResponse());
        assertTrue(result.response().functionalAnalysis().contains("**Potwierdzone:**"));
        assertTrue(result.response().technicalAnalysis().contains("`ActiveCaseRecordController.getActiveCaseRecordForCaseId`"));
    }

    @Test
    void shouldFallbackWhenMalformedJsonHasNoParseableJson() {
        var raw = "{ \"detectedProblem\": \"BROKEN\",";

        var result = parser.parse(raw);

        assertFalse(result.structuredResponse());
        assertTrue(result.fallbackResponseUsed());
        assertEquals("AI_UNSTRUCTURED_RESPONSE", result.response().detectedProblem());
        assertEquals(raw, result.response().functionalAnalysis());
        assertTrue(result.response().technicalAnalysis().contains("Nie udalo sie sparsowac"));
    }

    @Test
    void shouldNotParseLabeledFieldsWhenResponseIsNotJson() {
        var raw = """
                detectedProblem: DOWNSTREAM_TIMEOUT
                functionalAnalysis: Timeout w kliencie HTTP.
                technicalAnalysis: Sprawdz downstream.
                """.trim();

        var result = parser.parse(raw);

        assertFalse(result.structuredResponse());
        assertTrue(result.fallbackResponseUsed());
        assertEquals("AI_UNSTRUCTURED_RESPONSE", result.response().detectedProblem());
        assertEquals(raw, result.response().functionalAnalysis());
        assertTrue(result.response().technicalAnalysis().contains("Nie udalo sie sparsowac"));
    }

    @Test
    void shouldPreservePartiallyParsedFieldsWhenRequiredFieldIsMissing() {
        var result = parser.parse("""
                {
                  "detectedProblem": "DOWNSTREAM_TIMEOUT",
                  "affectedProcess": "Zamowienia",
                  "affectedBoundedContext": "Ordering",
                  "affectedTeam": "Orders Team",
                  "functionalAnalysis": "Timeout widoczny w procesie zamowienia.",
                  "confidence": "medium",
                  "visibilityLimits": ["Brak technicalAnalysis."]
                }
                """);

        assertFalse(result.structuredResponse());
        assertTrue(result.fallbackResponseUsed());
        assertEquals("DOWNSTREAM_TIMEOUT", result.response().detectedProblem());
        assertEquals("Timeout widoczny w procesie zamowienia.", result.response().functionalAnalysis());
        assertTrue(result.response().technicalAnalysis().contains("Nie udalo sie sparsowac"));
        assertEquals("Orders Team", result.response().affectedTeam());
        assertEquals("medium", result.response().confidence());
        assertEquals(1, result.response().visibilityLimits().size());
    }
}
