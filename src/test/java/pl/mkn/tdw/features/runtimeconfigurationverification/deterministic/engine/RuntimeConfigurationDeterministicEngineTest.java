package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationChangeKind;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationFinding;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationFindingSeverity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationReferenceStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationSensitivity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.SanitizedConfigurationNode;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeConfigurationDeterministicEngineTest {

    private final RuntimeConfigurationYamlParser yamlParser = new RuntimeConfigurationYamlParser();
    private final RuntimeConfigurationVarParser varParser = new RuntimeConfigurationVarParser();
    private final RuntimeConfigurationDeterministicEngine engine =
            new RuntimeConfigurationDeterministicEngine();

    @Test
    void shouldBuildFullSanitizedSchemaDiffReferencesAndFindings() throws Exception {
        var source = snapshot(
                "dev1",
                """
                        locals {
                          endpoints = {
                            backend = "https://dev.invalid"
                          }
                          unused = "source-only-context"
                        }
                        """,
                "locals {\n  feature = true\n}",
                """
                        # yaml-comment-must-not-leak
                        spring:
                          config:
                            activate:
                              on-profile: dev
                          datasource:
                            password: source-password
                        app:
                          endpoint: ${local.endpoints.backend}
                          retries: 3
                          stable: same-value
                          envHost: dev1.internal
                          tenants:
                            550e8400-e29b-41d4-a716-446655440000:
                              enabled: true
                        """
        );
        var target = snapshot(
                "zt001",
                """
                        locals {
                          endpoints = {
                            backend = "https://target.invalid"
                          }
                          unused = "target-only-context"
                        }
                        """,
                "locals {\n  feature = true\n}",
                """
                        spring:
                          config:
                            activate:
                              on-profile: zt
                          datasource:
                            password: target-password
                        app:
                          endpoint: ${local.endpoints.backend}
                          retries: "3"
                          stable: same-value
                          envHost: dev1.internal
                          tenants:
                            550e8400-e29b-41d4-a716-446655440000:
                              enabled: true
                          added: true
                        """
        );

        var context = engine.build(
                scope(),
                coverage("dev1"),
                coverage("zt001"),
                source,
                target
        );

        assertEquals(RuntimeConfigurationDeterministicStatus.REVIEW_REQUIRED, context.status());
        assertTrue(context.documents().stream()
                .anyMatch(document -> document.role() == RuntimeConfigurationFileRole.APPLICATION_YAML
                        && document.sourceProfileToken() != null
                        && document.targetProfileToken() != null));
        var stable = findNode(context, "app.stable");
        assertEquals(RuntimeConfigurationChangeKind.UNCHANGED, stable.relation());
        assertEquals(stable.sourceValueToken(), stable.targetValueToken());

        var secretDifference = context.differences().stream()
                .filter(difference -> difference.path().equals("spring.datasource.password"))
                .findFirst()
                .orElseThrow();
        assertEquals(RuntimeConfigurationSensitivity.SENSITIVE, secretDifference.sensitivity());
        assertNull(secretDifference.sourceValueToken());
        assertNull(secretDifference.targetValueToken());
        assertTrue(context.differences().stream()
                .anyMatch(difference -> difference.path().equals("app.retries")
                        && difference.kind() == RuntimeConfigurationChangeKind.TYPE_CHANGED));
        assertTrue(context.differences().stream()
                .anyMatch(difference -> difference.path().equals("app.endpoint")
                        && difference.kind() == RuntimeConfigurationChangeKind.EFFECTIVE_CHANGED));
        assertTrue(context.references().stream()
                .anyMatch(reference -> reference.targetPath().equals("local.endpoints.backend")));
        assertTrue(context.findings().isEmpty());

        var json = new ObjectMapper().writeValueAsString(context);
        assertFalse(json.contains("source-password"));
        assertFalse(json.contains("target-password"));
        assertFalse(json.contains("https://dev.invalid"));
        assertFalse(json.contains("https://target.invalid"));
        assertFalse(json.contains("source-only-context"));
        assertFalse(json.contains("yaml-comment-must-not-leak"));
        assertFalse(json.contains("550e8400-e29b-41d4-a716-446655440000"));
        assertFalse(json.toLowerCase().contains("hmac"));
        assertFalse(json.toLowerCase().contains("hash"));
        assertFalse(json.toLowerCase().contains("sha256"));
        assertTrue(json.contains("key-"));
    }

    @Test
    void shouldUseTokensOnlyInsideOneRun() {
        var source = snapshot(
                "dev1",
                "locals {\n  stable = \"same\"\n}",
                "locals {\n}",
                "app:\n  stable: same\n  envHost: shared.internal"
        );
        var target = snapshot(
                "dev2",
                "locals {\n  stable = \"same\"\n}",
                "locals {\n}",
                "app:\n  stable: same\n  envHost: shared.internal"
        );

        var first = engine.build(scope(), coverage("dev1"), coverage("dev2"), source, target);
        var second = engine.build(scope(), coverage("dev1"), coverage("dev2"), source, target);

        assertEquals(
                findNode(first, "app.stable").sourceValueToken(),
                findNode(first, "app.stable").targetValueToken()
        );
        assertNotEquals(
                findNode(first, "app.stable").sourceValueToken(),
                findNode(second, "app.stable").sourceValueToken()
        );
        assertTrue(first.findings().isEmpty());
    }

    @Test
    void shouldMarkUnresolvedCyclicAndMalformedContextIncomplete() {
        var source = snapshot(
                "dev1",
                """
                        locals {
                          a = "local.b"
                          b = "local.a"
                        }
                        """,
                "locals {\n}",
                "app:\n  value: ${local.missing}"
        );
        var malformedTarget = new ParsedConfigurationSnapshot(
                "zt001",
                List.of(
                        varParser.parse(
                                RuntimeConfigurationFileRole.GLOBAL_VAR,
                                "global.var",
                                "locals {\n  a = \"local.b\"\n  b = \"local.a\"\n  broken ???\n}"
                        ),
                        varParser.parse(
                                RuntimeConfigurationFileRole.LOCAL_VAR,
                                "backend/local.var",
                                "locals {\n}"
                        ),
                        yamlParser.parse("backend/application.yml.kv", "app: [")
                )
        );

        var context = engine.build(
                scope(),
                coverage("dev1"),
                coverage("zt001"),
                source,
                malformedTarget
        );

        assertEquals(RuntimeConfigurationDeterministicStatus.INCOMPLETE, context.status());
        assertTrue(context.findings().stream()
                .anyMatch(finding -> finding.code().equals("CYCLIC_REFERENCE")));
        assertTrue(context.findings().stream()
                .anyMatch(finding -> finding.code().equals("UNRESOLVED_REFERENCE")));
        assertTrue(context.findings().stream()
                .anyMatch(finding -> finding.code().equals("TARGET_YAML_PARSE_ERROR")));
        assertTrue(context.findings().stream()
                .anyMatch(finding -> finding.code().equals("TARGET_VAR_UNSUPPORTED_SYNTAX")
                        && finding.severity() == RuntimeConfigurationFindingSeverity.ERROR));
    }

    @Test
    void shouldResolveColonAssignmentAndEscalateHardcodedSensitiveAdditions() {
        var source = snapshot(
                "dev1",
                "locals {}",
                "locals {}",
                "app:\n  enabled: true"
        );
        var target = snapshot(
                "dev2",
                """
                        locals {
                          endpoints = {
                            customerRecordParentNodeId: "literal-node-id"
                          }
                        }
                        """,
                "locals {}",
                """
                        spring:
                          rabbitmq:
                            username: literal-user
                            password: literal-password
                        service:
                          token: $SERVICE_TOKEN$
                        integration:
                          crmRecords:
                            customerRecordParentNodeId: ${local.endpoints.customerRecordParentNodeId}
                        """
        );

        var context = engine.build(
                scope(),
                coverage("dev1"),
                coverage("dev2"),
                source,
                target
        );

        assertEquals(RuntimeConfigurationDeterministicStatus.REVIEW_REQUIRED, context.status());
        assertFalse(context.findings().stream()
                .anyMatch(finding -> finding.code().equals("TARGET_VAR_UNSUPPORTED_SYNTAX")));
        assertFalse(context.findings().stream()
                .anyMatch(finding -> finding.code().equals("UNRESOLVED_REFERENCE")));
        assertTrue(context.references().stream()
                .anyMatch(reference -> reference.targetPath()
                        .equals("local.endpoints.customerRecordParentNodeId")
                        && reference.targetStatus() == RuntimeConfigurationReferenceStatus.RESOLVED));

        var hardcodedSensitiveFindings = context.findings().stream()
                .filter(finding -> finding.code().equals("HARDCODED_SENSITIVE_VALUE_ADDED"))
                .toList();
        assertEquals(2, hardcodedSensitiveFindings.size());
        assertTrue(hardcodedSensitiveFindings.stream()
                .allMatch(finding -> finding.severity() == RuntimeConfigurationFindingSeverity.ERROR));
        assertTrue(hardcodedSensitiveFindings.stream()
                .map(RuntimeConfigurationFinding::path)
                .toList()
                .containsAll(List.of("spring.rabbitmq.username", "spring.rabbitmq.password")));
        assertFalse(context.findings().stream()
                .anyMatch(finding -> finding.path().equals("service.token")));
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
                        fileCoverage(RuntimeConfigurationFileRole.APPLICATION_YAML, "backend/application.yml.kv")
                )
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

    private static SanitizedConfigurationNode findNode(
            RuntimeConfigurationDeterministicContext context,
            String path
    ) {
        return context.documents().stream()
                .map(document -> findNode(document.root(), path))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseThrow();
    }

    private static SanitizedConfigurationNode findNode(
            SanitizedConfigurationNode node,
            String path
    ) {
        if (node == null) {
            return null;
        }
        if (path.equals(node.path())) {
            return node;
        }
        for (var child : node.children()) {
            var found = findNode(child, path);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
