package pl.mkn.tdw.features.changeverification.ai.preparation;

import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

public record ChangeVerificationPromptPreparation(
        String prompt,
        Map<String, String> artifactContents
) {

    public ChangeVerificationPromptPreparation {
        artifactContents = artifactContents != null
                ? Collections.unmodifiableMap(new LinkedHashMap<>(artifactContents))
                : Map.of();
    }
}
