package pl.mkn.tdw.features.changeverification.ai.copilot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotModelSelection;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionTarget;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.features.changeverification.ai.preparation.ChangeVerificationPromptPreparation;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
import pl.mkn.tdw.features.changeverification.job.report.ChangeVerificationReportFactory;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryResult;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChangeVerificationCopilotRunRequestAssembler {

    private static final CopilotToolDescriptionContext TOOL_DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("change-verification");
    private static final String DENIED_TOOL_MESSAGE =
            "Use only the inline Change Verification artifacts and the explicitly enabled Change Verification tools for this session.";

    private final CopilotSdkToolFactory toolFactory;
    private final ChangeVerificationCopilotToolSessionContextFactory toolSessionContextFactory;
    private final CopilotRunAuthMapper runAuthMapper;
    private final ChangeVerificationReportFactory reportFactory;

    public CopilotRunRequest assemble(
            String runReference,
            ChangeVerificationJobStartRequest request,
            ChangeVerificationPromptPreparation preparation,
            AnalysisAiAuthRef authRef
    ) {
        return assemble(runReference, request, null, preparation, authRef);
    }

    public CopilotRunRequest assemble(
            String runReference,
            ChangeVerificationJobStartRequest request,
            ChangeVerificationSourceDiscoveryResult sourceDiscovery,
            ChangeVerificationPromptPreparation preparation,
            AnalysisAiAuthRef authRef
    ) {
        var toolSessionContext = toolSessionContextFactory.create(
                runReference,
                request,
                sourceDiscovery,
                ChangeVerificationCopilotToolContextKeys.RUN_KIND_COMPLIANCE
        );
        var toolAccessPolicy = ChangeVerificationCopilotToolAccessPolicy.fromRegisteredTools(
                toolFactory.createToolDefinitions(toolSessionContext, TOOL_DESCRIPTION_CONTEXT),
                true
        );

        log.info(
                "Change Verification Copilot session prepared runReference={} sessionId={} gitLabToolsRegistered={} gitLabToolsEnabled={} operationalContextToolsRegistered={} operationalContextToolsEnabled={} reportToolsEnabled={}",
                runReference,
                toolSessionContext.copilotSessionId(),
                toolAccessPolicy.gitLabToolsRegistered(),
                toolAccessPolicy.gitLabToolsEnabled(),
                toolAccessPolicy.operationalContextToolsRegistered(),
                toolAccessPolicy.operationalContextToolsEnabled(),
                toolAccessPolicy.reportToolsEnabled()
        );

        var sessionConfigRequest = new CopilotSessionConfigRequest(
                toolSessionContext.copilotSessionId(),
                toolAccessPolicy.enabledTools(),
                toolAccessPolicy.availableToolNames(),
                modelSelection(request),
                DENIED_TOOL_MESSAGE
        );

        var runRequest = new CopilotRunRequest(
                toolSessionContext.analysisRunId(),
                runAuthMapper.toRunAuth(authRef),
                CopilotSessionTarget.newSession(),
                preparation.prompt(),
                sessionConfigRequest,
                preparation.artifactContents(),
                null
        );
        return runRequest.withInitialReport(reportFactory.createInitialReport(
                request,
                sourceDiscovery,
                toolSessionContext
        ));
    }

    private CopilotModelSelection modelSelection(ChangeVerificationJobStartRequest request) {
        return request != null && request.aiOptions() != null
                ? new CopilotModelSelection(request.aiOptions().model(), request.aiOptions().reasoningEffort())
                : CopilotModelSelection.DEFAULT;
    }
}
