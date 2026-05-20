package pl.mkn.incidenttracker.integrations.operationalcontext;

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
        var model = builder.buildForEntity(sampleCatalog(), "system", "app-core");

        assertEquals("operational-context.code-search", model.contract());
        assertEquals("app-core", model.analysisTarget().id());
        assertEquals(1, model.scopes().size());
        assertEquals("app-core-scope", model.scopes().get(0).scope().id());
        assertEquals(2, model.repositories().size());
        assertEquals("app-repo", model.repositories().get(0).repository().id());
        assertEquals("primary-implementation", model.repositories().get(0).role());
        assertEquals("Group/app", model.repositories().get(0).git().projectPath());
        assertTrue(model.repositories().get(0).modules().stream()
                .anyMatch(module -> module.id().equals("app-module")));
        assertTrue(model.aggregatedHints().packagePrefixes().contains("com.example.app"));
        assertTrue(model.aggregatedHints().classHints().contains("AppController"));
        assertTrue(model.aggregatedHints().endpointHints().contains("/app"));
        assertTrue(model.aggregatedHints().databaseHints().tables().contains("APP_TABLE"));
        assertTrue(model.aggregatedHints().databaseHints().migrations().contains("src/main/resources/db/changelog"));
        assertTrue(model.aggregatedHints().workflowHints().definitionPaths().contains("flows/app.bpmn"));
        assertTrue(model.validationFindings().isEmpty());
    }

    @Test
    void shouldBuildCodeSearchReadModelForBoundedContextAliasType() {
        var model = builder.buildForEntity(sampleCatalog(), "boundedContext", "core-context");

        assertEquals(1, model.scopes().size());
        assertEquals("core-context-scope", model.scopes().get(0).scope().id());
        assertFalse(model.repositories().isEmpty());
    }

    @Test
    void shouldUseOnlySemanticScopeTarget() {
        var catalog = OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(map("id", "core-process")),
                List.of(map("id", "app-core")),
                List.of(),
                List.of(map(
                        "id", "app-repo",
                        "references", map(
                                "systems", List.of("app-core"),
                                "boundedContexts", List.of("core-context"),
                                "terms", List.of("core-term")
                        )
                )),
                List.of(map(
                        "id", "repo-derived-scope",
                        "target", map("type", "process", "id", "core-process"),
                        "repositories", List.of(map(
                                "repoId", "app-repo",
                                "role", "primary-implementation",
                                "priority", 1
                        ))
                )),
                List.of(map("id", "core-context")),
                List.of(new OperationalContextDtos.OperationalContextGlossaryTerm(
                        "core-term",
                        "Core term",
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

        var processModel = builder.buildForEntity(catalog, "process", "core-process");
        var systemModel = builder.buildForEntity(catalog, "system", "app-core");

        assertEquals("repo-derived-scope", processModel.scopes().get(0).scope().id());
        assertEquals("process", processModel.scopes().get(0).target().type());
        assertEquals("core-process", processModel.scopes().get(0).target().id());
        assertTrue(systemModel.scopes().isEmpty());
    }

    @Test
    void shouldResolveScopeDirectly() {
        var model = builder.buildForEntity(sampleCatalog(), "codeSearchScope", "app-core-scope");

        assertEquals("app-core-scope", model.analysisTarget().id());
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
                List.of(map("id", "app-core")),
                List.of(),
                List.of(),
                List.of(map(
                        "id", "broken-scope",
                        "target", map("type", "system", "id", "app-core"),
                        "repositories", List.of(map(
                                "repoId", "missing-repo",
                                "role", "primary-implementation"
                        ))
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        );

        var model = builder.buildForEntity(catalog, "system", "app-core");

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
                        "id", "core-process",
                        "participants", map("primarySystems", List.of("app-core"))
                )),
                List.of(map("id", "app-core"), map("id", "missing-scope-system")),
                List.of(map("id", "partner-sync")),
                List.of(
                        map(
                                "id", "app-repo",
                                "name", "App Repository",
                                "git", map(
                                        "provider", "gitlab",
                                        "group", "Group",
                                        "project", "app",
                                        "projectPath", "Group/app",
                                        "defaultBranch", "main"
                                ),
                                "references", map("systems", List.of("app-core")),
                                "sourceLayout", map(
                                        "buildTool", "maven",
                                        "sourceRoots", List.of("src/main/java"),
                                        "resourceRoots", List.of("src/main/resources"),
                                        "databaseMigrationPaths", List.of("src/main/resources/db/changelog"),
                                        "workflowDefinitionPaths", List.of("flows/app.bpmn")
                                ),
                                "packagePrefixes", List.of("com.example.app"),
                                "classHints", List.of("AppService"),
                                "endpointHints", List.of("/app"),
                                "modules", List.of(map(
                                        "id", "app-module",
                                        "name", "App Module",
                                        "source", map(
                                                "paths", List.of("app-module/src/main/java"),
                                                "packages", List.of("com.example.app.module")
                                        ),
                                        "matchSignals", map("strong", map(
                                                "packagePrefixes", List.of("com.example.app.module"),
                                                "classHints", List.of("AppController")
                                        ))
                                ))
                        ),
                        map(
                                "id", "shared-repo",
                                "name", "Shared Repository",
                                "git", map("projectPath", "Group/shared"),
                                "packagePrefixes", List.of("com.example.shared"),
                                "classHints", List.of("SharedClient")
                        )
                ),
                List.of(map(
                        "id", "app-core-scope",
                        "name", "App Core Scope",
                        "target", map("type", "system", "id", "app-core"),
                        "repositories", List.of(
                                map(
                                        "repoId", "app-repo",
                                        "role", "primary-implementation",
                                        "priority", 1,
                                        "moduleIds", List.of("app-module"),
                                        "reason", "Main implementation"
                                ),
                                map(
                                        "repoId", "shared-repo",
                                        "role", "supporting-library",
                                        "priority", 2,
                                        "reason", "Shared library"
                                )
                        ),
                        "hints", map(
                                "packagePrefixes", List.of("com.example.scope"),
                                "classHints", List.of("ScopeEntryPoint"),
                                "endpointHints", List.of("/scope"),
                                "queueTopicHints", List.of("app.queue"),
                                "database", map(
                                        "schemas", List.of("APP_SCHEMA"),
                                        "tables", List.of("APP_TABLE"),
                                        "entities", List.of("AppEntity")
                                ),
                                "workflow", map(
                                        "workflowNames", List.of("AppFlow")
                                )
                        ),
                        "traversal", map(
                                "rules", List.of("Read app-repo before shared-repo."),
                                "expandWhen", List.of("Expand to shared-repo when shared predicates are referenced.")
                        ),
                        "limitations", List.of("Generated clients not included")
                ), map(
                        "id", "core-context-scope",
                        "name", "Core Context Scope",
                        "target", map("type", "bounded-context", "id", "core-context"),
                        "repositories", List.of(map(
                                "repoId", "app-repo",
                                "role", "primary-implementation",
                                "priority", 1
                        )),
                        "hints", map(
                                "packagePrefixes", List.of("com.example.app")
                        )
                )),
                List.of(map(
                        "id", "core-context",
                        "references", map("systems", List.of("app-core"))
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
