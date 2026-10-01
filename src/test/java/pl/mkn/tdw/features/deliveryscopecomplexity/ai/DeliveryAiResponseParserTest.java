package pl.mkn.tdw.features.deliveryscopecomplexity.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

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
        assertThat(response.qualityFlags()).isEmpty();
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
}
