package pl.mkn.tdw.features.deliverycomplexityassessment.ai;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.mkn.tdw.shared.ai.AnalysisAiFinding;

import java.util.List;
import static org.assertj.core.api.Assertions.*;

class DeliveryPartFindingsParserTest {
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = JsonMapper.builder().build();
    private final DeliveryPartFindingsParser parser = new DeliveryPartFindingsParser(mapper);
    private final String ref = "CRM/customer-api!1#file:0:src/Customer.java";

    @Test
    void shouldUseApplicationScopeWithoutRequiringModelCoverage() throws Exception {
        var fact = finding("customer-status", ref, List.of());
        var tree = mapper.readTree(json(List.of(ref), List.of(fact)));
        ((com.fasterxml.jackson.databind.node.ObjectNode) tree).remove("coverage");
        var result = parser.parse(tree.toString(), List.of(ref), null);
        assertThat(result.coverage()).containsExactly(ref);
        assertThat(result.findings()).containsExactly(fact);
        assertThat(result.visibilityLimits()).isEmpty();
    }

    @Test
    void shouldReportMissingExtraDuplicateAndInvalidDeclarationsWithoutLosingFacts() throws Exception {
        var fact = finding("customer-status", ref, List.of());
        var missing = "CRM/customer-api!2#metadata";
        var extra = "CRM/customer-api!3#metadata";
        var tree = (com.fasterxml.jackson.databind.node.ObjectNode) mapper.readTree(json(List.of(ref), List.of(fact)));
        tree.putArray("coverage").add(ref).add(ref).add(extra).add(42).addNull();
        var raw = tree.toString();
        var result = parser.parse(raw, List.of(ref, missing), null);
        assertThat(result.coverage()).containsExactly(ref, missing);
        assertThat(result.findings()).containsExactly(fact);
        assertThat(result.visibilityLimits()).singleElement().asString()
                .contains("brakujące=1", "nadmiarowe=1", "powtórzone=1", "niepoprawne=2", "rawResponse");
        assertThat(raw).isEqualTo(tree.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "\"all CRM files\"", "42"})
    void shouldWarnAboutWrongCoverageType(String coverage) {
        var result = parser.parse(minimalJson().replace("\"sufficientEvidence\"", "\"coverage\":" + coverage + ",\"sufficientEvidence\""), List.of(ref), null);
        assertThat(result.coverage()).containsExactly(ref);
        assertThat(result.visibilityLimits()).anyMatch(limit -> limit.contains("coverage: niepoprawny typ"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"high\"", "true", "{}", "-0.1", "1.1", "1e999"})
    void shouldKeepFactsWithInvalidConfidenceAndConservativeFallback(String confidence) {
        var result = parser.parse(minimalJson().replace("\"confidence\":0.8", "\"confidence\":" + confidence), List.of(ref), null);
        assertThat(result.confidence()).isZero();
        assertThat(result.findings()).hasSize(1);
        assertThat(result.visibilityLimits()).anyMatch(limit -> limit.contains("confidence:") && limit.contains("przyjęto 0"));
    }

    @Test
    void shouldRetainReadableLimitsAndWarnAboutMalformedMetadata() {
        var result = parser.parse(minimalJson().replace("\"visibilityLimits\":[]", "\"visibilityLimits\":[\"CRM runtime unavailable\",42,null]"), List.of(ref), null);
        assertThat(result.visibilityLimits()).contains("CRM runtime unavailable")
                .anyMatch(limit -> limit.contains("visibilityLimits:") && limit.contains("niepoprawne elementy=2"));
        var missing = parser.parse(minimalJson().replace("\"confidence\":0.8,", ""), List.of(ref), null);
        assertThat(missing.confidence()).isZero();
        assertThat(missing.visibilityLimits()).anyMatch(limit -> limit.contains("confidence:"));
    }

    @Test
    void shouldKeepInsufficientEvidenceWithoutSyntheticAssessment() throws Exception {
        var raw = json(List.of(ref), List.of()).replace("\"sufficientEvidence\":true", "\"sufficientEvidence\":false");
        var result = parser.parse(raw, List.of(ref), null);
        assertThat(result.sufficientEvidence()).isFalse();
        assertThat(result.findings()).isEmpty();
        assertThat(result.visibilityLimits()).anyMatch(limit -> limit.contains("wycena nie jest dostępna"));
    }

    @Test
    void shouldRejectOutOfScopeReferencesEvenWhenCoverageClaimsThem() throws Exception {
        var extra = "CRM/customer-api!9#metadata";
        assertThatThrownBy(() -> parser.parse(json(List.of(ref, extra), List.of(finding("customer-status", extra, List.of()))), List.of(ref), null))
                .hasMessageContaining("references outside current evidence scope");
    }

    @Test
    void shouldRejectReductionDroppingDistinctBehaviorsOrDependencies() throws Exception {
        var one = finding("customer-status", ref, List.of("CRM/customer-api!2: customer event"));
        var two = finding("customer-notification", ref, List.of());
        var original = List.of(one, two);
        assertThatThrownBy(() -> parser.parse(json(List.of(ref), List.of(one)), List.of(ref), original)).hasMessageContaining("lost behaviorId/dimension");
        assertThatThrownBy(() -> parser.parse(json(List.of(ref), List.of(finding("customer-status", ref, List.of()), two)), List.of(ref), original)).hasMessageContaining("lost dependencies");
        assertThat(parser.parse(json(List.of(), original), List.of(ref), original).findings()).containsExactly(one, two);
    }

    @Test
    void shouldRejectReductionThatMovesEvidenceToAnotherBehavior() throws Exception {
        var secondRef = "CRM/customer-api!2#metadata";
        var original = List.of(finding("customer-status", ref, List.of()), finding("customer-event", secondRef, List.of()));
        var swapped = List.of(finding("customer-status", secondRef, List.of()), finding("customer-event", ref, List.of()));
        assertThatThrownBy(() -> parser.parse(json(List.of(ref, secondRef), swapped), List.of(ref, secondRef), original)).hasMessageContaining("lost references");
    }

    @Test
    void shouldRejectTrailingJsonAndInvalidCoreFields() {
        assertThatThrownBy(() -> parser.parse(minimalJson() + " {}", List.of(ref), null)).hasMessageContaining("one valid JSON");
        assertThatThrownBy(() -> parser.parse(minimalJson().replace("\"sufficientEvidence\":true", "\"sufficientEvidence\":\"true\""), List.of(ref), null)).hasMessageContaining("sufficientEvidence must be boolean");
        assertThatThrownBy(() -> parser.parse(minimalJson().replace("\"outcomeBreadth\"", "\"unknown\""), List.of(ref), null)).hasMessageContaining("unsupported dimension");
        assertThatThrownBy(() -> parser.parse(minimalJson().replace("\"Customer status validation\"", "42"), List.of(ref), null)).hasMessageContaining("fact must be non-blank text");
        assertThatThrownBy(() -> parser.parse(minimalJson().replace("[\"" + ref + "\"]", "[42]"), List.of(ref), null)).hasMessageContaining("references must contain non-blank texts");
    }

    @Test
    void shouldRejectSufficientEvidenceWithoutFindings() throws Exception {
        assertThatThrownBy(() -> parser.parse(json(List.of(ref), List.of()), List.of(ref), null)).hasMessageContaining("sufficientEvidence=true requires findings");
    }

    private AnalysisAiFinding finding(String behavior, String reference, List<String> dependencies) {
        return new AnalysisAiFinding(behavior, "outcomeBreadth", "Customer status validation", List.of(reference), dependencies);
    }

    private String minimalJson() {
        return "{\"sufficientEvidence\":true,\"findings\":[{\"behaviorId\":\"customer-status\",\"dimension\":\"outcomeBreadth\","
                + "\"fact\":\"Customer status validation\",\"references\":[\"" + ref + "\"],\"dependencies\":[]}],\"confidence\":0.8,\"visibilityLimits\":[]}";
    }

    private String json(List<String> coverage, List<AnalysisAiFinding> facts) throws Exception {
        return mapper.writeValueAsString(new DeliveryPartFindings(coverage, true, facts, .8, List.of()));
    }
}
