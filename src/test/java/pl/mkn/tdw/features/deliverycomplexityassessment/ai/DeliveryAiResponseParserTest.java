package pl.mkn.tdw.features.deliverycomplexityassessment.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryAiResponseParserTest {

    private final DeliveryAiResponseParser parser = new DeliveryAiResponseParser(new ObjectMapper());

    @Test
    void shouldParseDeliveryJsonEmbeddedAfterReportToolWork() {
        var response = parser.parse("""
                Assessment complete.
                {
                  "classification":"DELIVERY",
                  "dimensions":{
                    "outcomeBreadth":2,
                    "domainDecisionComplexity":3,
                    "applicationFlowComplexity":4,
                    "boundaryAndDataComplexity":1,
                    "verificationStateSpace":2,
                    "implementedCompatibilityScope":1
                  },
                  "confidence":0.75,
                  "evidenceSummary":[
                    "outcomeBreadth | delivery-complexity/issues.md#DCA-1 | API behavior changed",
                    "domainDecisionComplexity | delivery-complexity/issues.md#DCA-1 | validation rule added",
                    "applicationFlowComplexity | delivery-complexity/diffs.md#service!1 | error flow added",
                    "boundaryAndDataComplexity | delivery-complexity/diffs.md#service!1 | request mapping changed",
                    "verificationStateSpace | delivery-complexity/issues.md#DCA-1 | success and error variants",
                    "implementedCompatibilityScope | delivery-complexity/diffs.md#service!1 | fallback retained"
                  ],
                  "qualityFlags":[],
                  "visibilityLimits":[]
                }
                """);

        assertThat(response.classification()).isEqualTo("DELIVERY");
        assertThat(response.dimensions().applicationFlowComplexity()).isEqualTo(4);
        assertThat(response.confidence()).isEqualTo(0.75);
    }

    @Test
    void shouldRejectOutOfRangeDimension() {
        assertThatThrownBy(() -> parser.parse("""
                {"classification":"DELIVERY","dimensions":{
                  "outcomeBreadth":5,"domainDecisionComplexity":0,"applicationFlowComplexity":0,
                  "boundaryAndDataComplexity":0,"verificationStateSpace":0,"implementedCompatibilityScope":0
                },"confidence":0.5,"evidenceSummary":[],"qualityFlags":[],"visibilityLimits":[]}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 4");
    }

    @Test
    void shouldAllowInsufficientEvidenceWithoutSyntheticDimensions() {
        var response = parser.parse("""
                {"classification":"INSUFFICIENT_EVIDENCE","confidence":0.2,
                 "evidenceSummary":[],"qualityFlags":[],"visibilityLimits":["No diff"]}
                """);

        assertThat(response.dimensions()).isNull();
        assertThat(response.visibilityLimits()).containsExactly("No diff");
    }

    @Test
    void shouldRejectNonZeroDimensionWithoutArtifactEvidence() {
        assertThatThrownBy(() -> parser.parse("""
                {"classification":"DELIVERY","dimensions":{
                  "outcomeBreadth":1,"domainDecisionComplexity":0,"applicationFlowComplexity":0,
                  "boundaryAndDataComplexity":0,"verificationStateSpace":0,"implementedCompatibilityScope":0
                },"confidence":0.5,"evidenceSummary":["API changed"],
                  "qualityFlags":[],"visibilityLimits":[]}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidence for non-zero dimension outcomeBreadth");
    }
}
