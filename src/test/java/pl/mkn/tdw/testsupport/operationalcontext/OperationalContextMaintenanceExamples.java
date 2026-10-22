package pl.mkn.tdw.testsupport.operationalcontext;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextCatalogEntityType;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextCatalog;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Reads the actual fictional CRM examples; no runtime storage or copied fixtures. */
public final class OperationalContextMaintenanceExamples {
    private static final Pattern FENCE = Pattern.compile("(?m)^```(yaml|json)\\R([\\s\\S]*?)^```\\s*$");

    private OperationalContextMaintenanceExamples() {}

    public static String read(String name) {
        try {
            return Files.readString(Path.of("operational-context-maintenance", name)).replace("\r\n", "\n");
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static List<Block> blocks(String markdown) {
        var result = new ArrayList<Block>();
        var matcher = FENCE.matcher(markdown);
        while (matcher.find()) result.add(new Block(matcher.group(1), matcher.group(2)));
        return List.copyOf(result);
    }

    public static String promptName(OperationalContextCatalogEntityType type) {
        return type.logicalDocument().replace(".yml", "-yml-update-prompt.md");
    }

    public static Map<String, String> documents() {
        var result = new LinkedHashMap<String, String>();
        for (var type : OperationalContextCatalogEntityType.values()) {
            result.put(type.logicalDocument(), blocks(read(promptName(type))).get(0).body());
        }
        return result;
    }

    public static Map<String, Map<String, Object>> decoded(Map<String, String> documents) {
        var result = new LinkedHashMap<String, Map<String, Object>>();
        documents.forEach((name, content) -> result.put(name, yaml(content)));
        return result;
    }

    public static Map<String, Object> yaml(String content) {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        return new Yaml(new SafeConstructor(options)).load(content);
    }

    public static Map<String, String> rendered(Map<String, Map<String, Object>> decoded) {
        var result = new LinkedHashMap<String, String>();
        decoded.forEach((name, document) -> result.put(name, new Yaml().dump(document)));
        return result;
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> entities(
            Map<String, Map<String, Object>> documents, OperationalContextCatalogEntityType type
    ) {
        return (List<Map<String, Object>>) documents.get(type.logicalDocument()).get(type.collectionName());
    }

    public static Map<String, Object> entity(
            Map<String, Map<String, Object>> documents, OperationalContextCatalogEntityType type, String id
    ) {
        return entities(documents, type).stream().filter(entity -> id.equals(entity.get("id")))
                .findFirst().orElseThrow();
    }

    public static OperationalContextCatalog catalog(Map<String, String> documents) {
        var raw = decoded(documents);
        return OperationalContextDtos.catalogFromRaw(
                entities(raw, OperationalContextCatalogEntityType.TEAM),
                entities(raw, OperationalContextCatalogEntityType.PROCESS),
                entities(raw, OperationalContextCatalogEntityType.SYSTEM),
                entities(raw, OperationalContextCatalogEntityType.INTEGRATION),
                entities(raw, OperationalContextCatalogEntityType.REPOSITORY),
                entities(raw, OperationalContextCatalogEntityType.CODE_SEARCH_SCOPE),
                entities(raw, OperationalContextCatalogEntityType.BOUNDED_CONTEXT),
                entities(raw, OperationalContextCatalogEntityType.GLOSSARY_TERM).stream().map(OperationalContextDtos::glossaryTerm).toList(),
                entities(raw, OperationalContextCatalogEntityType.HANDOFF_RULE).stream().map(OperationalContextDtos::handoffRule).toList(),
                List.of());
    }

    public static List<Target> targets() {
        var documents = decoded(documents());
        return Arrays.stream(OperationalContextCatalogEntityType.values()).flatMap(type -> entities(documents, type).stream()
                .map(entity -> new Target(type.externalName(), entity.get("id").toString()))).toList();
    }

    public static Map<String, String> withRedundantReferences() {
        var raw = decoded(documents());
        references(entity(raw, OperationalContextCatalogEntityType.SYSTEM, "crm-contact-core"))
                .put("boundedContexts", List.of("crm-contact-preferences"));
        var integration = references(entity(raw, OperationalContextCatalogEntityType.INTEGRATION, "crm-contact-to-core"));
        integration.put("systems", List.of("crm-agent-portal", "crm-contact-core"));
        integration.put("boundedContexts", List.of("crm-contact-preferences"));
        integration.put("processes", List.of("crm-contact-preference-update"));
        return rendered(raw);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> references(Map<String, Object> entity) {
        return (Map<String, Object>) entity.computeIfAbsent("references", ignored -> new LinkedHashMap<>());
    }

    public record Block(String format, String body) {}
    public record Target(String type, String id) {
        @Override public String toString() { return type + ":" + id; }
    }
}
