package pl.mkn.tdw.integrations.operationalcontext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
final class ClasspathOperationalContextDocumentSource implements OperationalContextDocumentSource {

    static final List<String> DOCUMENT_NAMES = List.of(
            "teams.yml",
            "processes.yml",
            "systems.yml",
            "integrations.yml",
            "repo-map.yml",
            "code-search-scopes.yml",
            "bounded-contexts.yml",
            "glossary.yml",
            "handoff-rules.yml",
            "operational-context-index.md"
    );

    private final OperationalContextProperties properties;

    @Override
    public OperationalContextRawDocuments loadDocuments() {
        var resourceRoot = normalizeRoot(properties.getResourceRoot());
        var contents = new LinkedHashMap<String, String>();
        DOCUMENT_NAMES.forEach(name -> contents.put(name, read(resourceRoot, name)));
        return new OperationalContextRawDocuments("classpath", contents);
    }

    private String read(String resourceRoot, String logicalDocument) {
        var resource = new ClassPathResource(resourceRoot + "/" + logicalDocument);
        if (!resource.exists()) {
            log.warn("Operational context resource missing: {}", resource.getDescription());
            return "";
        }

        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to read operational context resource: " + resource.getDescription(),
                    exception
            );
        }
    }

    private String normalizeRoot(String resourceRoot) {
        if (!StringUtils.hasText(resourceRoot)) {
            return "operational-context";
        }

        var normalized = resourceRoot.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
