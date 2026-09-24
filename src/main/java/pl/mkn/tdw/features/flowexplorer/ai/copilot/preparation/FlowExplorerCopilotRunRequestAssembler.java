package pl.mkn.tdw.features.flowexplorer.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionTarget;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.features.flowexplorer.ai.preparation.FlowExplorerPromptPreparation;
import pl.mkn.tdw.features.flowexplorer.ai.report.FlowExplorerReportFactory;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStartRequest;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;

@Component
@RequiredArgsConstructor
public class FlowExplorerCopilotRunRequestAssembler {

    private static final String FOLLOW_UP_REPORT_GUIDANCE = """
            W follow-up zmieniaj AnalysisReport tylko po jawnej prosbie operatora w najnowszej
            wiadomosci. Zwykle pytanie, prosba o wyjasnienie albo dodatkowy research nie zmienia
            raportu. Przed korekta odczytaj biezaca sekcje przez report_get_current(sectionId).
            Uzyj report_patch_section dla jednego jednoznacznego fragmentu z aktualnym digestem,
            report_upsert_section dla calej sekcji, report_update_header dla naglowka i
            report_update_meta dla globalnych metadata. Zachowaj niezmieniane tresci i potwierdz
            zapis przez report_get_current. Gdy tool odrzuci zmiane, powiedz, ze raport nie zostal
            zaktualizowany.
            """;

    private static final CopilotToolDescriptionContext TOOL_DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("flow-explorer");

    private final CopilotSdkToolFactory toolFactory;
    private final FlowExplorerCopilotToolSessionContextFactory toolSessionContextFactory;
    private final FlowExplorerCopilotToolAccessPolicyFactory toolAccessPolicyFactory;
    private final FlowExplorerCopilotSessionConfigRequestFactory sessionConfigRequestFactory;
    private final CopilotRunAuthMapper runAuthMapper;
    private final FlowExplorerReportFactory reportFactory;

    public FlowExplorerCopilotRunAssembly assemble(
            String runReference,
            FlowExplorerJobStartRequest request,
            FlowExplorerContextSnapshot contextSnapshot,
            FlowExplorerPromptPreparation preparation
    ) {
        return assemble(runReference, request, contextSnapshot, preparation, AnalysisAiAuthRef.localToken(null));
    }

    public FlowExplorerCopilotRunAssembly assemble(
            String runReference,
            FlowExplorerJobStartRequest request,
            FlowExplorerContextSnapshot contextSnapshot,
            FlowExplorerPromptPreparation preparation,
            AnalysisAiAuthRef authRef
    ) {
        return assemble(runReference, request, contextSnapshot, preparation, false, null, authRef, null);
    }

    public FlowExplorerCopilotRunAssembly assembleFollowUp(
            String runReference,
            FlowExplorerJobStartRequest request,
            FlowExplorerContextSnapshot contextSnapshot,
            FlowExplorerPromptPreparation preparation
    ) {
        return assembleFollowUp(
                runReference,
                request,
                contextSnapshot,
                preparation,
                null,
                AnalysisAiAuthRef.localToken(null)
        );
    }

    public FlowExplorerCopilotRunAssembly assembleFollowUp(
            String runReference,
            FlowExplorerJobStartRequest request,
            FlowExplorerContextSnapshot contextSnapshot,
            FlowExplorerPromptPreparation preparation,
            String copilotSessionId,
            AnalysisAiAuthRef authRef
    ) {
        return assembleFollowUp(runReference, request, contextSnapshot, preparation, copilotSessionId,
                authRef, null);
    }

    public FlowExplorerCopilotRunAssembly assembleFollowUp(
            String runReference, FlowExplorerJobStartRequest request, FlowExplorerContextSnapshot contextSnapshot,
            FlowExplorerPromptPreparation preparation, String copilotSessionId, AnalysisAiAuthRef authRef,
            AnalysisReport report
    ) {
        if (!StringUtils.hasText(copilotSessionId)) {
            throw new IllegalArgumentException("Flow Explorer follow-up requires copilotSessionId for session resume.");
        }
        return assemble(runReference, request, contextSnapshot, preparation, true, copilotSessionId, authRef,
                report);
    }

    private FlowExplorerCopilotRunAssembly assemble(
            String runReference,
            FlowExplorerJobStartRequest request,
            FlowExplorerContextSnapshot contextSnapshot,
            FlowExplorerPromptPreparation preparation,
            boolean followUp,
            String copilotSessionId,
            AnalysisAiAuthRef authRef,
            AnalysisReport followUpReport
    ) {
        var toolSessionContext = toolSessionContextFactory.create(
                runReference,
                copilotSessionId,
                request,
                contextSnapshot,
                preparation,
                followUp,
                followUpReport
        );
        var registeredTools = toolFactory.createToolDefinitions(toolSessionContext, TOOL_DESCRIPTION_CONTEXT);
        var toolAccessPolicy = followUp
                ? toolAccessPolicyFactory.createForFollowUp(registeredTools)
                : toolAccessPolicyFactory.create(registeredTools);
        var aiOptions = request != null ? request.aiOptions() : null;
        var sessionConfigRequest = followUp
                ? sessionConfigRequestFactory.createForFollowUp(
                        toolSessionContext.copilotSessionId(),
                        toolAccessPolicy,
                        aiOptions
                )
                : sessionConfigRequestFactory.create(
                        toolSessionContext.copilotSessionId(),
                        toolAccessPolicy,
                        aiOptions
                );
        if (followUp) {
            sessionConfigRequest = sessionConfigRequest.withDurableSystemInstructions(FOLLOW_UP_REPORT_GUIDANCE);
        }
        var runRequest = new CopilotRunRequest(
                toolSessionContext.analysisRunId(),
                runAuthMapper.toRunAuth(authRef),
                followUp
                        ? CopilotSessionTarget.existing(toolSessionContext.copilotSessionId())
                        : CopilotSessionTarget.newSession(),
                preparation != null ? preparation.prompt() : "",
                sessionConfigRequest,
                preparation != null ? preparation.artifactContents() : null,
                null
        ).withInitialReport(followUp
                ? followUpReport
                : reportFactory.createInitialReport(request, contextSnapshot, toolSessionContext));

        return new FlowExplorerCopilotRunAssembly(
                runRequest,
                toolSessionContext,
                toolAccessPolicy
        );
    }
}
