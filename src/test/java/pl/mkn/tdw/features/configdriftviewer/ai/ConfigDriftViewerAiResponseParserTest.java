package pl.mkn.tdw.features.configdriftviewer.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiConclusion;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiExecutionStatus;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerAiObservationType;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicStatus;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigDriftViewerAiResponseParserTest {

    private final ConfigDriftViewerAiResponseParser parser =
            new ConfigDriftViewerAiResponseParser(new ObjectMapper());

    @Test
    void shouldAcceptGroundedObservationAndExplicitHypothesis() {
        var opinion = parser.parse("""
                {
                  "conclusion": "REVIEW_REQUIRED",
                  "confidence": "MEDIUM",
                  "summary": "Zmiana sekretu wymaga potwierdzenia.",
                  "observations": [
                    {
                      "observationId": "observation-1",
                      "type": "GROUNDED_OBSERVATION",
                      "summary": "Zmieniła się wrażliwa właściwość.",
                      "explanation": "Finding wskazuje zmianę.",
                      "differenceIds": ["difference-1"],
                      "findingIds": ["finding-1"],
                      "contextIds": [],
                      "codeGroundingIds": []
                    },
                    {
                      "observationId": "observation-2",
                      "type": "HYPOTHESIS",
                      "summary": "Rotacja mogła zostać pominięta.",
                      "explanation": "Brak widoczności wartości.",
                      "differenceIds": [],
                      "findingIds": [],
                      "contextIds": [],
                      "codeGroundingIds": []
                    }
                  ],
                  "recommendedHumanChecks": ["Potwierdź rotację z administratorem."],
                  "functionalImpacts": [],
                  "visibilityLimits": []
                }
                """,
                ConfigDriftViewerAiTestFixtures.deterministic(ConfigDriftViewerDeterministicStatus.REVIEW_REQUIRED),
                null
        );

        assertThat(opinion.executionStatus()).isEqualTo(ConfigDriftViewerAiExecutionStatus.COMPLETED);
        assertThat(opinion.conclusion()).isEqualTo(ConfigDriftViewerAiConclusion.REVIEW_REQUIRED);
        assertThat(opinion.observations()).extracting(value -> value.type())
                .containsExactly(
                        ConfigDriftViewerAiObservationType.GROUNDED_OBSERVATION,
                        ConfigDriftViewerAiObservationType.HYPOTHESIS
                );
    }

    @Test
    void shouldAcceptDeepFunctionalImpactOnlyWithKnownReferences() {
        var opinion = parser.parse(deepResponse("code-1", "context-system-1"),
                ConfigDriftViewerAiTestFixtures.deterministic(ConfigDriftViewerDeterministicStatus.REVIEW_REQUIRED),
                ConfigDriftViewerAiTestFixtures.deep()
        );

        assertThat(opinion.executionStatus()).isEqualTo(ConfigDriftViewerAiExecutionStatus.COMPLETED);
        assertThat(opinion.functionalImpacts()).singleElement().satisfies(impact -> {
            assertThat(impact.affectedFunctionality()).isEqualTo("Obsługa klientów");
            assertThat(impact.codeGroundingIds()).containsExactly("code-1");
            assertThat(impact.contextIds()).containsExactly("context-system-1");
        });
    }

    @Test
    void shouldFallbackForMissingGroundingOrUnknownReference() {
        var deterministic = ConfigDriftViewerAiTestFixtures.deterministic(
                ConfigDriftViewerDeterministicStatus.REVIEW_REQUIRED
        );
        var missingGrounding = parser.parse("""
                {
                  "conclusion":"NO_CONCERN",
                  "confidence":"HIGH",
                  "summary":"OK",
                  "observations":[{
                    "observationId":"o1",
                    "type":"GROUNDED_OBSERVATION",
                    "summary":"Bez referencji",
                    "explanation":"",
                    "differenceIds":[],
                    "findingIds":[],
                    "contextIds":[],
                    "codeGroundingIds":[]
                  }],
                  "recommendedHumanChecks":[],
                  "functionalImpacts":[],
                  "visibilityLimits":[]
                }
                """, deterministic, null);
        var unknownCode = parser.parse(
                deepResponse("invented-code", "context-system-1"),
                deterministic,
                ConfigDriftViewerAiTestFixtures.deep()
        );

        assertThat(missingGrounding.executionStatus()).isEqualTo(ConfigDriftViewerAiExecutionStatus.INCOMPLETE);
        assertThat(unknownCode.executionStatus()).isEqualTo(ConfigDriftViewerAiExecutionStatus.INCOMPLETE);
    }

    @Test
    void shouldRejectAttemptsToChangeStatusDiffFindingsOrOwnership() {
        var deterministic = ConfigDriftViewerAiTestFixtures.deterministic(
                ConfigDriftViewerDeterministicStatus.INCOMPLETE
        );
        for (var protectedField : new String[]{"status", "differences", "findings", "ownership"}) {
            var response = """
                    {
                      "conclusion":"NO_CONCERN",
                      "confidence":"HIGH",
                      "summary":"AI próbuje nadpisać wynik.",
                      "observations":[],
                      "recommendedHumanChecks":[],
                      "functionalImpacts":[],
                      "visibilityLimits":[],
                      "%s":[]
                    }
                    """.formatted(protectedField);

            var opinion = parser.parse(response, deterministic, null);

            assertThat(opinion.executionStatus())
                    .as("protected field %s", protectedField)
                    .isEqualTo(ConfigDriftViewerAiExecutionStatus.INCOMPLETE);
            assertThat(opinion.visibilityLimits()).anyMatch(limit -> limit.contains(protectedField));
        }
    }

    private String deepResponse(String codeId, String contextId) {
        return """
                {
                  "conclusion":"LIKELY_CONFIGURATION_ERROR",
                  "confidence":"HIGH",
                  "summary":"Rozjazd może dotyczyć obsługi klientów.",
                  "observations":[{
                    "observationId":"o1",
                    "type":"GROUNDED_OBSERVATION",
                    "summary":"Kod wiąże właściwość z obsługą klientów.",
                    "explanation":"Potwierdzenie w kodzie.",
                    "differenceIds":["difference-1"],
                    "findingIds":["finding-1"],
                    "contextIds":["%s"],
                    "codeGroundingIds":["%s"]
                  }],
                  "recommendedHumanChecks":["Potwierdź intencję z ownerem."],
                  "functionalImpacts":[{
                    "impactId":"impact-1",
                    "affectedFunctionality":"Obsługa klientów",
                    "impact":"Połączenie może używać innego sekretu.",
                    "confidence":"HIGH",
                    "hypothesis":false,
                    "systemIds":["crm-api"],
                    "differenceIds":["difference-1"],
                    "findingIds":["finding-1"],
                    "contextIds":["%s"],
                    "codeGroundingIds":["%s"]
                  }],
                  "visibilityLimits":[]
                }
                """.formatted(contextId, codeId, contextId, codeId);
    }
}
