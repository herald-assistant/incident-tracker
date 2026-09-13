package pl.mkn.tdw.features.operationalcontextassistance.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    static final int MAX_CATALOG_BYTES = 1024 * 1024;
    static final int MAX_GUIDANCE_BYTES = 512 * 1024;

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
    private final ObjectMapper objectMapper;

    public OperationalContextAssistanceCatalogMaterial capture() {
        var snapshot = operationalContextPort.currentDocumentSnapshot();
        if (snapshot == null || !snapshot.documents().keySet().equals(Set.copyOf(DOCUMENT_NAMES))) {
            throw new OperationalContextAssistanceMaterialException(
                    "Nie można odczytać kompletu dziewięciu dokumentów aktywnego Operational Context."
            );
        }
        if (catalogBytes(snapshot.documents()) > MAX_CATALOG_BYTES) {
            throw new OperationalContextAssistanceMaterialException(
                    "Aktywny katalog Operational Context przekracza limit 1 MiB materiału AI; asysta wymaga pełnego katalogu."
            );
        }
        var guidance = new LinkedHashMap<String, String>();
        int guidanceBytes = 0;
        for (var name : GUIDANCE_NAMES) {
            var content = readGuidance(name);
            guidanceBytes += content.getBytes(StandardCharsets.UTF_8).length;
            if (guidanceBytes > MAX_GUIDANCE_BYTES) {
                throw new OperationalContextAssistanceMaterialException(
                        "Reguły utrzymania Operational Context przekraczają limit 512 KiB materiału AI."
                );
            }
            guidance.put(name, content);
        }
        return new OperationalContextAssistanceCatalogMaterial(
                snapshot.contentDigest(), snapshot.catalog(), snapshot.documents(),
                Collections.unmodifiableMap(guidance)
        );
    }

    private int catalogBytes(Object documents) {
        try {
            return objectMapper.writeValueAsBytes(documents).length;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Operational Context documents cannot be serialized for AI.", exception);
        }
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
