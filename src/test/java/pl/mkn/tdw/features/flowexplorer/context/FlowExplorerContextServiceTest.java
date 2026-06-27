package pl.mkn.tdw.features.flowexplorer.context;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseConfidence;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseContextRequest;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseContextResult;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseContextService;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseEndpointContext;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseFileCandidate;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseFileRole;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseLimits;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseMethodCandidate;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseRelation;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseRelationKind;
import pl.mkn.tdw.integrations.gitlab.usecase.GitLabJavaTypeKind;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowExplorerContextServiceTest {

    private final FlowExplorerRepositoryScopeService repositoryScopeService = mock(FlowExplorerRepositoryScopeService.class);
    private final GitLabEndpointUseCaseContextService gitLabEndpointUseCaseContextService =
            mock(GitLabEndpointUseCaseContextService.class);
    private final FlowExplorerSnippetCardService snippetCardService = mock(FlowExplorerSnippetCardService.class);
    private final FlowExplorerOpenApiContractService openApiContractService = mock(FlowExplorerOpenApiContractService.class);
    private final FlowExplorerContextService service = new FlowExplorerContextService(
            repositoryScopeService,
            gitLabEndpointUseCaseContextService,
            snippetCardService,
            openApiContractService
    );

    @Test
    void shouldBuildCompactFlowManifestForResolvedEndpoint() {
        when(repositoryScopeService.resolve("catalog-core", "feature/FLOW-42"))
                .thenReturn(scope());
        when(gitLabEndpointUseCaseContextService.buildContext(
                org.mockito.ArgumentMatchers.eq("platform/backend"),
                org.mockito.ArgumentMatchers.eq("feature/FLOW-42"),
                org.mockito.ArgumentMatchers.any(GitLabEndpointUseCaseContextRequest.class)
        )).thenReturn(resolvedUseCaseContext());
        when(snippetCardService.buildSnippetCards(
                org.mockito.ArgumentMatchers.eq("platform/backend"),
                org.mockito.ArgumentMatchers.eq("feature/FLOW-42"),
                org.mockito.ArgumentMatchers.any(FlowExplorerRepositoryContext.class),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.eq(pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerAnalysisGoal.DEEP_DISCOVERY),
                org.mockito.ArgumentMatchers.eq(List.of(pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerFocusArea.PERSISTENCE))
        )).thenReturn(new FlowExplorerSnippetCardResult(
                List.of(snippetCard()),
                List.of("Snippet card budget reached before all eligible flow nodes were embedded."),
                true,
                0
        ));
        when(openApiContractService.buildEndpointContracts(
                org.mockito.ArgumentMatchers.eq("platform/backend"),
                org.mockito.ArgumentMatchers.eq("feature/FLOW-42"),
                org.mockito.ArgumentMatchers.any(FlowExplorerRepositoryContext.class),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any(FlowExplorerContextRequest.class)
        )).thenReturn(new FlowExplorerOpenApiContractResult(
                List.of(),
                List.of("OpenAPI contract not detected.")
        ));

        var snapshot = service.buildContext(new FlowExplorerContextRequest(
                "catalog-core",
                "catalog-api:GET /api/catalog/{id}",
                null,
                null,
                "feature/FLOW-42",
                pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerAnalysisGoal.DEEP_DISCOVERY,
                List.of(pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerFocusArea.PERSISTENCE)
        ));

        assertEquals("catalog-core", snapshot.systemId());
        assertEquals("feature/FLOW-42", snapshot.resolvedRef());
        assertTrue(snapshot.coverage().endpointResolved());
        assertEquals(1, snapshot.coverage().attemptedRepositoryCount());
        assertEquals(2, snapshot.coverage().flowNodeCount());
        assertEquals(2, snapshot.coverage().methodCount());
        assertEquals("CatalogController", snapshot.endpoint().controllerClass());
        assertEquals("catalog-api", snapshot.repositories().get(0).projectName());
        assertTrue(snapshot.repositories().get(0).selected());

        var controllerNode = snapshot.flowNodes().get(0);
        assertEquals("CONTROLLER", controllerNode.role());
        assertEquals("src/main/java/com/example/catalog/CatalogController.java", controllerNode.filePath());
        assertEquals("getCatalog", controllerNode.methods().get(0).methodName());
        assertEquals(12, controllerNode.methods().get(0).lineStart());
        assertEquals(24, controllerNode.methods().get(0).lineEnd());
        assertEquals("Endpoint handler and local controller flow.", controllerNode.reason());
        assertEquals(1, snapshot.relations().size());
        assertEquals("ENDPOINT_HANDLER", snapshot.relations().get(0).kind());
        assertEquals(1, snapshot.snippetCards().size());
        assertEquals("src/main/java/com/example/catalog/CatalogController.java", snapshot.snippetCards().get(0).filePath());
        assertEquals(1, snapshot.coverage().snippetCardCount());
        assertTrue(snapshot.coverage().snippetBudgetReached());
        assertTrue(snapshot.openApiEndpointContracts().isEmpty());
        assertTrue(snapshot.limitations().contains("OpenAPI contract not detected."));

        var requestCaptor = ArgumentCaptor.forClass(GitLabEndpointUseCaseContextRequest.class);
        verify(gitLabEndpointUseCaseContextService).buildContext(
                org.mockito.ArgumentMatchers.eq("platform/backend"),
                org.mockito.ArgumentMatchers.eq("feature/FLOW-42"),
                requestCaptor.capture()
        );
        assertEquals("catalog-api", requestCaptor.getValue().projectName());
        assertEquals("GET /api/catalog/{id}", requestCaptor.getValue().endpointId());
        assertEquals(GitLabEndpointUseCaseContextRequest.MAX_MAX_FILES, requestCaptor.getValue().maxFiles());
    }

    @Test
    void shouldReturnUnresolvedCoverageWithoutFlowNodes() {
        when(repositoryScopeService.resolve("catalog-core", null)).thenReturn(scope());
        when(gitLabEndpointUseCaseContextService.buildContext(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(GitLabEndpointUseCaseContextRequest.class)
        )).thenReturn(unresolvedUseCaseContext());

        var snapshot = service.buildContext(new FlowExplorerContextRequest(
                "catalog-core",
                null,
                "GET",
                "/missing",
                null,
                null,
                List.of()
        ));

        assertFalse(snapshot.coverage().endpointResolved());
        assertTrue(snapshot.flowNodes().isEmpty());
        assertTrue(snapshot.relations().isEmpty());
        assertEquals(1, snapshot.coverage().unresolvedReferenceCount());
        assertTrue(snapshot.limitations().stream()
                .anyMatch(limitation -> limitation.contains("Endpoint could not be resolved")));
    }

    @Test
    void shouldPropagateMissingSystemFromScopeResolver() {
        when(repositoryScopeService.resolve("missing-system", null))
                .thenThrow(new FlowExplorerSystemNotFoundException("missing-system"));

        assertThrows(
                FlowExplorerSystemNotFoundException.class,
                () -> service.buildContext(new FlowExplorerContextRequest(
                        "missing-system",
                        "GET /api/catalog/{id}",
                        null,
                        null,
                        null,
                        null,
                        List.of()
                ))
        );
    }

    private static FlowExplorerRepositoryScope scope() {
        var catalog = catalog();
        return new FlowExplorerRepositoryScope(
                catalog.systems().get(0),
                "feature/FLOW-42",
                "feature/FLOW-42",
                "platform/backend",
                1,
                List.of(new FlowExplorerRepositoryScopeRepository(
                        "catalog-api",
                        "catalog-api",
                        "platform/backend/catalog-api",
                        null,
                        "system.references.repositories",
                        catalog.repositories().get(0)
                )),
                List.of()
        );
    }

    private static GitLabEndpointUseCaseContextResult resolvedUseCaseContext() {
        return new GitLabEndpointUseCaseContextResult(
                null,
                new GitLabEndpointUseCaseEndpointContext(
                        "GET /api/catalog/{id}",
                        List.of("GET"),
                        "/api/catalog/{id}",
                        "/api/catalog/{id}",
                        "CatalogController",
                        "getCatalog",
                        "src/main/java/com/example/catalog/CatalogController.java",
                        12,
                        24,
                        List.of(),
                        List.of("CatalogResponse"),
                        List.of("RestController", "GetMapping"),
                        GitLabEndpointUseCaseConfidence.HIGH,
                        List.of(),
                        List.of()
                ),
                List.of(
                        file(
                                "src/main/java/com/example/catalog/CatalogController.java",
                                GitLabEndpointUseCaseFileRole.CONTROLLER,
                                1,
                                "CatalogController",
                                "getCatalog",
                                12,
                                24,
                                "Endpoint handler and local controller flow."
                        ),
                        file(
                                "src/main/java/com/example/catalog/CatalogService.java",
                                GitLabEndpointUseCaseFileRole.USE_CASE_SERVICE,
                                2,
                                "CatalogService",
                                "getCatalog",
                                42,
                                55,
                                "Injected service method called by controller."
                        )
                ),
                List.of(new GitLabEndpointUseCaseRelation(
                        "GET /api/catalog/{id}",
                        "CatalogController#getCatalog",
                        GitLabEndpointUseCaseRelationKind.ENDPOINT_HANDLER,
                        GitLabEndpointUseCaseConfidence.HIGH,
                        "Endpoint inventory resolved this handler method."
                )),
                List.of(),
                List.of(),
                List.of("catalog-api:src/main/java/com/example/catalog/CatalogController.java"),
                GitLabEndpointUseCaseLimits.defaults(),
                GitLabEndpointUseCaseConfidence.HIGH
        );
    }

    private static GitLabEndpointUseCaseContextResult unresolvedUseCaseContext() {
        return new GitLabEndpointUseCaseContextResult(
                null,
                null,
                List.of(),
                List.of(),
                List.of(new pl.mkn.tdw.integrations.gitlab.usecase.GitLabEndpointUseCaseUnresolvedReference(
                        "GET /missing",
                        null,
                        "Endpoint could not be resolved before use-case traversal: NOT_FOUND.",
                        List.of("endpointId", "httpMethod", "endpointPath"),
                        List.of()
                )),
                List.of("Endpoint could not be resolved before use-case traversal: NOT_FOUND."),
                List.of(),
                GitLabEndpointUseCaseLimits.defaults(),
                GitLabEndpointUseCaseConfidence.LOW
        );
    }

    private static FlowExplorerSnippetCard snippetCard() {
        return new FlowExplorerSnippetCard(
                "catalog-api:src/main/java/com/example/catalog/CatalogController.java:L9-L27",
                "catalog-api",
                "src/main/java/com/example/catalog/CatalogController.java",
                "CONTROLLER",
                List.of(new FlowExplorerFlowMethod("getCatalog", 12, 24)),
                9,
                27,
                9,
                27,
                120,
                false,
                "Endpoint handler and local controller flow.",
                "// file: src/main/java/com/example/catalog/CatalogController.java\npublic CatalogResponse getCatalog() {}",
                0,
                List.of()
        );
    }

    private static GitLabEndpointUseCaseFileCandidate file(
            String path,
            GitLabEndpointUseCaseFileRole role,
            int priority,
            String typeName,
            String methodName,
            int lineStart,
            int lineEnd,
            String reason
    ) {
        return new GitLabEndpointUseCaseFileCandidate(
                path,
                role,
                priority,
                List.of(methodName),
                List.of(new GitLabEndpointUseCaseMethodCandidate(
                        path,
                        typeName,
                        typeName,
                        "com.example.catalog." + typeName,
                        GitLabJavaTypeKind.CLASS,
                        methodName,
                        null,
                        lineStart,
                        lineEnd,
                        0,
                        List.of(),
                        List.of(),
                        "CatalogResponse",
                        List.of("public"),
                        role,
                        priority,
                        priority,
                        reason,
                        GitLabEndpointUseCaseConfidence.HIGH
                )),
                reason,
                GitLabEndpointUseCaseConfidence.HIGH
        );
    }

    private static OperationalContextCatalog catalog() {
        return OperationalContextDtos.catalogFromRaw(
                List.of(),
                List.of(),
                List.of(map(
                        "id", "catalog-core",
                        "name", "Catalog Core",
                        "kind", "internal-application",
                        "references", map("repositories", List.of("catalog-api"))
                )),
                List.of(),
                List.of(map(
                        "id", "catalog-api",
                        "name", "Catalog API",
                        "git", map(
                                "provider", "gitlab",
                                "group", "platform/backend",
                                "projectPath", "platform/backend/catalog-api"
                        )
                )),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "index"
        );
    }

    private static Map<String, Object> map(Object... values) {
        var map = new java.util.LinkedHashMap<String, Object>();
        for (var index = 0; index + 1 < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }
}
