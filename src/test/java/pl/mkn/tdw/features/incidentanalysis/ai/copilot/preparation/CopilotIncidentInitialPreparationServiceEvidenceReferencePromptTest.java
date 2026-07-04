package pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.rpc.ToolDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mkn.tdw.features.incidentanalysis.ai.initial.InitialAnalysisRequest;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.report.CopilotIncidentReportFactory;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentInitialRunAssembler;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentHiddenToolContextFactory;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentRunRequestFactory;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentSessionConfigRequestFactory;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentToolSessionContextFactory;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotArtifactContentMapper;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotPreparedSessionFactory;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentPromptRenderer;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentInitialPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRunPreparationService;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionConfigFactory;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSkillRuntimeLoader;
import pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentToolAccessPolicyFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.mkn.tdw.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentToolAccessPolicyFactoryTestSupport.policyFactoryWithConfiguredElastic;
import static pl.mkn.tdw.testsupport.copilot.CopilotTestFixtures.artifactService;

class CopilotIncidentInitialPreparationServiceEvidenceReferencePromptTest {

    private static final CopilotToolDescriptionContext INCIDENT_DESCRIPTION_CONTEXT =
            CopilotToolDescriptionContext.profile("incident-analysis");

    @TempDir
    Path tempDirectory;

    @Test
    void shouldPromptForDigestVerificationAndSplitResultContract() {
        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory("C:\\workspace");
        properties.setSkillRuntimeDirectory(tempDirectory.resolve("skills").toString());
        var factory = mock(CopilotSdkToolFactory.class);
        when(factory.createToolDefinitions(
                any(CopilotToolSessionContext.class),
                eq(INCIDENT_DESCRIPTION_CONTEXT)
        )).thenReturn(List.<ToolDefinition>of());
        var service = new CopilotIncidentInitialPreparationService(
                new CopilotIncidentInitialRunAssembler(
                        factory,
                        new CopilotIncidentToolSessionContextFactory(new CopilotIncidentHiddenToolContextFactory()),
                        new CopilotIncidentSessionConfigRequestFactory(new CopilotSkillRuntimeLoader(properties)),
                        artifactService(new ObjectMapper()),
                        policyFactoryWithConfiguredElastic(),
                        new CopilotIncidentPromptRenderer(),
                        new CopilotIncidentRunRequestFactory(new CopilotArtifactContentMapper()),
                        new CopilotIncidentReportFactory()
                ),
                new CopilotRunPreparationService(
                        new CopilotPreparedSessionFactory(new CopilotSessionConfigFactory(properties))
                )
        );

        String prompt;
        try (var prepared = service.prepare(request())) {
            prompt = prepared.prompt();
        }

        assertTrue(prompt.contains("Read `00-incident-manifest.json` first and use it as the artifact index, then read `01-incident-digest.md`."));
        assertTrue(prompt.contains("Use raw evidence artifacts to verify the digest before making a claim."));
        assertTrue(prompt.contains("`functionalAnalysis` must follow Functional Analysis v1"));
        assertTrue(prompt.contains("`technicalAnalysis` must follow Technical Handoff v1"));
        assertTrue(prompt.contains("\"visibilityLimits\": [\"string\"]"));
        assertTrue(prompt.contains("01-incident-digest.md"));
        assertTrue(prompt.contains("## itemId: elastic-logs-001"));
    }

    private InitialAnalysisRequest request() {
        return new InitialAnalysisRequest(
                "corr-123",
                "dev3",
                "main",
                "CRM/runtime",
                List.of(new AnalysisEvidenceSection(
                        "elasticsearch",
                        "logs",
                        List.of(new AnalysisEvidenceItem(
                                "error log",
                                List.of(new AnalysisEvidenceAttribute("message", "timeout"))
                        ))
                ))
        );
    }
}
