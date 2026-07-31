package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.engine.RuntimeConfigurationDeterministicEngine;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationChangeKind;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationValueType;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.parse.ParsedConfigurationFile;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.parse.ParsedConfigurationSnapshot;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.parse.RuntimeConfigurationVarParser;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.parse.RuntimeConfigurationYamlParser;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationBranchCoverage;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationFileCoverage;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationFileRole;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationFileStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScope;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeConfigurationDiffProjectionBuilderTest {

    private static final String DYNAMIC_TENANT_KEY =
            "550e8400-e29b-41d4-a716-446655440000";

    private final RuntimeConfigurationYamlParser yamlParser = new RuntimeConfigurationYamlParser();
    private final RuntimeConfigurationVarParser varParser = new RuntimeConfigurationVarParser();
    private final RuntimeConfigurationDeterministicEngine engine =
            new RuntimeConfigurationDeterministicEngine();
    private final RuntimeConfigurationDiffProjectionBuilder builder =
            new RuntimeConfigurationDiffProjectionBuilder();

    @Test
    void shouldBuildPerFileProjectionWithActualValuesAndStableDifferenceIds() throws Exception {
        var source = snapshot(
                "dev1",
                """
                        locals {
                          backend_url = "https://dev1.internal"
                          vault_ref = "$VAULT_DB_PASSWORD_DEV$"
                        }
                        """,
                """
                        locals {
                          stable = "same"
                        }
                        """,
                """
                        app:
                          unchanged: same
                          changed: source-value
                          removed: source-only
                          nullable: null
                          typed: 3
                          endpoint: ${local.backend_url}
                          vaultReference: ${vault.database.password}
                          list:
                            - first
                            - source-second
                          tenants:
                            550e8400-e29b-41d4-a716-446655440000:
                              label: source-tenant
                        ---
                        spring:
                          config:
                            activate:
                              on-profile: dev
                        app:
                          profileValue: source-profile
                        """
        );
        var target = snapshot(
                "zt001",
                """
                        locals {
                          backend_url = "https://zt001.internal"
                          vault_ref = "$VAULT_DB_PASSWORD_ZT$"
                        }
                        """,
                """
                        locals {
                          stable = "same"
                        }
                        """,
                """
                        app:
                          unchanged: same
                          changed: target-value
                          added: target-only
                          nullable: null
                          typed: "3"
                          endpoint: ${local.backend_url}
                          vaultReference: ${vault.database.password}
                          list:
                            - first
                            - target-second
                            - third
                          tenants:
                            550e8400-e29b-41d4-a716-446655440000:
                              label: target-tenant
                        ---
                        spring:
                          config:
                            activate:
                              on-profile: zt
                        app:
                          profileValue: target-profile
                        """
        );
        var deterministicContext = engine.build(
                scope(),
                coverage("dev1"),
                coverage("zt001"),
                source,
                target
        );

        var projection = builder.build(source, target, deterministicContext);

        assertEquals("dev1", projection.sourceBranch());
        assertEquals("zt001", projection.targetBranch());
        assertEquals(3, projection.files().size());

        var yaml = file(projection, RuntimeConfigurationFileRole.APPLICATION_YAML);
        assertEquals(RuntimeConfigurationDiffFileFormat.YAML, yaml.format());
        assertEquals("backend/application.yml.kv", yaml.sourcePath());
        assertEquals("backend/application.yml.kv", yaml.targetPath());
        assertTrue(yaml.sourcePresent());
        assertTrue(yaml.targetPresent());
        assertEquals(2, yaml.documents().size());

        var changed = node(yaml, 0, "app.changed");
        assertEquals(RuntimeConfigurationChangeKind.CHANGED, changed.changeKind());
        assertEquals("source-value", changed.source().value());
        assertEquals("target-value", changed.target().value());
        assertEquals(
                List.of(differenceId(deterministicContext, 0, "app.changed")),
                changed.differenceIds()
        );

        var unchanged = node(yaml, 0, "app.unchanged");
        assertEquals(RuntimeConfigurationChangeKind.UNCHANGED, unchanged.changeKind());
        assertEquals("same", unchanged.source().value());
        assertEquals("same", unchanged.target().value());
        assertTrue(unchanged.differenceIds().isEmpty());

        var removed = node(yaml, 0, "app.removed");
        assertEquals(RuntimeConfigurationChangeKind.REMOVED, removed.changeKind());
        assertEquals("source-only", removed.source().value());
        assertEquals(RuntimeConfigurationDiffValuePresence.ABSENT, removed.target().presence());

        var added = node(yaml, 0, "app.added");
        assertEquals(RuntimeConfigurationChangeKind.ADDED, added.changeKind());
        assertEquals(RuntimeConfigurationDiffValuePresence.ABSENT, added.source().presence());
        assertEquals("target-only", added.target().value());

        var typed = node(yaml, 0, "app.typed");
        assertEquals(RuntimeConfigurationChangeKind.TYPE_CHANGED, typed.changeKind());
        assertEquals(RuntimeConfigurationValueType.NUMBER, typed.source().type());
        assertEquals(RuntimeConfigurationValueType.STRING, typed.target().type());
        assertEquals("3", typed.target().value());

        var effective = node(yaml, 0, "app.endpoint");
        assertEquals(RuntimeConfigurationChangeKind.EFFECTIVE_CHANGED, effective.changeKind());
        assertEquals("${local.backend_url}", effective.source().value());
        assertEquals("${local.backend_url}", effective.target().value());
        assertEquals(
                List.of(differenceId(deterministicContext, 0, "app.endpoint")),
                effective.differenceIds()
        );

        var list = node(yaml, 0, "app.list");
        assertEquals(RuntimeConfigurationValueType.LIST, list.source().type());
        assertEquals(2, list.source().cardinality());
        assertEquals(3, list.target().cardinality());
        assertEquals("third", node(yaml, 0, "app.list[2]").target().value());

        var dynamic = node(yaml, 0, "app.tenants." + DYNAMIC_TENANT_KEY + ".label");
        assertEquals("source-tenant", dynamic.source().value());
        assertEquals("target-tenant", dynamic.target().value());
        assertTrue(dynamic.path().contains(DYNAMIC_TENANT_KEY));
        assertFalse(dynamic.differenceIds().isEmpty());
        assertTrue(deterministicContext.differences().stream()
                .filter(difference -> dynamic.differenceIds().contains(difference.differenceId()))
                .allMatch(difference -> !difference.path().contains(DYNAMIC_TENANT_KEY)));

        var profiledDocument = document(yaml, 1);
        assertEquals("dev", profiledDocument.sourceProfile().value());
        assertEquals("zt", profiledDocument.targetProfile().value());
        var profileNode = node(yaml, 1, "spring.config.activate.on-profile");
        assertEquals(
                List.of(differenceId(
                        deterministicContext,
                        1,
                        "spring.config.activate.on-profile"
                )),
                profileNode.differenceIds()
        );

        var global = file(projection, RuntimeConfigurationFileRole.GLOBAL_VAR);
        assertEquals(RuntimeConfigurationDiffFileFormat.VAR, global.format());
        assertEquals(
                "https://dev1.internal",
                node(global, 0, "local.backend_url").source().value()
        );
        assertEquals(
                "$VAULT_DB_PASSWORD_ZT$",
                node(global, 0, "local.vault_ref").target().value()
        );

        var projectionJson = new ObjectMapper().writeValueAsString(projection);
        var deterministicJson = new ObjectMapper().writeValueAsString(deterministicContext);
        assertTrue(projectionJson.contains("source-value"));
        assertTrue(projectionJson.contains("https://dev1.internal"));
        assertTrue(projectionJson.contains("$VAULT_DB_PASSWORD_DEV$"));
        assertTrue(projectionJson.contains(DYNAMIC_TENANT_KEY));
        assertFalse(deterministicJson.contains("source-value"));
        assertFalse(deterministicJson.contains("https://dev1.internal"));
        assertFalse(deterministicJson.contains("$VAULT_DB_PASSWORD_DEV$"));
        assertFalse(deterministicJson.contains(DYNAMIC_TENANT_KEY));
        assertFalse(projection.toString().contains("source-value"));
        assertFalse(changed.toString().contains("source-value"));
        assertFalse(dynamic.toString().contains(DYNAMIC_TENANT_KEY));
    }

    @Test
    void shouldDistinguishAbsentNullAndEmptyValues() {
        var source = yamlOnlySnapshot(
                "dev2",
                """
                        values:
                          empty: ""
                          nullable: null
                          removed: source
                        """
        );
        var target = yamlOnlySnapshot(
                "zt002",
                """
                        values:
                          empty: ""
                          nullable: null
                          added: target
                        """
        );
        var deterministicContext = engine.build(
                scope(),
                yamlCoverage("dev2"),
                yamlCoverage("zt002"),
                source,
                target
        );

        var projection = builder.build(source, target, deterministicContext);
        var yaml = file(projection, RuntimeConfigurationFileRole.APPLICATION_YAML);
        var empty = node(yaml, 0, "values.empty");
        var nullable = node(yaml, 0, "values.nullable");
        var removed = node(yaml, 0, "values.removed");
        var added = node(yaml, 0, "values.added");

        assertEquals(RuntimeConfigurationDiffValuePresence.PRESENT, empty.source().presence());
        assertEquals(RuntimeConfigurationValueType.STRING, empty.source().type());
        assertEquals("", empty.source().value());
        assertEquals(RuntimeConfigurationChangeKind.UNCHANGED, empty.changeKind());

        assertEquals(RuntimeConfigurationDiffValuePresence.PRESENT, nullable.source().presence());
        assertEquals(RuntimeConfigurationDiffValuePresence.PRESENT, nullable.target().presence());
        assertEquals(RuntimeConfigurationValueType.NULL, nullable.source().type());
        assertNull(nullable.source().value());
        assertEquals(RuntimeConfigurationChangeKind.UNCHANGED, nullable.changeKind());

        assertEquals(RuntimeConfigurationDiffValuePresence.PRESENT, removed.source().presence());
        assertEquals(RuntimeConfigurationDiffValuePresence.ABSENT, removed.target().presence());
        assertNull(removed.target().type());
        assertNull(removed.target().value());

        assertEquals(RuntimeConfigurationDiffValuePresence.ABSENT, added.source().presence());
        assertEquals(RuntimeConfigurationDiffValuePresence.PRESENT, added.target().presence());
    }

    @Test
    void shouldRepresentMissingFileAndAllOfItsTargetValuesAsAbsent() {
        var source = yamlOnlySnapshot(
                "dev3",
                """
                        feature:
                          enabled: true
                        """
        );
        var target = new ParsedConfigurationSnapshot("zt003", List.of());
        var deterministicContext = engine.build(
                scope(),
                yamlCoverage("dev3"),
                new RuntimeConfigurationBranchCoverage("zt003", false, List.of()),
                source,
                target
        );

        var projection = builder.build(source, target, deterministicContext);
        var yaml = file(projection, RuntimeConfigurationFileRole.APPLICATION_YAML);
        var enabled = node(yaml, 0, "feature.enabled");

        assertTrue(yaml.sourcePresent());
        assertFalse(yaml.targetPresent());
        assertTrue(document(yaml, 0).sourcePresent());
        assertFalse(document(yaml, 0).targetPresent());
        assertEquals(RuntimeConfigurationDiffValuePresence.PRESENT, enabled.source().presence());
        assertEquals(RuntimeConfigurationDiffValuePresence.ABSENT, enabled.target().presence());
        assertEquals(RuntimeConfigurationChangeKind.REMOVED, enabled.changeKind());
    }

    private ParsedConfigurationSnapshot snapshot(
            String branch,
            String global,
            String local,
            String yaml
    ) {
        return new ParsedConfigurationSnapshot(
                branch,
                List.of(
                        varParser.parse(RuntimeConfigurationFileRole.GLOBAL_VAR, "global.var", global),
                        varParser.parse(RuntimeConfigurationFileRole.LOCAL_VAR, "backend/local.var", local),
                        yamlParser.parse("backend/application.yml.kv", yaml)
                )
        );
    }

    private ParsedConfigurationSnapshot yamlOnlySnapshot(String branch, String yaml) {
        return new ParsedConfigurationSnapshot(
                branch,
                List.of(yamlParser.parse("backend/application.yml.kv", yaml))
        );
    }

    private static RuntimeConfigurationScope scope() {
        return new RuntimeConfigurationScope(
                "runtime-config",
                "hidden-connection",
                "hidden/project",
                "backend",
                "Backend",
                "backend"
        );
    }

    private static RuntimeConfigurationBranchCoverage coverage(String branch) {
        return new RuntimeConfigurationBranchCoverage(
                branch,
                true,
                List.of(
                        fileCoverage(RuntimeConfigurationFileRole.GLOBAL_VAR, "global.var"),
                        fileCoverage(RuntimeConfigurationFileRole.LOCAL_VAR, "backend/local.var"),
                        fileCoverage(
                                RuntimeConfigurationFileRole.APPLICATION_YAML,
                                "backend/application.yml.kv"
                        )
                )
        );
    }

    private static RuntimeConfigurationBranchCoverage yamlCoverage(String branch) {
        return new RuntimeConfigurationBranchCoverage(
                branch,
                true,
                List.of(fileCoverage(
                        RuntimeConfigurationFileRole.APPLICATION_YAML,
                        "backend/application.yml.kv"
                ))
        );
    }

    private static RuntimeConfigurationFileCoverage fileCoverage(
            RuntimeConfigurationFileRole role,
            String path
    ) {
        return new RuntimeConfigurationFileCoverage(
                role,
                path,
                RuntimeConfigurationFileStatus.AVAILABLE,
                "commit",
                "last-commit",
                "2026-07-30T00:00:00Z",
                100L,
                null
        );
    }

    private static RuntimeConfigurationDiffFile file(
            RuntimeConfigurationDiffProjection projection,
            RuntimeConfigurationFileRole role
    ) {
        return projection.files().stream()
                .filter(file -> file.role() == role)
                .findFirst()
                .orElseThrow();
    }

    private static RuntimeConfigurationDiffDocument document(
            RuntimeConfigurationDiffFile file,
            int documentIndex
    ) {
        return file.documents().stream()
                .filter(document -> document.documentIndex() == documentIndex)
                .findFirst()
                .orElseThrow();
    }

    private static RuntimeConfigurationDiffNode node(
            RuntimeConfigurationDiffFile file,
            int documentIndex,
            String path
    ) {
        var found = node(document(file, documentIndex).root(), path);
        if (found == null) {
            throw new AssertionError("Missing node " + path);
        }
        return found;
    }

    private static RuntimeConfigurationDiffNode node(
            RuntimeConfigurationDiffNode current,
            String path
    ) {
        if (path.equals(current.path())) {
            return current;
        }
        for (var child : current.children()) {
            var found = node(child, path);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static String differenceId(
            RuntimeConfigurationDeterministicContext context,
            int documentIndex,
            String path
    ) {
        return context.differences().stream()
                .filter(difference -> difference.role() == RuntimeConfigurationFileRole.APPLICATION_YAML)
                .filter(difference -> difference.documentIndex() == documentIndex)
                .filter(difference -> difference.path().equals(path))
                .map(difference -> difference.differenceId())
                .findFirst()
                .orElseThrow();
    }
}
