package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.parse;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationValueType;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationFileRole;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeConfigurationVarParserTest {

    private final RuntimeConfigurationVarParser parser = new RuntimeConfigurationVarParser();

    @Test
    void shouldParseSupportedVariableLocalsMapsAndListsWithoutComments() {
        var parsed = parser.parse(RuntimeConfigurationFileRole.GLOBAL_VAR, "global.var", """
                # sensitive operational comment must disappear
                variable "environment" {
                  default = "dev1"
                }
                locals {
                  endpoints = {
                    backend = "https://backend.invalid"
                  }
                  flags = [true, false]
                }
                """);

        assertEquals(
                "dev1",
                find(parsed.documents().get(0).root(), "variable.environment.default").scalarValue()
        );
        assertEquals(
                "https://backend.invalid",
                find(parsed.documents().get(0).root(), "local.endpoints.backend").scalarValue()
        );
        assertEquals(
                true,
                find(parsed.documents().get(0).root(), "local.flags[0]").scalarValue()
        );
        assertFalse(parsed.toString().contains("sensitive operational comment"));
        assertTrue(parsed.issues().isEmpty());
    }

    @Test
    void shouldReportDuplicateUnsupportedAndMalformedSyntax() {
        var parsed = parser.parse(RuntimeConfigurationFileRole.LOCAL_VAR, "backend/local.var", """
                locals {
                  duplicate = "first"
                  duplicate = "second"
                  inline = { nested = "unsupported" }
                """);

        assertTrue(parsed.issues().stream()
                .anyMatch(issue -> issue.code().equals("VAR_DUPLICATE_KEY")));
        assertTrue(parsed.issues().stream()
                .anyMatch(issue -> issue.code().equals("VAR_UNSUPPORTED_EXPRESSION")));
    }

    @Test
    void shouldAcceptCommonInlineEmptyBlocksAndMaps() {
        var parsed = parser.parse(RuntimeConfigurationFileRole.LOCAL_VAR, "backend/local.var", """
                locals {}
                variable "optional" {}
                empty = {}
                """);

        assertEquals(
                RuntimeConfigurationValueType.MAP,
                find(parsed.documents().get(0).root(), "local").type()
        );
        assertEquals(
                RuntimeConfigurationValueType.MAP,
                find(parsed.documents().get(0).root(), "variable.optional").type()
        );
        assertEquals(
                RuntimeConfigurationValueType.MAP,
                find(parsed.documents().get(0).root(), "empty").type()
        );
        assertTrue(parsed.issues().isEmpty());
    }

    @Test
    void shouldAcceptColonAndEqualsAssignmentsAsEquivalentSyntax() {
        var parsed = parser.parse(RuntimeConfigurationFileRole.GLOBAL_VAR, "global.var", """
                locals {
                  endpoints: {
                    draftDocumentParentNodeId: "literal-value"
                    fallbackDocumentParentNodeId = "fallback-value"
                  }
                  flags: [true, false]
                }
                """);

        assertEquals(
                "literal-value",
                find(parsed.documents().get(0).root(),
                        "local.endpoints.draftDocumentParentNodeId").scalarValue()
        );
        assertEquals(
                "fallback-value",
                find(parsed.documents().get(0).root(),
                        "local.endpoints.fallbackDocumentParentNodeId").scalarValue()
        );
        assertEquals(
                true,
                find(parsed.documents().get(0).root(), "local.flags[0]").scalarValue()
        );
        assertTrue(parsed.issues().isEmpty());
    }

    private static ParsedConfigurationNode find(ParsedConfigurationNode node, String path) {
        if (node.path().equals(path)) {
            return node;
        }
        for (var child : node.children()) {
            try {
                return find(child, path);
            } catch (java.util.NoSuchElementException ignored) {
            }
        }
        throw new java.util.NoSuchElementException(path);
    }
}
