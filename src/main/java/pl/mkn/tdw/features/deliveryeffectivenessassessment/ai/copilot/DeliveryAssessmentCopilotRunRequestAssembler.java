package pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.copilot;

import com.github.copilot.rpc.ToolDefinition;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.agenttools.context.AgentToolContextKeys;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotModelSelection;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionTarget;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.report.CopilotReportToolNames;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.ai.DeliveryPromptPreparation;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.evidence.DeliveryEvidencePacket;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiOptions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DeliveryAssessmentCopilotRunRequestAssembler {

    private static final CopilotToolDescriptionContext TOOL_DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("delivery-effectiveness-assessment");
    private static final String DENIED_TOOL_MESSAGE =
            "Use only inline Delivery Effectiveness Assessment artifacts and report tools.";

    private final CopilotSdkToolFactory toolFactory;
    private final CopilotRunAuthMapper runAuthMapper;
    private final DeliveryAssessmentReportFactory reportFactory;

    public CopilotRunRequest assemble(
            String runReference,
            AnalysisAiOptions options,
            AnalysisAiAuthRef authRef,
            DeliveryEvidencePacket packet,
            DeliveryPromptPreparation preparation
    ) {
        var reportId = "report-" + UUID.randomUUID();
        var hidden = new LinkedHashMap<String, Object>();
        hidden.put("feature", "delivery-effectiveness-assessment");
        hidden.put("deliveryUnitId", packet.unit().unitId());
        hidden.put(AgentToolContextKeys.REPORT_ID, reportId);
        hidden.put(AgentToolContextKeys.REPORT_FEATURE, "delivery-effectiveness-assessment");
        hidden.put(AgentToolContextKeys.ALLOWED_REPORT_SECTION_IDS,
                List.of(DeliveryAssessmentReportFactory.SECTION_ASSESSMENT));
        var context = new CopilotToolSessionContext(
                runReference,
                "delivery-effectiveness-" + UUID.randomUUID(),
                hidden
        );
        var reportTools = toolFactory.createToolDefinitions(context, TOOL_DESCRIPTION_CONTEXT).stream()
                .filter(tool -> CopilotReportToolNames.GET_CURRENT.equals(tool.name())
                        || CopilotReportToolNames.UPSERT_SECTION.equals(tool.name()))
                .toList();
        var sessionConfig = new CopilotSessionConfigRequest(
                context.copilotSessionId(),
                reportTools,
                reportTools.stream().map(ToolDefinition::name).toList(),
                modelSelection(options),
                DENIED_TOOL_MESSAGE
        );
        return new CopilotRunRequest(
                runReference,
                runAuthMapper.toRunAuth(authRef),
                CopilotSessionTarget.newSession(),
                preparation.prompt(),
                sessionConfig,
                preparation.artifacts(),
                null
        ).withInitialReport(reportFactory.initialReport(reportId, packet));
    }

    private CopilotModelSelection modelSelection(AnalysisAiOptions options) {
        return options != null
                ? new CopilotModelSelection(options.model(), options.reasoningEffort())
                : CopilotModelSelection.DEFAULT;
    }
}
