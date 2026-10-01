package pl.mkn.tdw.features.deliveryscopecomplexity.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryAiResponseParserTest {

    private final DeliveryAiResponseParser parser = new DeliveryAiResponseParser(new ObjectMapper());

    @Test
    void shouldParseFencedResponseWithAllScopeDimensions() {
        var response = parser.parse("""
                ```json
                {
                  "classification":"DELIVERY",
                  "dimensions":{
                    "novelty":{"score":40,"scopeSignal":0.3,"evidence":["issues.md#CRM-1 | new component"]},
                    "structuralAndLogic":{"score":60,"scopeSignal":0.7,"evidence":["diffs.md#service | state flow"]},
                    "businessAndInvariants":{"score":45,"scopeSignal":0.2,"evidence":["issues.md#CRM-1 | changed rule"]},
                    "robustnessAndTests":{"score":30,"scopeSignal":0.4,"evidence":["diffs.md#test | error modes"]},
                    "refactorAndArchitecture":{"score":0,"scopeSignal":0.0,"evidence":[]},
                    "distribution":{"score":20,"scopeSignal":0.2,"evidence":["merge-requests.md#MR-1 | one context"]}
                  },
                  "confidence":0.84,
                  "evidenceSummary":["structuralAndLogic | diffs.md#service | state flow"],
                  "qualityFlags":[],
                  "visibilityLimits":[]
                }
                ```
                """);

        assertThat(response.classification()).isEqualTo("DELIVERY");
        assertThat(response.dimensions().structuralAndLogic().score()).isEqualTo(60);
        assertThat(response.dimensions().structuralAndLogic().scopeSignal()).isEqualTo(0.7);
        assertThat(response.dimensions().refactorAndArchitecture().evidence()).isEmpty();
    }

    @Test
    void shouldAcceptInsufficientEvidenceWithoutDimensions() {
        var response = parser.parse("""
                {"classification":"INSUFFICIENT_EVIDENCE","confidence":0.3,
                 "visibilityLimits":["No merged diff"]}
                """);

        assertThat(response.dimensions()).isNull();
        assertThat(response.visibilityLimits()).containsExactly("No merged diff");
    }

    @Test
    void shouldRejectMissingDimension() {
        assertThatThrownBy(() -> parser.parse(deliveryJson().replace(
                "\"distribution\":{\"score\":10,\"scopeSignal\":0.1,\"evidence\":[\"artifact | fact\"]},",
                ""
        )))
                .hasMessage("AI response dimension distribution is missing.");
    }

    @Test
    void shouldRejectOutOfRangeScore() {
        assertThatThrownBy(() -> parser.parse(deliveryJson().replaceFirst(
                "\\\"score\\\":10",
                "\\\"score\\\":101"
        )))
                .hasMessageContaining("dimension score must be between 0 and 100");
    }

    @Test
    void shouldRecoverEssentialFieldsWhenOptionalTailIsMalformed() {
        var malformed = deliveryJson().replace(
                "\"qualityFlags\":[]",
                "\"qualityFlags\":[\"broken tail\""
        );

        var response = parser.parse(malformed);

        assertThat(response.classification()).isEqualTo("DELIVERY");
        assertThat(response.dimensions().novelty().score()).isEqualTo(10);
        assertThat(response.qualityFlags()).contains("AI_METADATA_WARNING");
        assertThat(response.visibilityLimits()).anyMatch(limit -> limit.contains("odzyskano kompletny rdzeń"));
    }

    private String deliveryJson() {
        return """
                {
                  "classification":"DELIVERY",
                  "dimensions":{
                    "novelty":{"score":10,"scopeSignal":0.1,"evidence":["artifact | fact"]},
                    "structuralAndLogic":{"score":10,"scopeSignal":0.1,"evidence":["artifact | fact"]},
                    "businessAndInvariants":{"score":10,"scopeSignal":0.1,"evidence":["artifact | fact"]},
                    "robustnessAndTests":{"score":10,"scopeSignal":0.1,"evidence":["artifact | fact"]},
                    "refactorAndArchitecture":{"score":10,"scopeSignal":0.1,"evidence":["artifact | fact"]},
                    "distribution":{"score":10,"scopeSignal":0.1,"evidence":["artifact | fact"]},
                    "unused":{"score":0,"scopeSignal":0.0,"evidence":[]}
                  },
                  "confidence":0.8,
                  "evidenceSummary":[],
                  "qualityFlags":[],
                  "visibilityLimits":[]
                }
                """;
    }

    @Test
    void shouldRejectInvalidScopeSignalsScoresAndMissingEvidence() {
        for (var invalid : java.util.List.of("1.1", "-0.1", "1e999", "\"0.1\"")) {
            assertThatThrownBy(() -> parser.parse(deliveryJson().replace("\"scopeSignal\":0.1", "\"scopeSignal\":" + invalid)))
                    .hasMessageContaining("scopeSignal");
        }
        assertThatThrownBy(() -> parser.parse(deliveryJson().replace("\"score\":10", "\"score\":10.5"))).hasMessageContaining("score");
        assertThatThrownBy(() -> parser.parse(deliveryJson().replace("[\"artifact | fact\"]", "[]"))).hasMessageContaining("requires evidence");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"high\"", "true", "{}", "-0.1", "1.1", "1e999"})
    void shouldScoreUnchangedDimensionsWithInvalidConfidence(String confidence) {
        var baseline = parser.parse(deliveryJson());
        var response = parser.parse(deliveryJson().replace("\"confidence\":0.8", "\"confidence\":" + confidence));
        assertThat(response.dimensions()).isEqualTo(baseline.dimensions());
        assertThat(response.confidence()).isZero();
        assertThat(response.qualityFlags()).contains("AI_METADATA_WARNING");
        assertThat(response.visibilityLimits()).anyMatch(limit -> limit.contains("confidence:") && limit.contains("przyjęto 0"));
        assertThat(new DeliveryScopeScoringService().score(response).finalScore()).isEqualTo(new DeliveryScopeScoringService().score(baseline).finalScore());
    }

    @Test
    void shouldKeepScoreWhenConfidenceIsMissingAndOptionalMetadataIsMalformed() {
        var response = parser.parse(deliveryJson().replace("\"confidence\":0.8,", "")
                .replace("\"evidenceSummary\":[]", "\"evidenceSummary\":{}").replace("\"qualityFlags\":[]", "\"qualityFlags\":[\"CRM runtime unavailable\",42,null]"));
        assertThat(response.confidence()).isZero();
        assertThat(response.qualityFlags()).contains("CRM runtime unavailable", "AI_METADATA_WARNING");
        assertThat(response.visibilityLimits()).anyMatch(limit -> limit.contains("confidence:"))
                .anyMatch(limit -> limit.contains("evidenceSummary:"))
                .anyMatch(limit -> limit.contains("qualityFlags:") && limit.contains("niepoprawne elementy=2"));
    }

    @Test
    void shouldRecoverCoreWithoutConfidenceBeforeMalformedOptionalTail() {
        var response = parser.parse(deliveryJson().replace("\"confidence\":0.8,", "")
                .replace("\"qualityFlags\":[]", "\"qualityFlags\":[\"broken CRM tail\""));
        assertThat(response.classification()).isEqualTo("DELIVERY");
        assertThat(response.confidence()).isZero();
        assertThat(response.qualityFlags()).contains("AI_METADATA_WARNING");
        assertThat(response.visibilityLimits()).anyMatch(limit -> limit.contains("odzyskano kompletny rdzeń"));
    }

    @Test
    void shouldRejectUnsupportedClassificationRegardlessOfMetadata() {
        assertThatThrownBy(() -> parser.parse(deliveryJson().replace("\"DELIVERY\"", "\"UNKNOWN\"")))
                .hasMessageContaining("classification is unsupported");
    }

    @Test
    void shouldNotRecoverMalformedCoreAfterOtherwiseCompleteScoringFields() {
        var json = deliveryJson().replace("\"qualityFlags\":[]", "\"dimensions\":{\"broken\":not-a-number}");
        assertThatThrownBy(() -> parser.parse(json)).hasMessageContaining("JSON could not be parsed");
    }
}
