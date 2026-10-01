package pl.mkn.tdw.features.deliveryscopecomplexity.ai;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.shared.ai.AnalysisAiFinding;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class DeliveryPartFindingsParserTest {
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = JsonMapper.builder().build();
    private final DeliveryPartFindingsParser parser = new DeliveryPartFindingsParser(mapper);
    private final String ref = "CRM/customer-api!1#file:0:src/Customer.java";

    @Test
    void shouldValidateCompleteCoverageAndScopedReferences() throws Exception {
        var fact = finding("customer-status", ref, List.of("CRM/customer-api!2: customer event"));
        var result = parser.parse(json(List.of(ref), List.of(fact)), List.of(ref), null);
        assertThat(result.findings()).containsExactly(fact);
        assertThatThrownBy(() -> parser.parse(json(List.of(), List.of(fact)), List.of(ref), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse(json(List.of(ref, ref), List.of(fact)), List.of(ref), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse(json(List.of(ref), List.of(finding("customer-status", "CRM/customer-api!9#metadata", List.of()))), List.of(ref), null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectReductionDroppingDistinctBehaviorsOrDependencies() throws Exception {
        var one = finding("customer-status", ref, List.of("CRM/customer-api!2: customer event"));
        var two = finding("customer-notification", ref, List.of());
        var original = List.of(one, two);
        assertThatThrownBy(() -> parser.parse(json(List.of(ref), List.of(one)), List.of(ref), original)).isInstanceOf(IllegalArgumentException.class);
        var noDependency = finding("customer-status", ref, List.of());
        assertThatThrownBy(() -> parser.parse(json(List.of(ref), List.of(noDependency, two)), List.of(ref), original)).isInstanceOf(IllegalArgumentException.class);
        assertThat(parser.parse(json(List.of(ref), original), List.of(ref), original).findings()).containsExactly(one, two);
    }

    @Test
    void shouldRequireLimitsForInsufficientEvidenceAndRejectTrailingJson() throws Exception {
        var valid = json(List.of(ref), List.of(finding("customer-status", ref, List.of())));
        assertThatThrownBy(() -> parser.parse(valid + " {}", List.of(ref), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse(valid.replace("\"sufficientEvidence\":true", "\"sufficientEvidence\":false"), List.of(ref), null)).isInstanceOf(IllegalArgumentException.class);
    }

    private AnalysisAiFinding finding(String behavior, String reference, List<String> dependencies) {
        return new AnalysisAiFinding(behavior, "businessAndInvariants", "Customer status validation", List.of(reference), dependencies);
    }

    @Test
    void shouldRejectReductionThatMovesEvidenceToAnotherBehavior() throws Exception {
        var secondRef = "CRM/customer-api!2#metadata";
        var original = List.of(finding("customer-status", ref, List.of()), finding("customer-event", secondRef, List.of()));
        var swapped = List.of(finding("customer-status", secondRef, List.of()), finding("customer-event", ref, List.of()));
        assertThatThrownBy(() -> parser.parse(json(List.of(ref, secondRef), swapped), List.of(ref, secondRef), original)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> parser.parse(json(List.of(ref), List.of()), List.of(ref), null)).isInstanceOf(IllegalArgumentException.class);
    }
    private String json(List<String> coverage, List<AnalysisAiFinding> facts) throws Exception {
        return mapper.writeValueAsString(new DeliveryPartFindings(coverage, true, facts, .8, List.of()));
    }
}
