package pl.mkn.tdw.features.deliverycomplexityassessment.ai;
import java.util.List;
import pl.mkn.tdw.shared.ai.AnalysisAiFinding;

public record DeliveryPartFindings(List<String> coverage, boolean sufficientEvidence,
                                   List<AnalysisAiFinding> findings, double confidence, List<String> visibilityLimits) {
    public DeliveryPartFindings {
        coverage = List.copyOf(coverage);
        findings = List.copyOf(findings);
        visibilityLimits = List.copyOf(visibilityLimits);
    }
}
