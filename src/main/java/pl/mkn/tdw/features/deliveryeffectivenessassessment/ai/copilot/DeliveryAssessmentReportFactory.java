package pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.copilot;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.evidence.DeliveryEvidencePacket;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.List;

@Component
public class DeliveryAssessmentReportFactory {

    public static final String SECTION_ASSESSMENT = "ASSESSMENT";

    public AnalysisReport initialReport(String reportId, DeliveryEvidencePacket packet) {
        return new AnalysisReport(
                reportId,
                "Delivery Effectiveness Assessment",
                packet.unit().unitId(),
                "Assessment is in progress.",
                List.of(new AnalysisReportSection(
                        SECTION_ASSESSMENT,
                        "Assessment",
                        10,
                        "Assessment is in progress.",
                        AnalysisReportMeta.empty()
                )),
                new AnalysisReportMeta(
                        List.of(),
                        packet.visibilityLimits(),
                        List.of(),
                        List.of(),
                        null,
                        List.of()
                )
        );
    }
}
