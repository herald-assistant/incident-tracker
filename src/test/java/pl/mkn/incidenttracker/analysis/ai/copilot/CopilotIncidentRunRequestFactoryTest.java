package pl.mkn.incidenttracker.analysis.ai.copilot;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotArtifactService;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotIncidentRunRequestFactory;
import pl.mkn.incidenttracker.analysis.ai.copilot.runtime.CopilotSessionConfigRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static pl.mkn.incidenttracker.analysis.ai.copilot.CopilotTestFixtures.artifactService;

class CopilotIncidentRunRequestFactoryTest {

    private final CopilotIncidentRunRequestFactory factory =
            new CopilotIncidentRunRequestFactory(artifactService(new ObjectMapper()));

    @Test
    void shouldCreateCopilotRunRequestFromIncidentRuntimeInputs() {
        var sessionConfigRequest = new CopilotSessionConfigRequest(
                "analysis-123",
                List.of(),
                List.of(),
                List.of(),
                null,
                null
        );
        var artifacts = List.of(
                artifact("00-incident-manifest.json", "{\"deliveryMode\":\"embedded-prompt\"}"),
                artifact("01-incident-digest.md", "# Digest")
        );

        var runRequest = factory.create(
                "corr-123",
                "Prompt body",
                sessionConfigRequest,
                artifacts
        );

        assertEquals("corr-123", runRequest.runReference());
        assertEquals("Prompt body", runRequest.prompt());
        assertSame(sessionConfigRequest, runRequest.sessionConfigRequest());
        assertEquals(
                "{\"deliveryMode\":\"embedded-prompt\"}",
                runRequest.artifactContents().get("00-incident-manifest.json")
        );
        assertEquals("# Digest", runRequest.artifactContents().get("01-incident-digest.md"));
    }

    private CopilotArtifactService.Artifact artifact(String displayName, String content) {
        return new CopilotArtifactService.Artifact(
                displayName,
                "test",
                "copilot",
                "test",
                1,
                "text/plain",
                content
        );
    }
}
