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
        var model = builder.buildForEntity(sampleCatalog(), "boundedContext", "customer-profile-context");

        assertEquals("operational-context.implementation-map", model.contract());
        assertEquals("customer-profile-context", model.analysisTarget().id());
        assertEquals(3, model.implementations().size());
        assertTrue(model.validationFindings().isEmpty());

        var implementations = model.implementations().stream()
                .collect(Collectors.toMap(
                        OperationalContextImplementationReadModel.ImplementationView::id,
                        Function.identity()
                ));

        var primary = implementations.get("crm-customer-service-scope::crm-customer-service-repo::customer-module");
        assertEquals("implementation", primary.implementationKind());
        assertEquals("primary", primary.lifecycleRole());
        assertEquals("active", primary.migrationStatus());
        assertEquals("crm-customer-service", primary.systems().get(0).id());
        assertEquals("customer-profile-context", primary.boundedContexts().get(0).id());
        assertEquals("customer-profile-process", primary.processes().get(0).id());
        assertEquals("crm-customer-service-repo", primary.repository().id());
        assertEquals("customer-module", primary.module().id());
        assertEquals("crm-customer-service-scope", primary.codeSearchScope().id());
        assertTrue(primary.packagePrefixes().contains("com.example.crm.customer"));
        assertTrue(primary.sourceRoots().contains("customer-module/src/main/java"));
        assertTrue(primary.importantPaths().contains("customer-module/src/main/java/com/example/customer"));
        assertTrue(primary.hints().classHints().contains("CustomerProfileHandler"));
        assertFalse(primary.provenance().sourceRefs().isEmpty());

        var legacy = implementations.get("customer-legacy-scope::legacy-monolith-repo::legacy-customer-module");
        assertEquals("source-implementation", legacy.lifecycleRole());
        assertEquals("being-replaced", legacy.migrationStatus());
        assertTrue(legacy.systems().stream().anyMatch(system -> system.id().equals("legacy-monolith")));
        assertEquals("customer-profile-context", legacy.boundedContexts().get(0).id());

        var shared = implementations.get("crm-customer-service-scope::shared-contracts-repo");
        assertEquals("supporting-code", shared.implementationKind());
        assertEquals("supporting-library", shared.lifecycleRole());
        assertTrue(shared.provenance().warnings().stream()
                .anyMatch(warning -> warning.contains("supporting code")));
    }

    @Test
    void shouldProjectImplementationMapForSystemAndProcessTargets() {
        var systemModel = builder.buildForEntity(sampleCatalog(), "system", "crm-customer-service");
        var processModel = builder.buildForEntity(sampleCatalog(), "process", "customer-profile-process");

        assertEquals(1, systemModel.implementations().size());
        assertTrue(systemModel.implementations().stream()
                .allMatch(implementation -> implementation.systems().stream()
                        .anyMatch(system -> system.id().equals("crm-customer-service"))));
        assertTrue(processModel.implementations().stream()
                .allMatch(implementation -> implementation.processes().stream()
                        .anyMatch(process -> process.id().equals("customer-profile-process"))));
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
                        "target", map("type", "bounded-context", "id", "domain-context"),
                        "repositories", List.of(map(
                                "repoId", "domain-repo",
                                "role", "primary-implementation",
                                "priority", 1,
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
                        "id", "customer-profile-process",
                        "participants", map("primarySystems", List.of("crm-customer-service")),
                        "references", map("boundedContexts", List.of("customer-profile-context"))
                )),
                List.of(
                        map("id", "crm-customer-service"),
                        map("id", "legacy-monolith"),
                        map("id", "missing-scope-system")
                ),
                List.of(),
                List.of(
                        map(
                                "id", "crm-customer-service-repo",
                                "name", "CRM Customer Service Repository",
                                "lifecycleStatus", "active",
                                "git", map(
                                        "provider", "gitlab",
                                        "group", "Group",
                                        "project", "crm-customer-service",
                                        "projectPath", "Group/crm-customer-service",
                                        "defaultBranch", "main"
                                ),
                                "sourceLayout", map(
                                        "buildTool", "maven",
                                        "sourceRoots", List.of("src/main/java"),
                                        "importantPaths", List.of("customer-module/src/main/java/com/example/customer")
                                ),
                                "packagePrefixes", List.of("com.example.crm.customer"),
                                "classHints", List.of("CustomerApplication"),
                                "modules", List.of(map(
                                        "id", "customer-module",
                                        "name", "Customer Module",
                                        "lifecycleStatus", "active",
                                        "sourceRoots", List.of("customer-module/src/main/java"),
                                        "importantPaths", List.of("customer-module/src/main/java/com/example/customer"),
                                        "source", map(
                                                "paths", List.of("customer-module/src/main/java"),
                                                "packages", List.of("com.example.crm.customer")
                                        ),
                                        "matchSignals", map("strong", map(
                                                "classHints", List.of("CustomerProfileHandler")
                                        ))
                                ))
                        ),
                        map(
                                "id", "legacy-monolith-repo",
                                "name", "Legacy Monolith Repository",
                                "lifecycleStatus", "being-replaced",
                                "git", map("projectPath", "Group/legacy-monolith"),
                                "modules", List.of(map(
                                        "id", "legacy-customer-module",
                                        "name", "Legacy Customer Module",
                                        "lifecycleStatus", "being-replaced",
                                        "source", map(
                                                "paths", List.of("legacy/src/main/java"),
                                                "packages", List.of("com.example.legacy.customer")
                                        )
                                ))
                        ),
                        map(
                                "id", "shared-contracts-repo",
                                "name", "Shared Contracts Repository",
                                "git", map("projectPath", "CRM/runtime/crm-shared-contracts"),
                                "packagePrefixes", List.of("com.example.contracts"),
                                "classHints", List.of("CustomerContract")
                        )
                ),
                List.of(
                        map(
                                "id", "crm-customer-service-scope",
                                "name", "CRM Customer Service Scope",
                                "target", map("type", "bounded-context", "id", "customer-profile-context"),
                                "repositories", List.of(
                                        map(
                                                "repoId", "crm-customer-service-repo",
                                                "role", "primary-implementation",
                                                "priority", 1,
                                                "moduleIds", List.of("customer-module"),
                                                "reason", "Target service implementation"
                                        ),
                                        map(
                                                "repoId", "shared-contracts-repo",
                                                "role", "supporting-library",
                                                "priority", 3,
                                                "reason", "Shared API contracts"
                                        )
                                ),
                                "hints", map(
                                        "packagePrefixes", List.of("com.example.crm.customer"),
                                        "classHints", List.of("CustomerController"),
                                        "endpointHints", List.of("/crm/customers")
                                )
                        ),
                        map(
                                "id", "customer-legacy-scope",
                                "name", "Customer Legacy Scope",
                                "target", map("type", "bounded-context", "id", "customer-profile-context"),
                                "repositories", List.of(map(
                                        "repoId", "legacy-monolith-repo",
                                        "role", "legacy-implementation",
                                        "priority", 2,
                                        "moduleIds", List.of("legacy-customer-module"),
                                        "reason", "Source implementation being replaced"
                                )),
                                "hints", map(
                                        "packagePrefixes", List.of("com.example.legacy.customer"),
                                        "classHints", List.of("LegacyCustomerFacade")
                                )
                        ),
                        map(
                                "id", "customer-system-scope",
                                "name", "Customer System Scope",
                                "target", map("type", "system", "id", "crm-customer-service"),
                                "repositories", List.of(map(
                                        "repoId", "crm-customer-service-repo",
                                        "role", "primary-implementation",
                                        "priority", 1,
                                        "moduleIds", List.of("customer-module")
                                ))
                        ),
                        map(
                                "id", "customer-process-scope",
                                "name", "Customer Process Scope",
                                "target", map("type", "process", "id", "customer-profile-process"),
                                "repositories", List.of(map(
                                        "repoId", "crm-customer-service-repo",
                                        "role", "primary-implementation",
                                        "priority", 1,
                                        "moduleIds", List.of("customer-module")
                                ))
                        )
                ),
                List.of(map(
                        "id", "customer-profile-context",
                        "references", map("systems", List.of("crm-customer-service", "legacy-monolith"))
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
