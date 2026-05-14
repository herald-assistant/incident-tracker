package pl.mkn.incidenttracker.integrations.operationalcontext;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalContextImplementationReadModelBuilderTest {

    private final OperationalContextImplementationReadModelBuilder builder =
            new OperationalContextImplementationReadModelBuilder();

    @Test
    void shouldProjectImplementationMapForBoundedContextWithLegacyAndTargetImplementations() {
        var model = builder.buildForEntity(sampleCatalog(), "boundedContext", "agreement-context");

        assertEquals("operational-context.implementation-map", model.contract());
        assertEquals("agreement-context", model.analysisTarget().id());
        assertEquals(3, model.implementations().size());
        assertTrue(model.validationFindings().isEmpty());

        var implementations = model.implementations().stream()
                .collect(Collectors.toMap(
                        OperationalContextImplementationReadModel.ImplementationView::id,
                        Function.identity()
                ));

        var primary = implementations.get("agreement-service-scope::agreement-service-repo::agreement-module");
        assertEquals("implementation", primary.implementationKind());
        assertEquals("primary", primary.lifecycleRole());
        assertEquals("active", primary.migrationStatus());
        assertEquals("agreement-service", primary.systems().get(0).id());
        assertEquals("agreement-context", primary.boundedContexts().get(0).id());
        assertEquals("agreement-process", primary.processes().get(0).id());
        assertEquals("agreement-service-repo", primary.repository().id());
        assertEquals("agreement-module", primary.module().id());
        assertEquals("agreement-service-scope", primary.codeSearchScope().id());
        assertTrue(primary.packagePrefixes().contains("com.example.agreement.process"));
        assertTrue(primary.sourceRoots().contains("agreement-module/src/main/java"));
        assertTrue(primary.importantPaths().contains("agreement-module/src/main/java/com/example/agreement"));
        assertTrue(primary.hints().classHints().contains("AgreementProcessHandler"));
        assertFalse(primary.provenance().sourceRefs().isEmpty());

        var legacy = implementations.get("agreement-legacy-scope::legacy-monolith-repo::legacy-agreement-module");
        assertEquals("source-implementation", legacy.lifecycleRole());
        assertEquals("being-replaced", legacy.migrationStatus());
        assertEquals("legacy-monolith", legacy.systems().get(0).id());
        assertEquals("agreement-context", legacy.boundedContexts().get(0).id());

        var shared = implementations.get("agreement-service-scope::shared-contracts-repo");
        assertEquals("supporting-code", shared.implementationKind());
        assertEquals("supporting-library", shared.lifecycleRole());
        assertTrue(shared.provenance().warnings().stream()
                .anyMatch(warning -> warning.contains("supporting code")));
    }

    @Test
    void shouldProjectImplementationMapForSystemAndProcessTargets() {
        var systemModel = builder.buildForEntity(sampleCatalog(), "system", "agreement-service");
        var processModel = builder.buildForEntity(sampleCatalog(), "process", "agreement-process");

        assertEquals(2, systemModel.implementations().size());
        assertTrue(systemModel.implementations().stream()
                .allMatch(implementation -> implementation.systems().stream()
                        .anyMatch(system -> system.id().equals("agreement-service"))));
        assertTrue(processModel.implementations().stream()
                .allMatch(implementation -> implementation.processes().stream()
                        .anyMatch(process -> process.id().equals("agreement-process"))));
    }

    @Test
    void shouldReportMissingCodeSearchScopeAsMissingImplementationProjection() {
        var model = builder.buildForEntity(sampleCatalog(), "system", "missing-scope-system");

        assertTrue(model.implementations().isEmpty());
        assertTrue(model.validationFindings().stream()
                .anyMatch(finding -> finding.code().equals("NO_CODE_SEARCH_SCOPE")));
    }

    @Test
    void shouldWarnWhenImplementationScopeHasNoSystemTarget() {
        var catalog = OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(map(
                        "id", "domain-repo",
                        "modules", List.of(map(
                                "id", "domain-module",
                                "source", map("packages", List.of("com.example.domain"))
                        ))
                )),
                List.of(map(
                        "id", "domain-scope",
                        "target", map("boundedContexts", List.of("domain-context")),
                        "repositories", List.of(map(
                                "repoId", "domain-repo",
                                "role", "primary",
                                "priority", 1,
                                "include", true,
                                "moduleIds", List.of("domain-module")
                        ))
                )),
                List.of(map("id", "domain-context")),
                List.of(),
                List.of(),
                List.of(),
                "index"
        );

        var model = builder.buildForEntity(catalog, "bounded-context", "domain-context");

        assertEquals(1, model.implementations().size());
        assertTrue(model.validationFindings().stream()
                .anyMatch(finding -> finding.code().equals("IMPLEMENTATION_WITHOUT_SYSTEM")));
    }

    private static OperationalContextDtos.OperationalContextCatalog sampleCatalog() {
        return OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(map(
                        "id", "agreement-process",
                        "participants", map("primarySystems", List.of("agreement-service"))
                )),
                List.of(
                        map("id", "agreement-service"),
                        map("id", "legacy-monolith"),
                        map("id", "missing-scope-system")
                ),
                List.of(),
                List.of(
                        map(
                                "id", "agreement-service-repo",
                                "name", "Agreement Service Repository",
                                "lifecycleStatus", "active",
                                "git", map(
                                        "provider", "gitlab",
                                        "group", "Group",
                                        "project", "agreement-service",
                                        "projectPath", "Group/agreement-service",
                                        "defaultBranch", "main"
                                ),
                                "sourceLayout", map(
                                        "buildTool", "maven",
                                        "sourceRoots", List.of("src/main/java"),
                                        "importantPaths", List.of("agreement-module/src/main/java/com/example/agreement")
                                ),
                                "packagePrefixes", List.of("com.example.agreement"),
                                "classHints", List.of("AgreementApplication"),
                                "modules", List.of(map(
                                        "id", "agreement-module",
                                        "name", "Agreement Module",
                                        "lifecycleStatus", "active",
                                        "sourceRoots", List.of("agreement-module/src/main/java"),
                                        "importantPaths", List.of("agreement-module/src/main/java/com/example/agreement"),
                                        "source", map(
                                                "paths", List.of("agreement-module/src/main/java"),
                                                "packages", List.of("com.example.agreement.process")
                                        ),
                                        "matchSignals", map("strong", map(
                                                "classHints", List.of("AgreementProcessHandler")
                                        ))
                                ))
                        ),
                        map(
                                "id", "legacy-monolith-repo",
                                "name", "Legacy Monolith Repository",
                                "lifecycleStatus", "being-replaced",
                                "git", map("projectPath", "Group/legacy-monolith"),
                                "modules", List.of(map(
                                        "id", "legacy-agreement-module",
                                        "name", "Legacy Agreement Module",
                                        "lifecycleStatus", "being-replaced",
                                        "source", map(
                                                "paths", List.of("legacy/src/main/java"),
                                                "packages", List.of("com.example.legacy.agreement")
                                        )
                                ))
                        ),
                        map(
                                "id", "shared-contracts-repo",
                                "name", "Shared Contracts Repository",
                                "git", map("projectPath", "Group/shared-contracts"),
                                "packagePrefixes", List.of("com.example.contracts"),
                                "classHints", List.of("AgreementContract")
                        )
                ),
                List.of(
                        map(
                                "id", "agreement-service-scope",
                                "name", "Agreement Service Scope",
                                "target", map(
                                        "systems", List.of("agreement-service"),
                                        "processes", List.of("agreement-process"),
                                        "boundedContexts", List.of("agreement-context")
                                ),
                                "repositories", List.of(
                                        map(
                                                "repoId", "agreement-service-repo",
                                                "role", "primary",
                                                "priority", 1,
                                                "include", true,
                                                "moduleIds", List.of("agreement-module"),
                                                "reason", "Target service implementation"
                                        ),
                                        map(
                                                "repoId", "shared-contracts-repo",
                                                "role", "library",
                                                "priority", 3,
                                                "include", true,
                                                "reason", "Shared API contracts"
                                        )
                                ),
                                "packagePrefixes", List.of("com.example.agreement"),
                                "classHints", List.of("AgreementController"),
                                "endpointHints", List.of("/agreements")
                        ),
                        map(
                                "id", "agreement-legacy-scope",
                                "name", "Agreement Legacy Scope",
                                "target", map(
                                        "systems", List.of("legacy-monolith"),
                                        "boundedContexts", List.of("agreement-context")
                                ),
                                "repositories", List.of(map(
                                        "repoId", "legacy-monolith-repo",
                                        "role", "legacy-source",
                                        "priority", 2,
                                        "include", true,
                                        "moduleIds", List.of("legacy-agreement-module"),
                                        "reason", "Source implementation being replaced"
                                )),
                                "packagePrefixes", List.of("com.example.legacy.agreement"),
                                "classHints", List.of("LegacyAgreementFacade")
                        )
                ),
                List.of(map(
                        "id", "agreement-context",
                        "references", map("systems", List.of("agreement-service", "legacy-monolith"))
                )),
                List.of(),
                List.of(),
                List.of(),
                "index"
        );
    }

    private static Map<String, Object> map(Object... keyValues) {
        var map = new LinkedHashMap<String, Object>();
        for (var i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
