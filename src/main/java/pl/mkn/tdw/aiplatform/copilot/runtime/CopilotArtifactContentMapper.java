package pl.mkn.tdw.aiplatform.copilot.runtime;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CopilotArtifactContentMapper {

    public Map<String, String> toArtifactContentMap(List<CopilotRenderedArtifact> artifacts) {
        var artifactContents = new LinkedHashMap<String, String>();
        for (var artifact : artifacts) {
            artifactContents.put(artifact.displayName(), artifact.content());
        }

        return Collections.unmodifiableMap(artifactContents);
    }
}
