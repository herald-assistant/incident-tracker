package pl.mkn.tdw.features.deliverycomplexityassessment.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

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
                """, artifacts());

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
                """, artifacts()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 4");
    }

    @Test
    void shouldAllowInsufficientEvidenceWithoutSyntheticDimensions() {
        var response = parser.parse("""
                {"classification":"INSUFFICIENT_EVIDENCE","confidence":0.2,
                 "evidenceSummary":[],"qualityFlags":[],"visibilityLimits":["No diff"]}
                """, Map.of());

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
                """, artifacts()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidence for non-zero dimension outcomeBreadth");
    }

    @Test
    void shouldAcceptConcreteCodeSymbolPresentInInlineArtifacts() {
        var response = parser.parse("""
                {"classification":"DELIVERY","dimensions":{
                  "outcomeBreadth":0,"domainDecisionComplexity":0,"applicationFlowComplexity":0,
                  "boundaryAndDataComplexity":0,"verificationStateSpace":2,"implementedCompatibilityScope":0
                },"confidence":0.84,
                  "evidenceSummary":["verificationStateSpace | SetClauseFieldValueServiceBehaviourTest | covers success, non-standard response, field-not-found, and permission-denied states"],
                  "qualityFlags":[],"visibilityLimits":[]}
                """, Map.of(
                "delivery-complexity/diffs.md",
                "class SetClauseFieldValueServiceBehaviourTest { /* visible test cases */ }"
        ));

        assertThat(response.dimensions().verificationStateSpace()).isEqualTo(2);
        assertThat(response.confidence()).isEqualTo(0.84);
    }

    @Test
    void shouldAcceptGroundedShortArtifactAliasesReturnedByAi() {
        var response = parser.parse("""
                {"classification":"DELIVERY","dimensions":{
                  "outcomeBreadth":2,"domainDecisionComplexity":2,"applicationFlowComplexity":2,
                  "boundaryAndDataComplexity":2,"verificationStateSpace":2,"implementedCompatibilityScope":2
                },"confidence":0.86,"evidenceSummary":[
                  "outcomeBreadth | issues.md#CLP-36952 | warning box added",
                  "domainDecisionComplexity | diffs.md#CLP/CLP_FRONTEND!10398 - libs/feature/questions.utils.ts | validation changed",
                  "applicationFlowComplexity | diffs.md#libs/feature/watch-form.service.ts | initial state joins the flow",
                  "boundaryAndDataComplexity | diffs.md#libs/shared/collaterals.model.ts | shared contract extended",
                  "verificationStateSpace | merge-requests.md#CLP/CLP_FRONTEND!10398 | validation variants covered",
                  "implementedCompatibilityScope | diffs.md#libs/feature/questions.utils.ts | default behavior retained"
                ],"qualityFlags":[],"visibilityLimits":[]}
                """, Map.of(
                "delivery-complexity/issues.md", "## CLP-36952\nWarning box story",
                "delivery-complexity/merge-requests.md", "## CLP/CLP_FRONTEND!10398\nChanged paths",
                "delivery-complexity/diffs.md", """
                        ## CLP/CLP_FRONTEND!10398 - libs/feature/questions.utils.ts
                        libs/feature/watch-form.service.ts
                        libs/shared/collaterals.model.ts
                        """
        ));

        assertThat(response.dimensions().implementedCompatibilityScope()).isEqualTo(2);
    }

    @Test
    void shouldRejectShortArtifactAliasWithUnknownSection() {
        assertThatThrownBy(() -> parser.parse("""
                {"classification":"DELIVERY","dimensions":{
                  "outcomeBreadth":1,"domainDecisionComplexity":0,"applicationFlowComplexity":0,
                  "boundaryAndDataComplexity":0,"verificationStateSpace":0,"implementedCompatibilityScope":0
                },"confidence":0.5,
                  "evidenceSummary":["outcomeBreadth | issues.md#CLP-DOES-NOT-EXIST | behavior changed"],
                  "qualityFlags":[],"visibilityLimits":[]}
                """, artifacts()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidence for non-zero dimension outcomeBreadth");
    }

    @Test
    void shouldRejectConcreteReferenceAbsentFromInlineArtifacts() {
        assertThatThrownBy(() -> parser.parse("""
                {"classification":"DELIVERY","dimensions":{
                  "outcomeBreadth":0,"domainDecisionComplexity":0,"applicationFlowComplexity":0,
                  "boundaryAndDataComplexity":0,"verificationStateSpace":2,"implementedCompatibilityScope":0
                },"confidence":0.84,
                  "evidenceSummary":["verificationStateSpace | InventedBehaviourTest | covers several states"],
                  "qualityFlags":[],"visibilityLimits":[]}
                """, artifacts()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("evidence for non-zero dimension verificationStateSpace");
    }

    private Map<String, String> artifacts() {
        return Map.of(
                "delivery-complexity/issues.md", "# DCA-1\nAPI behavior changed",
                "delivery-complexity/diffs.md", "# service!1\nvalidation, request mapping, error flow and fallback"
        );
    }
}
