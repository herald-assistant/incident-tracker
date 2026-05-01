package pl.mkn.incidenttracker.analysis.ai.copilot;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation.CopilotIncidentRunRequestFactory;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotArtifactContentMapper;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotRenderedArtifact;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSessionConfigRequest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class CopilotIncidentRunRequestFactoryTest {

    private final CopilotIncidentRunRequestFactory factory =
            new CopilotIncidentRunRequestFactory(new CopilotArtifactContentMapper());

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

    private CopilotRenderedArtifact artifact(String displayName, String content) {
        return new CopilotRenderedArtifact(
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
