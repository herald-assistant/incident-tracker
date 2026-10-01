package pl.mkn.tdw.features.deliveryscopecomplexity.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.shared.ai.AnalysisAiFinding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component("deliveryScopePartFindingsParser")
@RequiredArgsConstructor
public class DeliveryPartFindingsParser {
    private static final Set<String> DIMENSIONS = Set.of("novelty", "structuralAndLogic", "businessAndInvariants", "robustnessAndTests", "refactorAndArchitecture", "distribution", "EXCLUDED");
    private final ObjectMapper objectMapper;

    public DeliveryPartFindings parse(String content, List<String> expectedCoverage, List<AnalysisAiFinding> sourceFindings) {
        final JsonNode root;
        try {
            root = objectMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(content);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Partial findings must contain one valid JSON object.", failure);
        }
        if (root == null || !root.isObject()) throw invalid("response must be an object");
        if (!root.path("sufficientEvidence").isBoolean()) throw invalid("sufficientEvidence must be boolean");
        if (!root.path("findings").isArray()) throw invalid("findings must be an array");
        var allowed = new HashSet<>(expectedCoverage);
        if (allowed.size() != expectedCoverage.size()) throw invalid("prepared evidence scope contains duplicates");
        var facts = new ArrayList<AnalysisAiFinding>();
        for (var node : root.path("findings")) {
            var behavior = requiredText(node, "behaviorId");
            var dimension = requiredText(node, "dimension");
            if (!DIMENSIONS.contains(dimension)) throw invalid("unsupported dimension: " + dimension);
            var fact = requiredText(node, "fact");
            var refs = requiredTexts(node.path("references"), "references");
            if (refs.isEmpty()) throw invalid("references must not be empty for " + behavior);
            if (refs.stream().anyMatch(ref -> !allowed.contains(ref))) throw invalid("references outside current evidence scope for " + behavior);
            var dependencies = node.get("dependencies");
            facts.add(new AnalysisAiFinding(behavior, dimension, fact, refs,
                    dependencies == null || dependencies.isNull() ? List.of() : requiredTexts(dependencies, "dependencies")));
        }
        var sufficient = root.path("sufficientEvidence").booleanValue();
        if (sufficient && facts.isEmpty()) throw invalid("sufficientEvidence=true requires findings");
        if (sourceFindings != null) validateReduction(sourceFindings, facts);

        var metadata = new DeliveryResponseMetadata();
        metadata.inspectCoverage(root.get("coverage"), expectedCoverage);
        var confidence = metadata.confidence(root.get("confidence"));
        var limits = metadata.textList(root.get("visibilityLimits"), "visibilityLimits");
        if (!sufficient && limits.isEmpty()) {
            metadata.warn("sufficientEvidence=false bez opisu ograniczenia; materiał nie pozwala ustalić dostarczonego zachowania, wycena nie jest dostępna.");
        }
        // This coverage describes the input supplied by the application, not a claim by the model.
        return new DeliveryPartFindings(expectedCoverage, sufficient, facts, confidence, metadata.withWarnings(limits));
    }

    private void validateReduction(List<AnalysisAiFinding> original, List<AnalysisAiFinding> reduced) {
        var originalRefs = original.stream().flatMap(f -> f.references().stream()).collect(Collectors.toSet());
        var reducedRefs = reduced.stream().flatMap(f -> f.references().stream()).collect(Collectors.toSet());
        if (!originalRefs.equals(reducedRefs)) throw invalid("reduction must preserve the complete reference union");
        var bySignal = reduced.stream().collect(Collectors.groupingBy(f -> new Signal(f.behaviorId(), f.dimension())));
        for (var finding : original) {
            var matching = bySignal.get(new Signal(finding.behaviorId(), finding.dimension()));
            if (matching == null) throw invalid("reduction lost behaviorId/dimension: " + finding.behaviorId() + "/" + finding.dimension());
            var refs = matching.stream().flatMap(f -> f.references().stream()).collect(Collectors.toSet());
            var deps = matching.stream().flatMap(f -> f.dependencies().stream()).collect(Collectors.toSet());
            if (!refs.containsAll(finding.references())) throw invalid("reduction lost references for " + finding.behaviorId() + "/" + finding.dimension());
            if (!deps.containsAll(finding.dependencies())) throw invalid("reduction lost dependencies for " + finding.behaviorId() + "/" + finding.dimension());
        }
    }

    private String requiredText(JsonNode node, String field) {
        var value = node.path(field);
        if (!value.isTextual() || value.textValue().isBlank()) throw invalid(field + " must be non-blank text");
        return value.textValue();
    }

    private List<String> requiredTexts(JsonNode node, String field) {
        if (!node.isArray()) throw invalid(field + " must be an array of non-blank texts");
        var values = new ArrayList<String>();
        for (var item : node) {
            if (!item.isTextual() || item.textValue().isBlank()) throw invalid(field + " must contain non-blank texts only");
            values.add(item.textValue());
        }
        return List.copyOf(values);
    }

    private IllegalArgumentException invalid(String reason) { return new IllegalArgumentException("Invalid partial findings: " + reason + "."); }
    private record Signal(String behaviorId, String dimension) {}
}
