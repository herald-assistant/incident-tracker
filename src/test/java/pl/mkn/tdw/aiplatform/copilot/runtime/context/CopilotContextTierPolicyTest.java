package pl.mkn.tdw.aiplatform.copilot.runtime.context;

import com.github.copilot.CopilotSession;
import com.github.copilot.rpc.CopilotClientOptions;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.ResumeSessionConfig;
import com.github.copilot.rpc.SessionConfig;
import com.github.copilot.rpc.SystemMessageConfig;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CopilotContextTierPolicyTest {

    @Test
    void shouldSelectLongContextForNewAndResumedCrmSessionAtAutoThreshold() {
        var properties = properties(0.70D, 1D, 0);
        var activities = new ArrayList<AnalysisAiActivityEvent>();
        var sessionConfig = new SessionConfig().setModel("gpt-crm-context").setReasoningEffort("medium");
        var resumeConfig = new ResumeSessionConfig().setModel("gpt-crm-context").setReasoningEffort("medium");
        var prepared = prepared(
                "CRM_INITIAL_CONTEXT_".repeat(4),
                sessionConfig,
                resumeConfig,
                List.of(),
                activities,
                CopilotContextTierPreference.AUTO
        );

        var contextTierSession = policy(properties, mock(CopilotEffectiveContextTierReader.class)).prepare(prepared);

        assertThat(contextTierSession.decision().estimatedInitialTokens()).isGreaterThanOrEqualTo(70);
        assertThat(contextTierSession.decision().useLongContextInitially()).isTrue();
        assertThat(contextTierSession.decision().modelMetadataAvailable()).isTrue();
        assertThat(sessionConfig.getContextTier()).isEqualTo("long_context");
        assertThat(resumeConfig.getContextTier()).isEqualTo("long_context");
        assertThat(activities).singleElement().satisfies(activity -> {
            assertThat(activity.category()).isEqualTo("CONTEXT");
            assertThat(activity.status()).isEqualTo("STARTED");
            assertThat(activity.details()).containsEntry("trigger", "PRE_SESSION");
        });
    }

    @Test
    void shouldIncludeToolDefinitionsAndReserveWithoutCountingEmbeddedArtifactsTwice() {
        var properties = properties(0.70D, 1D, 10);
        var tool = ToolDefinition.createSkipPermission(
                "crm_contact_lookup",
                "Read a synthetic CRM contact context",
                Map.of("type", "object", "properties", Map.of("crmContactId", Map.of("type", "string"))),
                invocation -> CompletableFuture.completedFuture(Map.of("status", "ok"))
        );
        var sessionConfig = new SessionConfig().setModel("gpt-crm-context").setTools(List.of(tool));
        var prepared = prepared(
                "CRM",
                sessionConfig,
                new ResumeSessionConfig(),
                List.of(tool),
                new ArrayList<>(),
                CopilotContextTierPreference.AUTO
        );

        var decision = policy(properties, mock(CopilotEffectiveContextTierReader.class)).prepare(prepared).decision();

        assertThat(decision.estimatedInitialTokens()).isGreaterThan(70);
        assertThat(decision.useLongContextInitially()).isTrue();
        assertThat(prepared.artifactContents()).containsEntry(
                "crm-source.md",
                "CRM evidence already embedded in the prompt"
        );
    }

    @Test
    void shouldIncludeDurableCrmSystemInstructionsInInitialContextEstimate() {
        var properties = properties(0.70D, 1D, 0);
        var sessionConfig = new SessionConfig()
                .setModel("gpt-crm-context")
                .setSystemMessage(new SystemMessageConfig().setContent(
                        "CRM_DURABLE_RESPONSE_CONTRACT_".repeat(3)
                ));
        var prepared = prepared(
                "CRM",
                sessionConfig,
                new ResumeSessionConfig(),
                List.of(),
                new ArrayList<>(),
                CopilotContextTierPreference.AUTO
        );

        var decision = policy(properties, mock(CopilotEffectiveContextTierReader.class)).prepare(prepared).decision();

        assertThat(decision.estimatedInitialTokens()).isGreaterThanOrEqualTo(70);
        assertThat(decision.useLongContextInitially()).isTrue();
    }

    @Test
    void shouldKeepDefaultTierForSmallAutoPromptAndUnknownModel() {
        var properties = properties(0.70D, 4D, 0);
        var knownConfig = new SessionConfig().setModel("gpt-crm-context");
        var unknownConfig = new SessionConfig().setModel("gpt-unknown-crm");

        var known = policy(properties, mock(CopilotEffectiveContextTierReader.class)).prepare(prepared(
                "Small synthetic CRM prompt",
                knownConfig,
                new ResumeSessionConfig(),
                List.of(),
                new ArrayList<>(),
                CopilotContextTierPreference.AUTO
        ));
        var unknown = policy(properties, mock(CopilotEffectiveContextTierReader.class)).prepare(prepared(
                "CRM_LONG_PROMPT_".repeat(20),
                unknownConfig,
                new ResumeSessionConfig(),
                List.of(),
                new ArrayList<>(),
                CopilotContextTierPreference.AUTO
        ));

        assertThat(known.decision().useLongContextInitially()).isFalse();
        assertThat(knownConfig.getContextTier()).isNull();
        assertThat(unknown.decision().modelMetadataAvailable()).isFalse();
        assertThat(unknownConfig.getContextTier()).isNull();
    }

    @Test
    void shouldRequireLongContextWithoutDependingOnDynamicCatalogMetadata() {
        var properties = properties(0.70D, 4D, 0);
        var activities = new ArrayList<AnalysisAiActivityEvent>();
        var sessionConfig = new SessionConfig().setModel("gpt-crm-dynamic");
        var resumeConfig = new ResumeSessionConfig().setModel("gpt-crm-dynamic");
        var prepared = prepared(
                "Small synthetic CRM prompt",
                sessionConfig,
                resumeConfig,
                List.of(),
                activities,
                CopilotContextTierPreference.LONG_CONTEXT_REQUIRED
        );

        var controller = policy(properties, mock(CopilotEffectiveContextTierReader.class)).prepare(prepared);

        assertThat(controller.decision().preference()).isEqualTo(CopilotContextTierPreference.LONG_CONTEXT_REQUIRED);
        assertThat(controller.decision().modelMetadataAvailable()).isFalse();
        assertThat(controller.decision().useLongContextInitially()).isTrue();
        assertThat(sessionConfig.getContextTier()).isEqualTo("long_context");
        assertThat(resumeConfig.getContextTier()).isEqualTo("long_context");
        assertThat(activities).extracting(AnalysisAiActivityEvent::status).containsExactly("STARTED");
    }

    @Test
    void shouldUseResumedCrmSessionModelAndConfigureResumeTierBeforeOpen() {
        var properties = properties(0.70D, 1D, 0);
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
                CopilotRunAuth.localToken(),
                CopilotContextTierPreference.AUTO
        );

        var controller = policy(properties, mock(CopilotEffectiveContextTierReader.class)).prepare(prepared);

        assertThat(controller.decision().modelId()).isEqualTo("gpt-crm-context");
        assertThat(controller.decision().useLongContextInitially()).isTrue();
        assertThat(resumeConfig.getContextTier()).isEqualTo("long_context");
    }

    @Test
    void shouldRestoreSdkDefaultsWhenPlatformPolicyIsDisabled() {
        var properties = properties(0.70D, 1D, 0);
        properties.getContextTier().setEnabled(false);
        var activities = new ArrayList<AnalysisAiActivityEvent>();
        var sessionConfig = new SessionConfig().setModel("gpt-crm-context");

        var controller = policy(properties, mock(CopilotEffectiveContextTierReader.class)).prepare(prepared(
                "CRM_LONG_CONTEXT_".repeat(20),
                sessionConfig,
                new ResumeSessionConfig(),
                List.of(),
                activities,
                CopilotContextTierPreference.LONG_CONTEXT_REQUIRED
        ));

        assertThat(controller.decision().policyEnabled()).isFalse();
        assertThat(sessionConfig.getContextTier()).isNull();
        assertThat(activities).isEmpty();
    }

    @Test
    void shouldConfirmEffectiveLongContextBeforeFirstCrmMessage() {
        var activities = new ArrayList<AnalysisAiActivityEvent>();
        var reader = mock(CopilotEffectiveContextTierReader.class);
        var session = mock(CopilotSession.class);
        when(reader.read(session)).thenReturn(new CopilotEffectiveContextTier(
                "gpt-crm-dynamic",
                "high",
                "long_context"
        ));
        var controller = policy(properties(0.70D, 4D, 0), reader).prepare(prepared(
                "Small synthetic CRM prompt",
                new SessionConfig().setModel("gpt-crm-dynamic"),
                new ResumeSessionConfig(),
                List.of(),
                activities,
                CopilotContextTierPreference.LONG_CONTEXT_REQUIRED
        ));

        controller.verifyBeforeFirstMessage(session);

        verify(reader).read(session);
        assertThat(activities).extracting(AnalysisAiActivityEvent::status)
                .containsExactly("STARTED", "COMPLETED");
        assertThat(activities.get(1).details())
                .containsEntry("trigger", "SDK_MODEL_GET_CURRENT")
                .containsEntry("effectiveTier", "long_context")
                .containsEntry("effectiveModel", "gpt-crm-dynamic");
    }

    @Test
    void shouldRejectCrmRunBeforePromptWhenSdkKeepsDefaultTier() {
        var activities = new ArrayList<AnalysisAiActivityEvent>();
        var reader = mock(CopilotEffectiveContextTierReader.class);
        var session = mock(CopilotSession.class);
        when(reader.read(session)).thenReturn(new CopilotEffectiveContextTier(
                "gpt-crm-dynamic",
                "medium",
                "default"
        ));
        var controller = policy(properties(0.70D, 4D, 0), reader).prepare(prepared(
                "Small synthetic CRM prompt",
                new SessionConfig().setModel("gpt-crm-dynamic"),
                new ResumeSessionConfig(),
                List.of(),
                activities,
                CopilotContextTierPreference.LONG_CONTEXT_REQUIRED
        ));

        assertThatThrownBy(() -> controller.verifyBeforeFirstMessage(session))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("did not activate long_context");
        assertThat(activities).extracting(AnalysisAiActivityEvent::status)
                .containsExactly("STARTED", "FAILED");
    }

    private CopilotPreparedSession prepared(
            String prompt,
            SessionConfig sessionConfig,
            ResumeSessionConfig resumeConfig,
            List<ToolDefinition> tools,
            List<AnalysisAiActivityEvent> activities,
            CopilotContextTierPreference preference
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
                CopilotRunAuth.localToken(),
                preference
        );
    }

    private CopilotSdkProperties properties(
            double initialThreshold,
            double charactersPerToken,
            int reserve
    ) {
        var properties = new CopilotSdkProperties();
        properties.getContextTier().setInitialPromptThreshold(initialThreshold);
        properties.getContextTier().setEstimatedCharactersPerToken(charactersPerToken);
        properties.getContextTier().setReservedTokens(reserve);
        return properties;
    }

    private CopilotContextTierPolicy policy(
            CopilotSdkProperties properties,
            CopilotEffectiveContextTierReader reader
    ) {
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
                ),
                reader
        );
    }
}
