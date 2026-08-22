package pl.mkn.tdw.aiplatform.copilot.runtime.context;

import com.github.copilot.CopilotSession;
import com.github.copilot.generated.SessionUsageInfoEvent;
import com.github.copilot.rpc.CopilotClientOptions;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.ResumeSessionConfig;
import com.github.copilot.rpc.SessionConfig;
import com.github.copilot.rpc.ToolDefinition;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotPreparedSession;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionTarget;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuth;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelOption;
import pl.mkn.tdw.aiplatform.copilot.runtime.options.CopilotModelOptionsResponse;
import pl.mkn.tdw.shared.ai.AnalysisAiActivityEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CopilotContextTierPolicyTest {

    @Test
    void shouldSelectLongContextForNewAndResumedCrmSessionAtInitialThreshold() {
        var properties = properties(0.70D, 0.70D, 1D, 0);
        var activities = new ArrayList<AnalysisAiActivityEvent>();
        var sessionConfig = new SessionConfig().setModel("gpt-crm-context").setReasoningEffort("medium");
        var resumeConfig = new ResumeSessionConfig().setModel("gpt-crm-context").setReasoningEffort("medium");
        var prepared = prepared(
                "CRM_INITIAL_CONTEXT_".repeat(4),
                sessionConfig,
                resumeConfig,
                List.of(),
                activities
        );

        var contextTierSession = policy(properties).prepare(prepared);

        assertThat(contextTierSession.decision().estimatedInitialTokens()).isGreaterThanOrEqualTo(70);
        assertThat(contextTierSession.decision().useLongContextInitially()).isTrue();
        assertThat(sessionConfig.getContextTier()).isEqualTo("long_context");
        assertThat(resumeConfig.getContextTier()).isEqualTo("long_context");
        assertThat(activities).singleElement().satisfies(activity -> {
            assertThat(activity.category()).isEqualTo("CONTEXT");
            assertThat(activity.status()).isEqualTo("COMPLETED");
            assertThat(activity.details()).containsEntry("trigger", "INITIAL_PROMPT");
        });
    }

    @Test
    void shouldIncludeToolDefinitionsAndReserveWithoutCountingEmbeddedArtifactsTwice() {
        var properties = properties(0.70D, 0.70D, 1D, 10);
        var tool = ToolDefinition.createSkipPermission(
                "crm_contact_lookup",
                "Read a synthetic CRM contact context",
                Map.of("type", "object", "properties", Map.of("crmContactId", Map.of("type", "string"))),
                invocation -> CompletableFuture.completedFuture(Map.of("status", "ok"))
        );
        var sessionConfig = new SessionConfig()
                .setModel("gpt-crm-context")
                .setTools(List.of(tool));
        var prepared = prepared("CRM", sessionConfig, new ResumeSessionConfig(), List.of(tool), new ArrayList<>());

        var decision = policy(properties).prepare(prepared).decision();

        assertThat(decision.estimatedInitialTokens()).isGreaterThan(70);
        assertThat(decision.useLongContextInitially()).isTrue();
        assertThat(prepared.artifactContents()).containsEntry(
                "crm-source.md",
                "CRM evidence already embedded in the prompt"
        );
    }

    @Test
    void shouldKeepDefaultTierForSmallPromptAndUnknownModel() {
        var properties = properties(0.70D, 0.70D, 4D, 0);
        var knownConfig = new SessionConfig().setModel("gpt-crm-context");
        var unknownConfig = new SessionConfig().setModel("gpt-unknown-crm");

        var known = policy(properties).prepare(prepared(
                "Small synthetic CRM prompt", knownConfig, new ResumeSessionConfig(), List.of(), new ArrayList<>()
        ));
        var unknown = policy(properties).prepare(prepared(
                "CRM_LONG_PROMPT_".repeat(20), unknownConfig, new ResumeSessionConfig(), List.of(), new ArrayList<>()
        ));

        assertThat(known.decision().useLongContextInitially()).isFalse();
        assertThat(knownConfig.getContextTier()).isNull();
        assertThat(unknown.decision().modelSupported()).isFalse();
        assertThat(unknownConfig.getContextTier()).isNull();
    }

    @Test
    void shouldUseResumedCrmSessionModelAndConfigureResumeTierBeforeOpen() {
        var properties = properties(0.70D, 0.70D, 1D, 0);
        var sessionConfig = new SessionConfig().setModel("gpt-unknown-crm");
        var resumeConfig = new ResumeSessionConfig().setModel("gpt-crm-context");
        var prompt = "CRM_RESUMED_CONTEXT_".repeat(5);
        var prepared = new CopilotPreparedSession(
                "crm-resumed-context-run",
                CopilotSessionTarget.existing("crm-resumed-session"),
                new CopilotClientOptions(),
                sessionConfig,
                resumeConfig,
                new MessageOptions().setPrompt(prompt),
                prompt,
                Map.of(),
                null,
                evidence -> {
                },
                activity -> {
                },
                CopilotRunAuth.localToken()
        );

        var controller = policy(properties).prepare(prepared);

        assertThat(controller.decision().modelId()).isEqualTo("gpt-crm-context");
        assertThat(controller.decision().useLongContextInitially()).isTrue();
        assertThat(resumeConfig.getContextTier()).isEqualTo("long_context");
    }

    @Test
    void shouldRestoreSdkDefaultsWhenPlatformPolicyIsDisabled() {
        var properties = properties(0.70D, 0.70D, 1D, 0);
        properties.getContextTier().setEnabled(false);
        var activities = new ArrayList<AnalysisAiActivityEvent>();
        var sessionConfig = new SessionConfig().setModel("gpt-crm-context");

        var controller = policy(properties).prepare(prepared(
                "CRM_LONG_CONTEXT_".repeat(20),
                sessionConfig,
                new ResumeSessionConfig(),
                List.of(),
                activities
        ));

        assertThat(controller.decision().policyEnabled()).isFalse();
        assertThat(sessionConfig.getContextTier()).isNull();
        assertThat(activities).isEmpty();
    }

    @Test
    void shouldSwitchRunningCrmSessionOnlyOnceWhenReportedUsageReachesThreshold() {
        var properties = properties(0.95D, 0.70D, 4D, 0);
        var activities = new ArrayList<AnalysisAiActivityEvent>();
        var controller = policy(properties).prepare(prepared(
                "Small CRM prompt",
                new SessionConfig().setModel("gpt-crm-context").setReasoningEffort("high"),
                new ResumeSessionConfig(),
                List.of(),
                activities
        ));
        var session = mock(CopilotSession.class);
        when(session.setModel("gpt-crm-context", "high", "long_context", null))
                .thenReturn(CompletableFuture.completedFuture(null));

        controller.onSessionUsage(session, usage(100, 69));
        controller.onSessionUsage(session, usage(100, 70));
        controller.onSessionUsage(session, usage(100, 90));
        controller.runtimeSwitch().join();

        verify(session, times(1)).setModel("gpt-crm-context", "high", "long_context", null);
        assertThat(activities).extracting(AnalysisAiActivityEvent::status)
                .containsExactly("INFO", "STARTED", "COMPLETED");
        assertThat(activities.get(2).details()).containsEntry("trigger", "RUNTIME_USAGE");
    }

    @Test
    void shouldContinueAndExposeFailureWhenRuntimeSwitchFails() {
        var properties = properties(0.95D, 0.70D, 4D, 0);
        var activities = new ArrayList<AnalysisAiActivityEvent>();
        var controller = policy(properties).prepare(prepared(
                "Small CRM prompt",
                new SessionConfig().setModel("gpt-crm-context"),
                new ResumeSessionConfig(),
                List.of(),
                activities
        ));
        var session = mock(CopilotSession.class);
        when(session.setModel("gpt-crm-context", null, "long_context", null))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("synthetic CRM switch failure")));

        controller.onSessionUsage(session, usage(100, 75));
        controller.runtimeSwitch().join();

        assertThat(activities).extracting(AnalysisAiActivityEvent::status)
                .containsExactly("INFO", "STARTED", "FAILED");
        assertThat(activities.get(2).summary()).contains("kontynuowana");
    }

    @Test
    void shouldNotSwitchWhenSdkAlreadyReportsAWindowLargerThanTheDefaultProfile() {
        var properties = properties(0.95D, 0.70D, 4D, 0);
        var controller = policy(properties).prepare(prepared(
                "Small CRM prompt",
                new SessionConfig().setModel("gpt-crm-context"),
                new ResumeSessionConfig(),
                List.of(),
                new ArrayList<>()
        ));
        var session = mock(CopilotSession.class);

        controller.onSessionUsage(session, usage(1_000, 900));

        verify(session, times(0)).setModel("gpt-crm-context", null, "long_context", null);
    }

    private CopilotPreparedSession prepared(
            String prompt,
            SessionConfig sessionConfig,
            ResumeSessionConfig resumeConfig,
            List<ToolDefinition> tools,
            List<AnalysisAiActivityEvent> activities
    ) {
        sessionConfig.setTools(tools);
        resumeConfig.setTools(tools);
        return new CopilotPreparedSession(
                "crm-context-policy-run",
                CopilotSessionTarget.newSession(),
                new CopilotClientOptions(),
                sessionConfig,
                resumeConfig,
                new MessageOptions().setPrompt(prompt),
                prompt,
                Map.of("crm-source.md", "CRM evidence already embedded in the prompt"),
                null,
                evidence -> {
                },
                activities::add,
                CopilotRunAuth.localToken()
        );
    }

    private CopilotSdkProperties properties(
            double initialThreshold,
            double runtimeThreshold,
            double charactersPerToken,
            int reserve
    ) {
        var properties = new CopilotSdkProperties();
        properties.getContextTier().setInitialPromptThreshold(initialThreshold);
        properties.getContextTier().setRuntimeUsageThreshold(runtimeThreshold);
        properties.getContextTier().setEstimatedCharactersPerToken(charactersPerToken);
        properties.getContextTier().setReservedTokens(reserve);
        return properties;
    }

    private CopilotContextTierPolicy policy(CopilotSdkProperties properties) {
        return new CopilotContextTierPolicy(
                properties,
                auth -> new CopilotModelOptionsResponse(
                        "gpt-crm-context",
                        "medium",
                        List.of("low", "medium", "high"),
                        List.of(new CopilotModelOption(
                                "gpt-crm-context",
                                "Synthetic CRM Context Model",
                                true,
                                List.of("low", "medium", "high"),
                                "medium",
                                100,
                                1_000
                        ))
                )
        );
    }

    private SessionUsageInfoEvent usage(long tokenLimit, long currentTokens) {
        var event = new SessionUsageInfoEvent();
        event.setData(new SessionUsageInfoEvent.SessionUsageInfoEventData(
                tokenLimit,
                currentTokens,
                3L,
                null,
                null,
                null,
                null
        ));
        return event;
    }
}
