package pl.mkn.incidenttracker.analysis.operationalcontext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.YamlMapFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static pl.mkn.incidenttracker.analysis.operationalcontext.OperationalContextMaps.mapList;

@Component
@Slf4j
@RequiredArgsConstructor
public class OperationalContextCatalogLoader {

    private final OperationalContextProperties properties;
    private final OperationalContextMarkdownParser markdownParser = new OperationalContextMarkdownParser();

    private volatile OperationalContextCatalog cachedCatalog;

    public Optional<OperationalContextCatalog> loadCatalog() {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }

        var catalog = cachedCatalog;
        if (catalog != null) {
            return Optional.of(catalog);
        }

        synchronized (this) {
            if (cachedCatalog == null) {
                cachedCatalog = buildCatalog();
            }

            return Optional.ofNullable(cachedCatalog);
        }
    }

    private OperationalContextCatalog buildCatalog() {
        var resourceRoot = normalizeRoot(properties.getResourceRoot());

        var teams = loadYamlEntries(resourceRoot, "teams.yml", "teams");
        var processes = loadYamlEntries(resourceRoot, "processes.yml", "processes");
        var systems = loadYamlEntries(resourceRoot, "systems.yml", "systems");
        var integrations = loadYamlEntries(resourceRoot, "integrations.yml", "integrations");
        var repositories = loadYamlEntries(resourceRoot, "repo-map.yml", "repositories");
        var boundedContexts = loadYamlEntries(resourceRoot, "bounded-contexts.yml", "boundedContexts");
        var glossaryDocument = readTextResource(resourceRoot, "glossary.md");
        var handoffRulesDocument = readTextResource(resourceRoot, "handoff-rules.md");
        var indexDocument = readTextResource(resourceRoot, "operational-context-index.md");

        var glossaryTerms = markdownParser.parseGlossary(glossaryDocument);
        var handoffRules = markdownParser.parseHandoffRules(handoffRulesDocument);

        log.info(
                "Operational context catalog loaded resourceRoot={} teams={} processes={} systems={} integrations={} repositories={} boundedContexts={} glossaryTerms={} handoffRules={}",
                resourceRoot,
                teams.size(),
                processes.size(),
                systems.size(),
                integrations.size(),
                repositories.size(),
                boundedContexts.size(),
                glossaryTerms.size(),
                handoffRules.size()
        );

        return new OperationalContextCatalog(
                teams,
                processes,
                systems,
                integrations,
                repositories,
                boundedContexts,
                glossaryTerms,
                handoffRules,
                indexDocument
        );
    }

    private List<java.util.Map<String, Object>> loadYamlEntries(String resourceRoot, String fileName, String listKey) {
        var resource = resource(resourceRoot, fileName);
        if (!resource.exists()) {
            log.warn("Operational context resource missing: {}", resource.getDescription());
            return List.of();
        }

        var factoryBean = new YamlMapFactoryBean();
        factoryBean.setResources(resource);
        factoryBean.afterPropertiesSet();

        var document = factoryBean.getObject();
        if (document == null) {
            return List.of();
        }

        return mapList(document.get(listKey));
    }

    private String readTextResource(String resourceRoot, String fileName) {
        var resource = resource(resourceRoot, fileName);
        if (!resource.exists()) {
            log.warn("Operational context text resource missing: {}", resource.getDescription());
            return "";
        }

        try (var inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to read operational context resource: " + resource.getDescription(), exception);
        }
    }

    private Resource resource(String resourceRoot, String fileName) {
        return new ClassPathResource(resourceRoot + "/" + fileName);
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
