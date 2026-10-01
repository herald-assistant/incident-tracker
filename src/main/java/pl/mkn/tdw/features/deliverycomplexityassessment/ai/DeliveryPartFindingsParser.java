package pl.mkn.tdw.features.deliverycomplexityassessment.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.shared.ai.AnalysisAiFinding;
import java.util.*;

@Component("deliveryAssessmentPartFindingsParser")
@RequiredArgsConstructor
public class DeliveryPartFindingsParser {
    private static final Set<String> DIMENSIONS = Set.of("outcomeBreadth", "domainDecisionComplexity", "applicationFlowComplexity", "boundaryAndDataComplexity", "verificationStateSpace", "implementedCompatibilityScope", "parameterizationComplexity", "EXCLUDED");
    private final ObjectMapper objectMapper;

    public DeliveryPartFindings parse(String content, List<String> expectedCoverage, List<AnalysisAiFinding> sourceFindings) {
        try {
            com.fasterxml.jackson.databind.JsonNode root = objectMapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(content);
            if (!root.isObject() || !root.path("coverage").isArray() || !root.path("findings").isArray()
                    || !root.path("sufficientEvidence").isBoolean() || !root.path("confidence").isNumber()
                    || !root.path("visibilityLimits").isArray()) throw invalid();
            var result = objectMapper.treeToValue(root, DeliveryPartFindings.class);
            if (result.confidence() < 0 || result.confidence() > 1 || !Double.isFinite(result.confidence())
                    || new HashSet<>(result.coverage()).size() != result.coverage().size()
                    || !new HashSet<>(result.coverage()).equals(new HashSet<>(expectedCoverage))) throw invalid();
            var allowed = new HashSet<>(expectedCoverage);
            for (var finding : result.findings()) {
                if (finding.behaviorId() == null || finding.behaviorId().isBlank() || !DIMENSIONS.contains(finding.dimension())
                        || finding.fact() == null || finding.fact().isBlank() || finding.references().isEmpty()
                        || finding.references().stream().anyMatch(ref -> !allowed.contains(ref))) throw invalid();
            }
            if (!result.sufficientEvidence() && result.visibilityLimits().isEmpty()) throw invalid();
            if (result.sufficientEvidence() && result.findings().isEmpty()) throw invalid();
            if (sourceFindings != null) {
                var originalRefs = new HashSet<String>();
                sourceFindings.forEach(f -> originalRefs.addAll(f.references()));
                var reducedRefs = new HashSet<String>();
                result.findings().forEach(f -> reducedRefs.addAll(f.references()));
                if (!originalRefs.equals(reducedRefs)) throw invalid();
                var originalSignals = sourceFindings.stream().map(f -> f.behaviorId() + "|" + f.dimension()).collect(java.util.stream.Collectors.toSet());
                var reducedSignals = result.findings().stream().map(f -> f.behaviorId() + "|" + f.dimension()).collect(java.util.stream.Collectors.toSet());
                if (!reducedSignals.containsAll(originalSignals)) throw invalid();
                var bySignal = result.findings().stream().collect(java.util.stream.Collectors.groupingBy(f -> f.behaviorId() + "|" + f.dimension()));
                for (var original : sourceFindings) {
                    var matching = bySignal.get(original.behaviorId() + "|" + original.dimension());
                    var refs = matching.stream().flatMap(f -> f.references().stream()).collect(java.util.stream.Collectors.toSet());
                    var deps = matching.stream().flatMap(f -> f.dependencies().stream()).collect(java.util.stream.Collectors.toSet());
                    if (!refs.containsAll(original.references()) || !deps.containsAll(original.dependencies())) throw invalid();
                }
                var dependencies = sourceFindings.stream().flatMap(f -> f.dependencies().stream()).collect(java.util.stream.Collectors.toSet());
                if (!result.findings().stream().flatMap(f -> f.dependencies().stream()).collect(java.util.stream.Collectors.toSet()).containsAll(dependencies)) throw invalid();
                var dimensions = sourceFindings.stream().map(AnalysisAiFinding::dimension).collect(java.util.stream.Collectors.toSet());
                if (!result.findings().stream().map(AnalysisAiFinding::dimension).collect(java.util.stream.Collectors.toSet()).containsAll(dimensions)) throw invalid();
            }
            return result;
        } catch (Exception failure) {
            throw new IllegalArgumentException("Invalid partial findings or incomplete evidence coverage.", failure);
        }
    }

    private IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid partial findings or incomplete evidence coverage."); }
}
