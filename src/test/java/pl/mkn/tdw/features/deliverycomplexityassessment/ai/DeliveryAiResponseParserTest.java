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
                    "implementedCompatibilityScope":1,
                    "parameterizationComplexity":3
                  },
                  "confidence":0.75,
                  "evidenceSummary":[
                    "outcomeBreadth | delivery-complexity/issues.md#DCA-1 | API behavior changed",
                    "domainDecisionComplexity | delivery-complexity/issues.md#DCA-1 | validation rule added",
                    "applicationFlowComplexity | delivery-complexity/diffs.md#service!1 | error flow added",
                    "boundaryAndDataComplexity | delivery-complexity/diffs.md#service!1 | request mapping changed",
                    "verificationStateSpace | delivery-complexity/issues.md#DCA-1 | success and error variants",
                    "implementedCompatibilityScope | delivery-complexity/diffs.md#service!1 | fallback retained",
                    "parameterizationComplexity | delivery-complexity/diffs.md#service!1 | runtime parameters added"
                  ],
                  "qualityFlags":[],
                  "visibilityLimits":[]
                }
                """);

        assertThat(response.classification()).isEqualTo("DELIVERY");
        assertThat(response.dimensions().applicationFlowComplexity()).isEqualTo(4);
        assertThat(response.dimensions().parameterizationComplexity()).isEqualTo(3);
        assertThat(response.confidence()).isEqualTo(0.75);
    }

    @Test
    void shouldParseMarkdownFencedJsonWithUnicodeLineSeparators() {
        var content = """
                ```json
                {
                  "classification":"DELIVERY",
                  "dimensions":{
                    "outcomeBreadth":2,
                    "domainDecisionComplexity":2,
                    "applicationFlowComplexity":1,
                    "boundaryAndDataComplexity":1,
                    "verificationStateSpace":2,
                    "implementedCompatibilityScope":0,
                    "parameterizationComplexity":0
                  },
                  "confidence":0.82,
                  "evidenceSummary":["outcomeBreadth | issues.md#DCA-1 | visible result"],
                  "qualityFlags":["feature flag not verified"],
                  "visibilityLimits":["runtime evidence unavailable"]
                }
                ```
                """.replace("\n", "\u2028");

        var response = parser.parse(content);

        assertThat(response.classification()).isEqualTo("DELIVERY");
        assertThat(response.dimensions().outcomeBreadth()).isEqualTo(2);
        assertThat(response.dimensions().applicationFlowComplexity()).isEqualTo(1);
        assertThat(response.confidence()).isEqualTo(0.82);
        assertThat(response.evidenceSummary()).containsExactly(
                "outcomeBreadth | issues.md#DCA-1 | visible result"
        );
        assertThat(response.qualityFlags()).containsExactly("feature flag not verified");
        assertThat(response.visibilityLimits()).containsExactly("runtime evidence unavailable");
        var score = new DeliveryAssessmentScoringService().score(response);
        assertThat(score.score100()).isEqualTo(28.75);
        assertThat(score.deliveredStoryPoints()).isEqualTo(3);
    }

    @Test
    void shouldRejectOutOfRangeDimension() {
        assertThatThrownBy(() -> parser.parse("""
                {"classification":"DELIVERY","dimensions":{
                  "outcomeBreadth":5,"domainDecisionComplexity":0,"applicationFlowComplexity":0,
                  "boundaryAndDataComplexity":0,"verificationStateSpace":0,"implementedCompatibilityScope":0,
                  "parameterizationComplexity":0
                },"confidence":0.5,"evidenceSummary":[],"qualityFlags":[],"visibilityLimits":[]}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 4");
    }

    @Test
    void shouldRejectDeliveryResponseWithoutParameterizationDimension() {
        assertThatThrownBy(() -> parser.parse("""
                {"classification":"DELIVERY","dimensions":{
                  "outcomeBreadth":1,"domainDecisionComplexity":0,"applicationFlowComplexity":0,
                  "boundaryAndDataComplexity":0,"verificationStateSpace":0,"implementedCompatibilityScope":0
                },"confidence":0.5,"evidenceSummary":[],"qualityFlags":[],"visibilityLimits":[]}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI response dimension parameterizationComplexity is missing.");
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
    void shouldAcceptNonZeroDimensionWithoutStrictEvidenceDescription() {
        var response = parser.parse("""
                {"classification":"DELIVERY","dimensions":{
                  "outcomeBreadth":1,"domainDecisionComplexity":0,"applicationFlowComplexity":0,
                  "boundaryAndDataComplexity":0,"verificationStateSpace":0,"implementedCompatibilityScope":0,
                  "parameterizationComplexity":0
                },"confidence":0.5,"evidenceSummary":["API changed"],
                  "qualityFlags":[],"visibilityLimits":[]}
                """);

        assertThat(response.dimensions().outcomeBreadth()).isEqualTo(1);
        assertThat(response.evidenceSummary()).containsExactly("API changed");
    }

    @Test
    void shouldAcceptDescriptionReferencesWithDifferentSeparators() {
        var response = parser.parse("""
                {"classification":"DELIVERY","dimensions":{
                  "outcomeBreadth":2,"domainDecisionComplexity":2,"applicationFlowComplexity":2,
                  "boundaryAndDataComplexity":0,"verificationStateSpace":2,"implementedCompatibilityScope":0,
                  "parameterizationComplexity":0
                },"confidence":0.93,"evidenceSummary":[
                  "outcomeBreadth | delivery-complexity/issues.md#CRM-37416 | visible regression",
                  "domainDecisionComplexity | delivery-complexity/issues.md#CRM-37416 | contact preference rule",
                  "applicationFlowComplexity | delivery-complexity/diffs.md#CRM_FRONTEND!10413:libs/customer-profile/guard.service.ts | waits for profile availability",
                  "verificationStateSpace | delivery-complexity/diffs.md#CRM_FRONTEND!10413:libs/customer-profile/guard.service.spec.ts | immediate and delayed profile states"
                ],"qualityFlags":[],"visibilityLimits":[]}
                """);

        assertThat(response.dimensions().verificationStateSpace()).isEqualTo(2);
        assertThat(response.confidence()).isEqualTo(0.93);
        assertThat(response.evidenceSummary()).hasSize(4);
    }

    @Test
    void shouldAllowMissingOptionalDescriptionCollections() {
        var response = parser.parse("""
                {"classification":"DELIVERY","dimensions":{
                  "outcomeBreadth":2,"domainDecisionComplexity":2,"applicationFlowComplexity":2,
                  "boundaryAndDataComplexity":2,"verificationStateSpace":3,"implementedCompatibilityScope":0,
                  "parameterizationComplexity":2
                },"confidence":0.93}
                """);

        assertThat(response.evidenceSummary()).isEmpty();
        assertThat(response.qualityFlags()).isEmpty();
        assertThat(response.visibilityLimits()).isEmpty();
    }

    @Test
    void shouldKeepReadableValuesFromMalformedOptionalDescriptionCollections() {
        var response = parser.parse("""
                {"classification":"DELIVERY","dimensions":{
                  "outcomeBreadth":1,"domainDecisionComplexity":0,"applicationFlowComplexity":0,
                  "boundaryAndDataComplexity":0,"verificationStateSpace":0,"implementedCompatibilityScope":0,
                  "parameterizationComplexity":0
                },"confidence":0.5,
                  "evidenceSummary":{"unexpected":"object"},
                  "qualityFlags":["usable flag",42,null],
                  "visibilityLimits":"single readable limit"}
                """);

        assertThat(response.evidenceSummary()).isEmpty();
        assertThat(response.qualityFlags()).containsExactly("usable flag");
        assertThat(response.visibilityLimits()).containsExactly("single readable limit");
    }

    @Test
    void shouldRecoverScoringFieldsWhenOptionalDescriptionsBreakJsonSyntax() {
        var response = parser.parse("""
                {"classification":"DELIVERY","dimensions":{
                  "outcomeBreadth":3,"domainDecisionComplexity":2,"applicationFlowComplexity":2,
                  "boundaryAndDataComplexity":3,"verificationStateSpace":3,"implementedCompatibilityScope":0,
                  "parameterizationComplexity":0
                },"confidence":0.88,
                  "evidenceSummary":[
                    "outcomeBreadth | issues.md#CRM-36597 | sekcja „REGULY SEGMENTACJI – OPIS KOMPLETNY" z pelnymi opisami"
                  ],"qualityFlags":[],"visibilityLimits":[]}
                """);

        assertThat(response.classification()).isEqualTo("DELIVERY");
        assertThat(response.dimensions().outcomeBreadth()).isEqualTo(3);
        assertThat(response.dimensions().boundaryAndDataComplexity()).isEqualTo(3);
        assertThat(response.dimensions().verificationStateSpace()).isEqualTo(3);
        assertThat(response.confidence()).isEqualTo(0.88);
        assertThat(response.evidenceSummary()).isEmpty();
        var score = new DeliveryAssessmentScoringService().score(response);
        assertThat(score.score100()).isEqualTo(46.25);
        assertThat(score.deliveredStoryPoints()).isEqualTo(5);
    }

    @Test
    void shouldRejectMalformedJsonBeforeRequiredScoringFieldsAreComplete() {
        assertThatThrownBy(() -> parser.parse("""
                {"classification":"DELIVERY","dimensions":{
                  "outcomeBreadth":3,"domainDecisionComplexity":not-a-number
                },"confidence":0.88}
                """))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI response JSON could not be parsed.");
    }
}
