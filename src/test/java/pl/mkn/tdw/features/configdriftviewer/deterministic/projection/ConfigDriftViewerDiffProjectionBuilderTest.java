package pl.mkn.tdw.features.configdriftviewer.deterministic.projection;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.configdriftviewer.deterministic.engine.ConfigDriftViewerDeterministicEngine;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerChangeKind;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerValueType;
import pl.mkn.tdw.features.configdriftviewer.deterministic.parse.ParsedConfigurationFile;
import pl.mkn.tdw.features.configdriftviewer.deterministic.parse.ParsedConfigurationSnapshot;
import pl.mkn.tdw.features.configdriftviewer.deterministic.parse.ConfigDriftViewerVarParser;
import pl.mkn.tdw.features.configdriftviewer.deterministic.parse.ConfigDriftViewerYamlParser;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerBranchCoverage;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerFileCoverage;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerFileRole;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerFileStatus;
import pl.mkn.tdw.features.configdriftviewer.scope.ConfigDriftViewerScope;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigDriftViewerDiffProjectionBuilderTest {

    private static final String DYNAMIC_TENANT_KEY =
            "550e8400-e29b-41d4-a716-446655440000";

    private final ConfigDriftViewerYamlParser yamlParser = new ConfigDriftViewerYamlParser();
    private final ConfigDriftViewerVarParser varParser = new ConfigDriftViewerVarParser();
    private final ConfigDriftViewerDeterministicEngine engine =
            new ConfigDriftViewerDeterministicEngine();
    private final ConfigDriftViewerDiffProjectionBuilder builder =
            new ConfigDriftViewerDiffProjectionBuilder();

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

        var yaml = file(projection, ConfigDriftViewerFileRole.APPLICATION_YAML);
        assertEquals(ConfigDriftViewerDiffFileFormat.YAML, yaml.format());
        assertEquals("backend/application.yml.kv", yaml.sourcePath());
        assertEquals("backend/application.yml.kv", yaml.targetPath());
        assertTrue(yaml.sourcePresent());
        assertTrue(yaml.targetPresent());
        assertEquals(2, yaml.documents().size());

        var changed = node(yaml, 0, "app.changed");
        assertEquals(ConfigDriftViewerChangeKind.CHANGED, changed.changeKind());
        assertEquals("source-value", changed.source().value());
        assertEquals("target-value", changed.target().value());
        assertEquals(
                List.of(differenceId(deterministicContext, 0, "app.changed")),
                changed.differenceIds()
        );

        var unchanged = node(yaml, 0, "app.unchanged");
        assertEquals(ConfigDriftViewerChangeKind.UNCHANGED, unchanged.changeKind());
        assertEquals("same", unchanged.source().value());
        assertEquals("same", unchanged.target().value());
        assertTrue(unchanged.differenceIds().isEmpty());

        var removed = node(yaml, 0, "app.removed");
        assertEquals(ConfigDriftViewerChangeKind.REMOVED, removed.changeKind());
        assertEquals("source-only", removed.source().value());
        assertEquals(ConfigDriftViewerDiffValuePresence.ABSENT, removed.target().presence());

        var added = node(yaml, 0, "app.added");
        assertEquals(ConfigDriftViewerChangeKind.ADDED, added.changeKind());
        assertEquals(ConfigDriftViewerDiffValuePresence.ABSENT, added.source().presence());
        assertEquals("target-only", added.target().value());

        var typed = node(yaml, 0, "app.typed");
        assertEquals(ConfigDriftViewerChangeKind.TYPE_CHANGED, typed.changeKind());
        assertEquals(ConfigDriftViewerValueType.NUMBER, typed.source().type());
        assertEquals(ConfigDriftViewerValueType.STRING, typed.target().type());
        assertEquals("3", typed.target().value());

        var effective = node(yaml, 0, "app.endpoint");
        assertEquals(ConfigDriftViewerChangeKind.EFFECTIVE_CHANGED, effective.changeKind());
        assertEquals("${local.backend_url}", effective.source().value());
        assertEquals("${local.backend_url}", effective.target().value());
        assertEquals("https://dev1.internal", effective.sourceEffective().value());
        assertEquals("https://zt001.internal", effective.targetEffective().value());
        assertEquals(
                List.of(differenceId(deterministicContext, 0, "app.endpoint")),
                effective.differenceIds()
        );

        var list = node(yaml, 0, "app.list");
        assertEquals(ConfigDriftViewerValueType.LIST, list.source().type());
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

        var global = file(projection, ConfigDriftViewerFileRole.GLOBAL_VAR);
        assertEquals(ConfigDriftViewerDiffFileFormat.VAR, global.format());
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
        assertTrue(projectionJson.contains("https://zt001.internal"));
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
        var yaml = file(projection, ConfigDriftViewerFileRole.APPLICATION_YAML);
        var empty = node(yaml, 0, "values.empty");
        var nullable = node(yaml, 0, "values.nullable");
        var removed = node(yaml, 0, "values.removed");
        var added = node(yaml, 0, "values.added");

        assertEquals(ConfigDriftViewerDiffValuePresence.PRESENT, empty.source().presence());
        assertEquals(ConfigDriftViewerValueType.STRING, empty.source().type());
        assertEquals("", empty.source().value());
        assertEquals(ConfigDriftViewerChangeKind.UNCHANGED, empty.changeKind());

        assertEquals(ConfigDriftViewerDiffValuePresence.PRESENT, nullable.source().presence());
        assertEquals(ConfigDriftViewerDiffValuePresence.PRESENT, nullable.target().presence());
        assertEquals(ConfigDriftViewerValueType.NULL, nullable.source().type());
        assertNull(nullable.source().value());
        assertEquals(ConfigDriftViewerChangeKind.UNCHANGED, nullable.changeKind());

        assertEquals(ConfigDriftViewerDiffValuePresence.PRESENT, removed.source().presence());
        assertEquals(ConfigDriftViewerDiffValuePresence.ABSENT, removed.target().presence());
        assertNull(removed.target().type());
        assertNull(removed.target().value());

        assertEquals(ConfigDriftViewerDiffValuePresence.ABSENT, added.source().presence());
        assertEquals(ConfigDriftViewerDiffValuePresence.PRESENT, added.target().presence());
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
                new ConfigDriftViewerBranchCoverage("zt003", false, List.of()),
                source,
                target
        );

        var projection = builder.build(source, target, deterministicContext);
        var yaml = file(projection, ConfigDriftViewerFileRole.APPLICATION_YAML);
        var enabled = node(yaml, 0, "feature.enabled");

        assertTrue(yaml.sourcePresent());
        assertFalse(yaml.targetPresent());
        assertTrue(document(yaml, 0).sourcePresent());
        assertFalse(document(yaml, 0).targetPresent());
        assertEquals(ConfigDriftViewerDiffValuePresence.PRESENT, enabled.source().presence());
        assertEquals(ConfigDriftViewerDiffValuePresence.ABSENT, enabled.target().presence());
        assertEquals(ConfigDriftViewerChangeKind.REMOVED, enabled.changeKind());
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
                        varParser.parse(ConfigDriftViewerFileRole.GLOBAL_VAR, "global.var", global),
                        varParser.parse(ConfigDriftViewerFileRole.LOCAL_VAR, "backend/local.var", local),
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

    private static ConfigDriftViewerScope scope() {
        return new ConfigDriftViewerScope(
                "runtime-config",
                "hidden-connection",
                "hidden/project",
                "backend",
                "Backend",
                "backend"
        );
    }

    private static ConfigDriftViewerBranchCoverage coverage(String branch) {
        return new ConfigDriftViewerBranchCoverage(
                branch,
                true,
                List.of(
                        fileCoverage(ConfigDriftViewerFileRole.GLOBAL_VAR, "global.var"),
                        fileCoverage(ConfigDriftViewerFileRole.LOCAL_VAR, "backend/local.var"),
                        fileCoverage(
                                ConfigDriftViewerFileRole.APPLICATION_YAML,
                                "backend/application.yml.kv"
                        )
                )
        );
    }

    private static ConfigDriftViewerBranchCoverage yamlCoverage(String branch) {
        return new ConfigDriftViewerBranchCoverage(
                branch,
                true,
                List.of(fileCoverage(
                        ConfigDriftViewerFileRole.APPLICATION_YAML,
                        "backend/application.yml.kv"
                ))
        );
    }

    private static ConfigDriftViewerFileCoverage fileCoverage(
            ConfigDriftViewerFileRole role,
            String path
    ) {
        return new ConfigDriftViewerFileCoverage(
                role,
                path,
                ConfigDriftViewerFileStatus.AVAILABLE,
                "commit",
                "last-commit",
                "2026-07-30T00:00:00Z",
                100L,
                null
        );
    }

    private static ConfigDriftViewerDiffFile file(
            ConfigDriftViewerDiffProjection projection,
            ConfigDriftViewerFileRole role
    ) {
        return projection.files().stream()
                .filter(file -> file.role() == role)
                .findFirst()
                .orElseThrow();
    }

    private static ConfigDriftViewerDiffDocument document(
            ConfigDriftViewerDiffFile file,
            int documentIndex
    ) {
        return file.documents().stream()
                .filter(document -> document.documentIndex() == documentIndex)
                .findFirst()
                .orElseThrow();
    }

    private static ConfigDriftViewerDiffNode node(
            ConfigDriftViewerDiffFile file,
            int documentIndex,
            String path
    ) {
        var found = node(document(file, documentIndex).root(), path);
        if (found == null) {
            throw new AssertionError("Missing node " + path);
        }
        return found;
    }

    private static ConfigDriftViewerDiffNode node(
            ConfigDriftViewerDiffNode current,
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
            ConfigDriftViewerDeterministicContext context,
            int documentIndex,
            String path
    ) {
        return context.differences().stream()
                .filter(difference -> difference.role() == ConfigDriftViewerFileRole.APPLICATION_YAML)
                .filter(difference -> difference.documentIndex() == documentIndex)
                .filter(difference -> difference.path().equals(path))
                .map(difference -> difference.differenceId())
                .findFirst()
                .orElseThrow();
    }
}
