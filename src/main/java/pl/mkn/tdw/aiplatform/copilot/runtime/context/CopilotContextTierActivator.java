package pl.mkn.tdw.aiplatform.copilot.runtime.context;

import com.github.copilot.CopilotSession;
import com.github.copilot.generated.rpc.OptionsUpdateContextTier;
import com.github.copilot.generated.rpc.SessionOptionsUpdateParams;
import com.github.copilot.generated.rpc.SessionOptionsUpdateResult;
import com.github.copilot.generated.rpc.SessionRpc;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class CopilotContextTierActivator {

    public boolean activateLongContext(CopilotSession session, long timeoutMillis) {
        if (session == null) {
            throw new IllegalArgumentException("Copilot session must not be null");
        }
        return activateLongContext(session.getRpc(), timeoutMillis);
    }

    boolean activateLongContext(SessionRpc sessionRpc, long timeoutMillis) {
        if (sessionRpc == null) {
            throw new IllegalArgumentException("Copilot session RPC must not be null");
        }
        SessionOptionsUpdateResult result = sessionRpc.options.update(longContextPatch())
                .orTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .join();
        return result != null && Boolean.TRUE.equals(result.success());
    }

    private SessionOptionsUpdateParams longContextPatch() {
        return new SessionOptionsUpdateParams(
                null, // sessionId is injected by the session-scoped RPC API
                null, // model
                null, // modelCapabilitiesOverrides
                null, // reasoningEffort
                null, // reasoningSummary
                null, // verbosity
                null, // clientName
                null, // lspClientName
                null, // integrationId
                null, // featureFlags
                null, // isExperimentalMode
                null, // provider
                null, // capi
                null, // workingDirectory
                null, // availableTools
                null, // excludedTools
                null, // includedBuiltinAgents
                null, // excludedBuiltinAgents
                null, // toolFilterPrecedence
                null, // enableScriptSafety
                null, // shell
                null, // shellInitProfile
                null, // shellProcessFlags
                null, // sandboxConfig
                null, // logInteractiveShells
                null, // envValueMode
                null, // allowAllMcpServerInstructions
                null, // skillDirectories
                null, // disabledSkills
                null, // enableOnDemandInstructionDiscovery
                null, // maxInlineBinaryBytes
                null, // installedPlugins
                null, // customAgentsLocalOnly
                null, // suppressCustomAgentPrompt
                null, // skipCustomInstructions
                null, // disabledInstructionSources
                null, // coauthorEnabled
                null, // trajectoryFile
                null, // enableStreaming
                null, // copilotUrl
                null, // askUserDisabled
                null, // continueOnAutoMode
                null, // runningInInteractiveMode
                null, // enableReasoningSummaries
                null, // agentContext
                null, // eventsLogDirectory
                null, // eventsLogIncludesSubagents
                null, // additionalContentExclusionPolicies
                null, // manageScheduleEnabled
                null, // sessionCapabilities
                null, // skipEmbeddingRetrieval
                null, // organizationCustomInstructions
                null, // enableFileHooks
                null, // enableHostGitOperations
                null, // enableSessionStore
                null, // enableSkills
                OptionsUpdateContextTier.LONG_CONTEXT,
                null // sessionLimits
        );
    }
}
