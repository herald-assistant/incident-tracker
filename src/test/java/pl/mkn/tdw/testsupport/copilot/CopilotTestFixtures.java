package pl.mkn.tdw.testsupport.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallbackProvider;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotSdkExecutionGateway;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentArtifactService;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentArtifactItemIdGenerator;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentDigestService;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotToolInvocationHandler;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolContextFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionCustomizer;
import pl.mkn.tdw.aiplatform.copilot.tools.evidence.CopilotToolEvidenceSessionStore;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.tools.database.DatabaseToolEvidenceCaptureListener;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.tools.database.DatabaseToolEvidenceMapper;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.tools.description.CopilotIncidentToolDescriptionCustomizer;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.tools.description.CopilotIncidentToolGuidanceCatalog;
import pl.mkn.tdw.aiplatform.copilot.tools.events.CopilotToolInvocationEventPublisher;
import pl.mkn.tdw.aiplatform.copilot.tools.events.CopilotToolInvocationFinishedEvent;
import pl.mkn.tdw.aiplatform.copilot.tools.events.CopilotToolInvocationOutcome;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.tools.gitlab.GitLabToolEvidenceCaptureListener;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.tools.gitlab.GitLabToolEvidenceMapper;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.budget.CopilotToolBudgetPolicy;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.budget.CopilotToolBudgetProperties;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.budget.CopilotToolBudgetRegistry;
import pl.mkn.tdw.aiplatform.copilot.tools.policy.session.CopilotToolSessionValidationPolicy;
import pl.mkn.tdw.common.JsonPayloadReader;

import java.util.List;

public final class CopilotTestFixtures {

    private CopilotTestFixtures() {
    }

    public static CopilotIncidentArtifactService artifactService(ObjectMapper objectMapper) {
        return new CopilotIncidentArtifactService(
                objectMapper,
                new CopilotIncidentDigestService(),
                new CopilotIncidentArtifactItemIdGenerator()
        );
    }

    public static CopilotToolEvidenceSessionStore toolEvidenceSessionStore(ObjectMapper objectMapper) {
        return new CopilotToolEvidenceSessionStore();
    }

    public static List<CopilotToolDescriptionCustomizer> toolDescriptionCustomizers() {
        return List.of(new CopilotIncidentToolDescriptionCustomizer(new CopilotIncidentToolGuidanceCatalog()));
    }

    public static CopilotSdkToolFactory toolFactory(
            List<ToolCallbackProvider> toolCallbackProviders,
            ObjectMapper objectMapper,
            CopilotToolEvidenceSessionStore toolEvidenceSessionStore
    ) {
        return toolFactory(
                toolCallbackProviders,
                objectMapper,
                toolEvidenceSessionStore,
                new CopilotToolBudgetPolicy(
                        new CopilotToolBudgetRegistry(new CopilotToolBudgetProperties())
                )
        );
    }

    public static CopilotSdkToolFactory toolFactory(
            List<ToolCallbackProvider> toolCallbackProviders,
            ObjectMapper objectMapper,
            CopilotToolEvidenceSessionStore toolEvidenceSessionStore,
            CopilotToolBudgetPolicy budgetPolicy
    ) {
        return new CopilotSdkToolFactory(
                toolCallbackProviders,
                objectMapper,
                toolDescriptionCustomizers(),
                new CopilotToolInvocationHandler(
                        objectMapper,
                        new CopilotToolContextFactory(),
                        List.of(new CopilotToolSessionValidationPolicy(), budgetPolicy),
                        toolInvocationEventPublisher(
                                objectMapper,
                                toolEvidenceSessionStore
                        )
                )
        );
    }

    public static CopilotToolInvocationEventPublisher toolInvocationEventPublisher(
            ObjectMapper objectMapper,
            CopilotToolEvidenceSessionStore toolEvidenceSessionStore
    ) {
        var payloadReader = new JsonPayloadReader(objectMapper);
        var gitLabListener = new GitLabToolEvidenceCaptureListener(
                toolEvidenceSessionStore,
                new GitLabToolEvidenceMapper(objectMapper, payloadReader)
        );
        var databaseListener = new DatabaseToolEvidenceCaptureListener(
                toolEvidenceSessionStore,
                new DatabaseToolEvidenceMapper(payloadReader)
        );
        return new CopilotToolInvocationEventPublisher(event -> {
            if (event instanceof CopilotToolInvocationFinishedEvent finishedEvent) {
                if (finishedEvent.outcome() == CopilotToolInvocationOutcome.COMPLETED) {
                    gitLabListener.onToolInvocationFinished(finishedEvent);
                    databaseListener.onToolInvocationFinished(finishedEvent);
                }
            }
        });
    }

    public static CopilotSdkExecutionGateway executionGateway(
            CopilotSdkProperties properties,
            CopilotToolEvidenceSessionStore toolEvidenceSessionStore
    ) {
        return new CopilotSdkExecutionGateway(
                properties,
                toolEvidenceSessionStore,
                new CopilotToolBudgetRegistry(new CopilotToolBudgetProperties())
        );
    }
}
