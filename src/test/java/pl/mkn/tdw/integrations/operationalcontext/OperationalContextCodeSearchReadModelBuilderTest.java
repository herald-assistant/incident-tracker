package pl.mkn.tdw.integrations.operationalcontext;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalContextCodeSearchReadModelBuilderTest {

    private final OperationalContextCodeSearchReadModelBuilder builder =
            new OperationalContextCodeSearchReadModelBuilder();

    @Test
    void shouldBuildCodeSearchReadModelForSystemTarget() {
        var model = builder.buildForEntity(sampleCatalog(), "system", "crm-customer-service");

        assertEquals("operational-context.code-search", model.contract());
        assertEquals("crm-customer-service", model.analysisTarget().id());
        assertEquals(1, model.scopes().size());
        assertEquals("crm-customer-service-scope", model.scopes().get(0).scope().id());
        assertEquals(2, model.repositories().size());
        assertEquals("crm-customer-service-repo", model.repositories().get(0).repository().id());
        assertEquals("primary", model.repositories().get(0).role());
        assertEquals("Main repository", model.repositories().get(0).reason());
        assertTrue(model.scopes().get(0).repositories().stream()
                .anyMatch(repository -> repository.id().equals("crm-customer-service-repo")));
        assertEquals("CRM/customer-platform/crm-customer-service-repo", model.repositories().get(0).git().projectPath());
        assertTrue(model.limitations().contains("Generated clients not included"));
        assertTrue(model.validationFindings().isEmpty());
    }

    @Test
    void shouldBuildCodeSearchReadModelForBoundedContextAliasType() {
        var model = builder.buildForEntity(sampleCatalog(), "boundedContext", "customer-profile-context");

        assertEquals(1, model.scopes().size());
        assertEquals("customer-profile-context-scope", model.scopes().get(0).scope().id());
        assertFalse(model.repositories().isEmpty());
    }

    @Test
    void shouldUseOnlySemanticScopeTarget() {
        var catalog = OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(map("id", "customer-support-process")),
                List.of(map("id", "crm-customer-service")),
                List.of(),
                List.of(map(
                        "id", "crm-customer-service-repo",
                        "references", map(
                                "systems", List.of("crm-customer-service"),
                                "boundedContexts", List.of("customer-profile-context"),
                                "terms", List.of("customer-profile-term")
                        )
                )),
                List.of(map(
                        "id", "repo-derived-scope",
                        "target", map("type", "process", "id", "customer-support-process"),
                        "repositories", List.of(map(
                                "repoId", "crm-customer-service-repo",
                                "role", "primary",
                                "priority", 1
                        ))
                )),
                List.of(map("id", "customer-profile-context")),
                List.of(new OperationalContextDtos.OperationalContextGlossaryTerm(
                        "customer-profile-term",
                        "Customer profile term",
                        null,
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()
                )),
                List.of(),
                List.of(),
                "index"
        );

        var processModel = builder.buildForEntity(catalog, "process", "customer-support-process");
        var systemModel = builder.buildForEntity(catalog, "system", "crm-customer-service");

        assertEquals("repo-derived-scope", processModel.scopes().get(0).scope().id());
        assertEquals("process", processModel.scopes().get(0).target().type());
        assertEquals("customer-support-process", processModel.scopes().get(0).target().id());
        assertTrue(systemModel.scopes().isEmpty());
    }

    @Test
    void shouldResolveScopeDirectly() {
        var model = builder.buildForEntity(sampleCatalog(), "codeSearchScope", "crm-customer-service-scope");

        assertEquals("crm-customer-service-scope", model.analysisTarget().id());
        assertEquals(1, model.scopes().size());
        assertEquals(2, model.repositories().size());
    }

    @Test
    void shouldReportMissingScopeForEntity() {
        var model = builder.buildForEntity(sampleCatalog(), "system", "missing-scope-system");

        assertTrue(model.scopes().isEmpty());
        assertTrue(model.validationFindings().stream()
                .anyMatch(finding -> finding.code().equals("NO_CODE_SEARCH_SCOPE")));
    }

    @Test
    void shouldReportMissingIncludedRepository() {
        var catalog = OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(map("id", "crm-customer-service")),
                List.of(),
                List.of(),
                List.of(map(
                        "id", "broken-scope",
                        "target", map("type", "system", "id", "crm-customer-service"),
                        "repositories", List.of(map(
                                "repoId", "missing-repo",
                                "role", "primary"
                        ))
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        );

        var model = builder.buildForEntity(catalog, "system", "crm-customer-service");

        assertEquals(1, model.scopes().size());
        assertTrue(model.repositories().isEmpty());
        assertTrue(model.validationFindings().stream()
                .anyMatch(finding -> finding.code().equals("UNKNOWN_CODE_SEARCH_REPOSITORY")
                        && finding.severity().equals("error")));
    }

    private static OperationalContextDtos.OperationalContextCatalog sampleCatalog() {
        return OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(map(
                        "id", "customer-support-process",
                        "participants", map("primarySystems", List.of("crm-customer-service"))
                )),
                List.of(map("id", "crm-customer-service"), map("id", "missing-scope-system")),
                List.of(map("id", "crm-customer-to-notification-sync")),
                List.of(
                        map(
                                "id", "crm-customer-service-repo",
                                "name", "CRM Customer Service Repository",
                                "git", map(
                                        "provider", "gitlab",
                                        "group", "CRM/customer-platform",
                                        "project", "crm-customer-service-repo",
                                        "projectPath", "CRM/customer-platform/crm-customer-service-repo",
                                        "defaultBranch", "main"
                                ),
                                "references", map("systems", List.of("crm-customer-service"))
                        ),
                        map(
                                "id", "crm-customer-shared-repo",
                                "name", "CRM Customer Shared Repository",
                                "git", map("projectPath", "CRM/customer-platform/crm-customer-shared")
                        )
                ),
                List.of(map(
                        "id", "crm-customer-service-scope",
                        "name", "CRM Customer Service Scope",
                        "target", map("type", "system", "id", "crm-customer-service"),
                        "repositories", List.of(
                                map(
                                        "repoId", "crm-customer-service-repo",
                                        "role", "primary",
                                        "priority", 1,
                                        "reason", "Main repository"
                                ),
                                map(
                                        "repoId", "crm-customer-shared-repo",
                                        "role", "supporting-library",
                                        "priority", 2,
                                        "reason", "Shared customer library"
                                )
                        ),
                        "limitations", List.of("Generated clients not included")
                ), map(
                        "id", "customer-profile-context-scope",
                        "name", "Customer Profile Context Scope",
                        "target", map("type", "bounded-context", "id", "customer-profile-context"),
                        "repositories", List.of(map(
                                "repoId", "crm-customer-service-repo",
                                "role", "primary",
                                "priority", 1
                        ))
                )),
                List.of(map(
                        "id", "customer-profile-context",
                        "references", map("systems", List.of("crm-customer-service"))
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
