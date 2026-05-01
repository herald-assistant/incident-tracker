package pl.mkn.incidenttracker.aiplatform.copilot.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CopilotArtifactContentMapperTest {

    private final CopilotArtifactContentMapper mapper = new CopilotArtifactContentMapper();

    @Test
    void shouldMapRenderedArtifactsToRuntimeContentMapInOrder() {
        var artifactContents = mapper.toArtifactContentMap(List.of(
                artifact("00-incident-manifest.json", "{}"),
                artifact("01-incident-digest.md", "# Digest")
        ));

        assertEquals(List.of("00-incident-manifest.json", "01-incident-digest.md"),
                List.copyOf(artifactContents.keySet()));
        assertEquals("{}", artifactContents.get("00-incident-manifest.json"));
        assertEquals("# Digest", artifactContents.get("01-incident-digest.md"));
        assertThrows(UnsupportedOperationException.class, () -> artifactContents.put("other.md", "content"));
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
