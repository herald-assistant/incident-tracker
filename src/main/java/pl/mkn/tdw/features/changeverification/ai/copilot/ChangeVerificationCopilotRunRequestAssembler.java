package pl.mkn.tdw.features.changeverification.ai.copilot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotModelSelection;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotNamedSkillDirectoryResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionTarget;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.features.changeverification.ai.preparation.ChangeVerificationPromptPreparation;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
import pl.mkn.tdw.features.changeverification.source.ChangeVerificationSourceDiscoveryResult;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

import java.util.List;

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
    private final CopilotNamedSkillDirectoryResolver skillDirectoryResolver;
    private final CopilotRunAuthMapper runAuthMapper;

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
        return assemble(
                runReference,
                request,
                sourceDiscovery,
                preparation,
                authRef,
                ChangeVerificationCopilotRuntimeSkillNames.initialSkillNames(),
                ChangeVerificationCopilotToolContextKeys.RUN_KIND_COMPLIANCE
        );
    }

    public CopilotRunRequest assemble(
            String runReference,
            ChangeVerificationJobStartRequest request,
            ChangeVerificationPromptPreparation preparation,
            AnalysisAiAuthRef authRef,
            List<String> skillNames
    ) {
        return assemble(
                runReference,
                request,
                null,
                preparation,
                authRef,
                skillNames,
                ChangeVerificationCopilotToolContextKeys.RUN_KIND_COMPLIANCE
        );
    }

    public CopilotRunRequest assemble(
            String runReference,
            ChangeVerificationJobStartRequest request,
            ChangeVerificationSourceDiscoveryResult sourceDiscovery,
            ChangeVerificationPromptPreparation preparation,
            AnalysisAiAuthRef authRef,
            List<String> skillNames,
            String runKind
    ) {
        var skillDirectories = skillDirectoryResolver.resolveSkillDirectories(skillNames);
        if (skillDirectories.isEmpty()) {
            throw new IllegalStateException("Change Verification Copilot runtime skills were not resolved.");
        }
        var toolSessionContext = toolSessionContextFactory.create(runReference, request, sourceDiscovery, runKind);
        var toolAccessPolicy = ChangeVerificationCopilotToolAccessPolicy.fromRegisteredTools(
                toolFactory.createToolDefinitions(toolSessionContext, TOOL_DESCRIPTION_CONTEXT)
        );

        log.info(
                "Change Verification Copilot session prepared runReference={} sessionId={} skillCount={} skills={} skillDirectories={} gitLabToolsRegistered={} gitLabToolsEnabled={} databaseToolsRegistered={} databaseToolsEnabled={}",
                runReference,
                toolSessionContext.copilotSessionId(),
                skillNames.size(),
                skillNames,
                skillDirectories,
                toolAccessPolicy.gitLabToolsRegistered(),
                toolAccessPolicy.gitLabToolsEnabled(),
                toolAccessPolicy.databaseToolsRegistered(),
                toolAccessPolicy.databaseToolsEnabled()
        );

        var sessionConfigRequest = new CopilotSessionConfigRequest(
                toolSessionContext.copilotSessionId(),
                toolAccessPolicy.enabledTools(),
                toolAccessPolicy.availableToolNames(),
                skillDirectories,
                modelSelection(request),
                DENIED_TOOL_MESSAGE
        );

        return new CopilotRunRequest(
                toolSessionContext.analysisRunId(),
                runAuthMapper.toRunAuth(authRef),
                CopilotSessionTarget.newSession(),
                preparation.prompt(),
                sessionConfigRequest,
                preparation.artifactContents(),
                null
        );
    }

    private CopilotModelSelection modelSelection(ChangeVerificationJobStartRequest request) {
        return request != null && request.aiOptions() != null
                ? new CopilotModelSelection(request.aiOptions().model(), request.aiOptions().reasoningEffort())
                : CopilotModelSelection.DEFAULT;
    }
}
