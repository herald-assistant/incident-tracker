package pl.mkn.tdw.features.changeverification.ai.copilot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotModelSelection;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotNamedSkillDirectoryResolver;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigRequest;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionTarget;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuthMapper;
import pl.mkn.tdw.features.changeverification.ai.preparation.ChangeVerificationPromptPreparation;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationJobStartRequest;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChangeVerificationCopilotRunRequestAssembler {

    private static final String DENIED_TOOL_MESSAGE =
            "Use only the inline Change Verification artifacts and runtime skill for this session.";
    private static final String SESSION_PREFIX = "change-verification-";

    private final CopilotNamedSkillDirectoryResolver skillDirectoryResolver;
    private final CopilotRunAuthMapper runAuthMapper;

    public CopilotRunRequest assemble(
            String runReference,
            ChangeVerificationJobStartRequest request,
            ChangeVerificationPromptPreparation preparation,
            AnalysisAiAuthRef authRef
    ) {
        return assemble(
                runReference,
                request,
                preparation,
                authRef,
                ChangeVerificationCopilotRuntimeSkillNames.initialSkillNames()
        );
    }

    public CopilotRunRequest assemble(
            String runReference,
            ChangeVerificationJobStartRequest request,
            ChangeVerificationPromptPreparation preparation,
            AnalysisAiAuthRef authRef,
            List<String> skillNames
    ) {
        var skillDirectories = skillDirectoryResolver.resolveSkillDirectories(skillNames);
        if (skillDirectories.isEmpty()) {
            throw new IllegalStateException("Change Verification Copilot runtime skills were not resolved.");
        }

        log.info(
                "Change Verification Copilot session skills resolved runReference={} skillCount={} skills={} skillDirectories={}",
                runReference,
                skillNames.size(),
                skillNames,
                skillDirectories
        );

        var sessionConfigRequest = new CopilotSessionConfigRequest(
                SESSION_PREFIX + runReference,
                List.of(),
                List.of(),
                skillDirectories,
                modelSelection(request),
                DENIED_TOOL_MESSAGE
        );

        return new CopilotRunRequest(
                runReference,
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
