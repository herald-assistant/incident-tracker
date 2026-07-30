package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.parse;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationValueType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeConfigurationYamlParserTest {

    private final RuntimeConfigurationYamlParser parser = new RuntimeConfigurationYamlParser();

    @Test
    void shouldPreserveMultiDocumentProfilesNestingTypesAndLists() {
        var parsed = parser.parse("backend/application.yml.kv", """
                feature:
                  enabled: true
                  limits:
                    - 2
                    - 5
                ---
                spring:
                  config:
                    activate:
                      on-profile: target
                feature:
                  endpoint: ${local.endpoints.backend}
                """);

        assertEquals(2, parsed.documents().size());
        assertEquals("target", parsed.documents().get(1).profileValue());
        assertEquals(
                RuntimeConfigurationValueType.BOOLEAN,
                find(parsed.documents().get(0).root(), "feature.enabled").type()
        );
        assertEquals(
                RuntimeConfigurationValueType.LIST,
                find(parsed.documents().get(0).root(), "feature.limits").type()
        );
        assertEquals(
                new java.math.BigDecimal("5"),
                new java.math.BigDecimal(find(
                        parsed.documents().get(0).root(),
                        "feature.limits[1]"
                ).scalarValue().toString())
        );
        assertTrue(parsed.issues().isEmpty());
    }

    @Test
    void shouldReportDuplicateAndMalformedYamlWithoutReturningRawErrorText() {
        var duplicate = parser.parse("application.yml.kv", """
                feature:
                  enabled: true
                  enabled: false
                """);
        var malformed = parser.parse("application.yml.kv", "feature: [");

        assertTrue(duplicate.issues().stream()
                .anyMatch(issue -> issue.code().equals("YAML_DUPLICATE_KEY")));
        assertTrue(malformed.issues().stream()
                .anyMatch(issue -> issue.code().equals("YAML_PARSE_ERROR")));
    }

    private static ParsedConfigurationNode find(ParsedConfigurationNode node, String path) {
        if (node.path().equals(path)) {
            return node;
        }
        return node.children().stream()
                .map(child -> findOrNull(child, path))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow();
    }

    private static ParsedConfigurationNode findOrNull(ParsedConfigurationNode node, String path) {
        if (node.path().equals(path)) {
            return node;
        }
        for (var child : node.children()) {
            var found = findOrNull(child, path);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
