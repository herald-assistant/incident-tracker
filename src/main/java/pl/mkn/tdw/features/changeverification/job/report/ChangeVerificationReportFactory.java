package pl.mkn.tdw.features.changeverification.job.report;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryResult;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.util.ArrayList;
import java.util.List;

@Component
public class ChangeVerificationReportFactory {

    public AnalysisReport createInitialReport(
            ChangeVerificationJobStartRequest request,
            ChangeVerificationSourceDiscoveryResult sourceDiscovery,
            CopilotToolSessionContext toolSessionContext
    ) {
        var sections = new ArrayList<AnalysisReportSection>();
        if (request != null && request.checkStoryCompliance()) {
            sections.add(section(
                    ChangeVerificationReportSectionIds.STORY_COMPLIANCE,
                    "Story compliance",
                    0
            ));
        }
        if (request != null && request.checkInstructionCompliance()) {
            sections.add(section(
                    ChangeVerificationReportSectionIds.INSTRUCTION_COMPLIANCE,
                    "Instruction compliance",
                    sections.size()
            ));
        }

        return new AnalysisReport(
                reportId(toolSessionContext),
                "Change Verification: " + issueLabel(request, sourceDiscovery),
                "AI verification in progress",
                "",
                sections,
                AnalysisReportMeta.empty()
        );
    }

    private AnalysisReportSection section(String id, String title, int order) {
        return new AnalysisReportSection(id, title, order, "", AnalysisReportMeta.empty());
    }

    private String reportId(CopilotToolSessionContext toolSessionContext) {
        var value = toolSessionContext != null
                ? toolSessionContext.hiddenContext().get(AgentToolContextKeys.REPORT_ID)
                : null;
        if (value instanceof String reportId && StringUtils.hasText(reportId)) {
            return reportId.trim();
        }
        throw new IllegalStateException("Change Verification initial report requires hidden reportId.");
    }

    private String issueLabel(
            ChangeVerificationJobStartRequest request,
            ChangeVerificationSourceDiscoveryResult sourceDiscovery
    ) {
        if (sourceDiscovery != null && StringUtils.hasText(sourceDiscovery.issueKey())) {
            return sourceDiscovery.issueKey().trim();
        }
        if (request != null && StringUtils.hasText(request.issueKey())) {
            return request.issueKey().trim();
        }
        return "change";
    }
}
