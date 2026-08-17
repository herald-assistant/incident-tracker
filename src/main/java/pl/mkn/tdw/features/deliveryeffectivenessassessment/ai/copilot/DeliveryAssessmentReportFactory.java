package pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.copilot;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.DeliveryAiResponse;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.evidence.DeliveryEvidencePacket;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Component
public class DeliveryAssessmentReportFactory {

    public static final String SECTION_ASSESSMENT = "ASSESSMENT";

    public AnalysisReport assessmentReport(DeliveryEvidencePacket packet, DeliveryAiResponse response) {
        var visibilityLimits = new LinkedHashSet<>(packet.visibilityLimits());
        visibilityLimits.addAll(response.visibilityLimits());
        var confidence = Math.round(response.confidence() * 100) + "%";
        return new AnalysisReport(
                "report-" + UUID.randomUUID(),
                "Delivery Effectiveness Assessment",
                packet.unit().unitId(),
                "AI classification: " + response.classification() + ", confidence " + confidence + ".",
                List.of(new AnalysisReportSection(
                        SECTION_ASSESSMENT,
                        "Assessment",
                        10,
                        markdown(response),
                        AnalysisReportMeta.empty()
                )),
                new AnalysisReportMeta(
                        List.of(),
                        List.copyOf(visibilityLimits),
                        List.of(),
                        "INSUFFICIENT_EVIDENCE".equals(response.classification())
                                ? List.of("AI could not establish a grounded assessment.")
                                : List.of(),
                        confidence,
                        response.qualityFlags()
                )
        );
    }

    private String markdown(DeliveryAiResponse response) {
        var result = new StringBuilder()
                .append("Classification: **").append(response.classification()).append("**\n\n")
                .append("Confidence: **").append(Math.round(response.confidence() * 100)).append("%**\n");
        if (response.dimensions() != null) {
            result.append("\n| Dimension | Value |\n| --- | ---: |\n")
                    .append("| Outcome breadth | ").append(response.dimensions().outcomeBreadth()).append(" / 4 |\n")
                    .append("| Domain decision complexity | ").append(response.dimensions().domainDecisionComplexity()).append(" / 4 |\n")
                    .append("| Application flow complexity | ").append(response.dimensions().applicationFlowComplexity()).append(" / 4 |\n")
                    .append("| Boundary and data complexity | ").append(response.dimensions().boundaryAndDataComplexity()).append(" / 4 |\n")
                    .append("| Verification state space | ").append(response.dimensions().verificationStateSpace()).append(" / 4 |\n")
                    .append("| Implemented compatibility scope | ").append(response.dimensions().implementedCompatibilityScope()).append(" / 4 |\n");
        }
        appendList(result, "Evidence", response.evidenceSummary());
        appendList(result, "Quality flags", response.qualityFlags());
        appendList(result, "Visibility limits", response.visibilityLimits());
        return result.toString().trim();
    }

    private void appendList(StringBuilder target, String heading, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        target.append("\n\n### ").append(heading).append('\n');
        values.forEach(value -> target.append("\n- ").append(value));
    }
}
