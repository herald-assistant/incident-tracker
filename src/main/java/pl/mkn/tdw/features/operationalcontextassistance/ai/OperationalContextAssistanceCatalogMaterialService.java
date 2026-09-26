package pl.mkn.tdw.features.operationalcontextassistance.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OperationalContextAssistanceCatalogMaterialService {

    private static final String RESOURCE_ROOT = "operational-context-maintenance/";
    static final List<String> DOCUMENT_NAMES = List.of(
            "systems.yml", "repo-map.yml", "code-search-scopes.yml", "processes.yml",
            "bounded-contexts.yml", "integrations.yml", "teams.yml", "glossary.yml", "handoff-rules.yml"
    );
    static final List<String> GUIDANCE_NAMES = List.of(
            "operational-context-fill-order.md", "operational-context-field-guidance.md",
            "systems-yml-update-prompt.md", "repo-map-yml-update-prompt.md",
            "code-search-scopes-yml-update-prompt.md", "processes-yml-update-prompt.md",
            "bounded-contexts-yml-update-prompt.md", "integrations-yml-update-prompt.md",
            "teams-yml-update-prompt.md", "glossary-yml-update-prompt.md",
            "handoff-rules-yml-update-prompt.md"
    );

    private final OperationalContextPort operationalContextPort;

    public OperationalContextAssistanceCatalogMaterial capture() {
        var snapshot = operationalContextPort.currentDocumentSnapshot();
        if (snapshot == null || !snapshot.documents().keySet().equals(Set.copyOf(DOCUMENT_NAMES))) {
            throw new OperationalContextAssistanceMaterialException(
                    "Nie można odczytać kompletu dziewięciu dokumentów aktywnego Operational Context."
            );
        }
        var guidance = new LinkedHashMap<String, String>();
        for (var name : GUIDANCE_NAMES) {
            var content = readGuidance(name);
            guidance.put(name, content);
        }
        return new OperationalContextAssistanceCatalogMaterial(
                snapshot.contentDigest(), snapshot.catalog(), snapshot.documents(),
                Collections.unmodifiableMap(guidance)
        );
    }

    private String readGuidance(String name) {
        var resource = new ClassPathResource(RESOURCE_ROOT + name);
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Packaged Operational Context maintenance rule unavailable: " + name,
                    exception);
        }
    }
}
