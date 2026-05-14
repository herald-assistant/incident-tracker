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
        assertEquals("primary", model.repositories().get(0).role());
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
        assertEquals("app-core-scope", model.scopes().get(0).scope().id());
        assertFalse(model.repositories().isEmpty());
    }

    @Test
    void shouldDeriveCodeSearchTargetsFromIncludedRepositoryReferences() {
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
                        "target", map("processes", List.of("core-process")),
                        "repositories", List.of(map(
                                "repoId", "app-repo",
                                "role", "primary",
                                "priority", 1,
                                "include", true
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

        var systemModel = builder.buildForEntity(catalog, "system", "app-core");
        var boundedContextModel = builder.buildForEntity(catalog, "bounded-context", "core-context");
        var termModel = builder.buildForEntity(catalog, "term", "core-term");

        assertEquals("repo-derived-scope", systemModel.scopes().get(0).scope().id());
        assertEquals("repo-derived-scope", boundedContextModel.scopes().get(0).scope().id());
        assertEquals("repo-derived-scope", termModel.scopes().get(0).scope().id());
        assertTrue(systemModel.scopes().get(0).targets().stream()
                .anyMatch(target -> target.type().equals("system") && target.id().equals("app-core")));
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
                        "target", map("systems", List.of("app-core")),
                        "repositories", List.of(map(
                                "repoId", "missing-repo",
                                "role", "primary",
                                "include", true
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
                        "target", map(
                                "systems", List.of("app-core"),
                                "processes", List.of("core-process"),
                                "boundedContexts", List.of("core-context"),
                                "integrations", List.of("partner-sync")
                        ),
                        "repositories", List.of(
                                map(
                                        "repoId", "app-repo",
                                        "role", "primary",
                                        "priority", 1,
                                        "include", true,
                                        "moduleIds", List.of("app-module"),
                                        "reason", "Main implementation"
                                ),
                                map(
                                        "repoId", "shared-repo",
                                        "role", "library",
                                        "priority", 2,
                                        "include", true,
                                        "reason", "Shared library"
                                )
                        ),
                        "packagePrefixes", List.of("com.example.scope"),
                        "classHints", List.of("ScopeEntryPoint"),
                        "endpointHints", List.of("/scope"),
                        "queueTopicHints", List.of("app.queue"),
                        "databaseHints", map(
                                "schemas", List.of("APP_SCHEMA"),
                                "tables", List.of("APP_TABLE"),
                                "entities", List.of("AppEntity")
                        ),
                        "workflowHints", map(
                                "workflowNames", List.of("AppFlow")
                        ),
                        "searchStrategy", map(
                                "priorityOrder", List.of("app-repo", "shared-repo"),
                                "includeSharedLibraries", true
                        ),
                        "limitations", List.of("Generated clients not included")
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
