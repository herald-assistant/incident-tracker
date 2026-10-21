package pl.mkn.tdw.integrations.gitlab.service;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryEndpointListRequest;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryEndpointListResult;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryFile;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryFileCandidate;
import pl.mkn.tdw.integrations.gitlab.contract.GitLabRepositoryFileContent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.mkn.tdw.testsupport.integrations.GitLabIntegrationTestCreator.endpointService;

class GitLabRepositoryEndpointHierarchyTest {

    private static final String CONTROLLER = "app/src/main/java/com/example/crm/web/CustomerController.java";
    private static final String CONTRACT = "api/src/main/java/com/example/crm/contract/CustomerApi.java";

    @Test
    void shouldIgnoreCommentedMappingsAndNestedHelpers() {
        var service = endpointService(mock(GitLabRepositoryPort.class));
        var endpoints = service.parseEndpointFile("crm-customers", CONTROLLER, """
                package com.example.crm.web;
                @RestController class CustomerController {
                    /* @GetMapping("/removed")
                       public String removed() { return "removed"; } */
                    static class Helper {
                        @GetMapping("/helper") public String helper() { return "helper"; }
                    }
                    @GetMapping("/crm/customers") public String customers() { return "customers"; }
                }
                """, List.of());
        assertEquals(1, endpoints.size());
        assertEquals("customers", endpoints.get(0).handlerMethod());
        assertEquals("com.example.crm.web.CustomerController", endpoints.get(0).controllerClass());
    }

    @Test
    void shouldBindInterfaceMappingToCorrectOverloadAndKeepBothSources() {
        var result = inventory(Map.of(CONTROLLER, """
                package com.example.crm.web;
                import com.example.crm.contract.CustomerApi;
                @RestController class CustomerController implements CustomerApi {
                    public String getCustomer(String customerId) { return customerId; }
                    public String getCustomer(long customerId) { return "other"; }
                }
                """, CONTRACT, """
                package com.example.crm.contract;
                @RequestMapping("/crm/customers")
                public interface CustomerApi {
                    @Operation(summary = "Customer details")
                    @GetMapping("/{customerId}")
                    String getCustomer(@PathVariable("customerId") String customerId);
                }
                """));
        assertEquals(1, result.endpoints().size());
        var endpoint = result.endpoints().get(0);
        assertEquals("/crm/customers/{customerId}", endpoint.path());
        assertEquals(CONTROLLER, endpoint.filePath());
        assertEquals(4, endpoint.lineStart());
        assertEquals("Customer details", endpoint.documentation().summary());
        assertEquals("customerId", endpoint.documentation().parameters().get(0).name());
        assertTrue(endpoint.suggestedNextReads().stream().anyMatch(read -> read.contains(CONTRACT)));
        assertTrue(endpoint.suggestedNextReads().stream().anyMatch(read -> read.contains(CONTROLLER)));
        assertEquals(List.of(), result.limitations());
    }

    @Test
    void shouldKeepInterfaceDocumentationWhenHandlerOverridesMapping() {
        var result = inventory(Map.of(CONTROLLER, """
                package com.example.crm.web;
                import com.example.crm.contract.CustomerApi;
                @RestController class CustomerController implements CustomerApi {
                    @GetMapping("/active") public String getCustomer(String id) { return id; }
                }
                """, CONTRACT, """
                package com.example.crm.contract;
                @RequestMapping("/crm/customers")
                public interface CustomerApi extends CustomerReadApi {}
                interface CustomerReadApi {
                    @Operation(summary = "Customer details")
                    @GetMapping("/{id}")
                    String getCustomer(@Parameter(description = "Customer identifier") @PathVariable String id);
                }
                """));
        assertEquals(1, result.endpoints().size());
        var endpoint = result.endpoints().get(0);
        assertEquals("/crm/customers/active", endpoint.path());
        assertEquals("Customer details", endpoint.documentation().summary());
        assertEquals("Customer identifier", endpoint.documentation().parameters().get(0).description());
    }

    @Test
    void shouldUseConcreteHandlerInheritedFromBaseClass() {
        var base = "app/src/main/java/com/example/crm/web/BaseCustomerController.java";
        var result = inventory(Map.of(CONTROLLER, """
                package com.example.crm.web;
                import com.example.crm.contract.CustomerApi;
                @RestController class CustomerController extends BaseCustomerController implements CustomerApi {}
                """, base, """
                package com.example.crm.web;
                class BaseCustomerController {
                    public String getCustomer(String id) { return id; }
                }
                """, CONTRACT, """
                package com.example.crm.contract;
                public interface CustomerApi {
                    @GetMapping("/crm/customers/{id}") String getCustomer(@PathVariable String id);
                }
                """));
        assertEquals(1, result.endpoints().size());
        assertEquals(base, result.endpoints().get(0).filePath());
        assertEquals("com.example.crm.web.CustomerController", result.endpoints().get(0).controllerClass());
    }

    @Test
    void shouldUseMappedDefaultInterfaceMethod() {
        var result = inventory(Map.of(CONTROLLER, """
                package com.example.crm.web;
                import com.example.crm.contract.CustomerApi;
                @RestController class CustomerController implements CustomerApi {}
                """, CONTRACT, """
                package com.example.crm.contract;
                public interface CustomerApi {
                    @GetMapping("/crm/customers") default String customers() { return "customers"; }
                }
                """));
        assertEquals(1, result.endpoints().size());
        assertEquals(CONTRACT, result.endpoints().get(0).filePath());
    }

    @Test
    void shouldResolveWildcardInterfaceImport() {
        var result = inventory(Map.of(CONTROLLER, """
                package com.example.crm.web;
                import com.example.crm.contract.*;
                @RestController class CustomerController implements CustomerApi {
                    public String customers() { return "customers"; }
                }
                """, CONTRACT, """
                package com.example.crm.contract;
                public interface CustomerApi { @GetMapping("/crm/customers") String customers(); }
                """));
        assertEquals(1, result.endpoints().size());
        assertEquals(List.of(), result.limitations());
    }

    @Test
    void shouldSubstituteSimpleGenericContractParameter() {
        var result = inventory(Map.of(CONTROLLER, """
                package com.example.crm.web;
                import com.example.crm.contract.CustomerApi;
                @RestController class CustomerController implements CustomerApi<String> {
                    public String getCustomer(String id) { return id; }
                }
                """, CONTRACT, """
                package com.example.crm.contract;
                public interface CustomerApi<T> {
                    @GetMapping("/crm/customers/{id}") String getCustomer(@PathVariable T id);
                }
                """));
        assertEquals(1, result.endpoints().size());
        assertEquals(List.of(), result.limitations());
        assertEquals(List.of("String id"), result.endpoints().get(0).requestTypes());
        assertEquals("java.lang.String", result.endpoints().get(0).documentation().parameters().get(0).type());
    }

    @Test
    void shouldNotBindUnmappedLocalInterfaceToUnrelatedOpenApi() {
        var result = inventory(Map.of(CONTROLLER, """
                package com.example.crm.web;
                import com.example.crm.contract.CustomerApi;
                @RestController class CustomerController implements CustomerApi {
                    public String customers() { return "customers"; }
                }
                """, CONTRACT, """
                package com.example.crm.contract;
                public interface CustomerApi { String customers(); }
                """, "api/src/main/resources/customer-api.yaml", """
                openapi: 3.0.1
                paths:
                  /crm/customers:
                    get:
                      tags: [Customer]
                      operationId: customers
                """));
        assertEquals(List.of(), result.endpoints());
    }

    @Test
    void shouldRankControllerImplementationAheadOfDtoInTreeFallback() {
        var result = inventory(Map.of(
                "src/main/java/com/example/crm/api/CustomerDto.java", """
                        package com.example.crm.api;
                        public record CustomerDto(String id) {}
                        """,
                "src/main/java/com/example/crm/customer/CustomerControllerGeneratedApi.java", """
                        package com.example.crm.customer;
                        @RestController class CustomerControllerGeneratedApi {
                            @GetMapping("/crm/customers") public String customers() { return "customers"; }
                        }
                        """), false, List.of(), 1, Set.of());
        assertEquals(2, result.candidateFileCount());
        assertEquals(1, result.scannedFileCount());
        assertTrue(result.scannedFileLimitReached());
        assertEquals(1, result.endpoints().size());
    }

    @Test
    void shouldPreferSamePackageContractOverWildcardImportForOpenApiBinding() {
        var result = inventory(Map.of(CONTROLLER, """
                package com.example.crm.web;
                import java.util.*;
                @RestController class CustomerController implements CustomerApi {
                    public String customers() { return "customers"; }
                }
                """, "app/src/main/java/com/example/crm/web/CustomerApi.java", """
                package com.example.crm.web;
                public interface CustomerApi { String customers(); }
                """, "api/src/main/resources/customer-api.yaml", """
                openapi: 3.0.1
                paths:
                  /crm/customers:
                    get:
                      tags: [Customer]
                      operationId: customers
                """));
        assertEquals(List.of(), result.endpoints());
    }

    @Test
    void shouldRespectPathPrefixesWhenLoadingContract() {
        var result = inventory(customerSources(), true, List.of("app"), 120, Set.of());
        assertEquals(List.of(), result.endpoints());
        assertTrue(result.limitations().stream().anyMatch(value -> value.contains("interface source unavailable")));
    }

    @Test
    void shouldReportAmbiguousContractAcrossModules() {
        var sources = new LinkedHashMap<>(customerSources());
        sources.put("other/" + CONTRACT, sources.get(CONTRACT));
        var result = inventory(sources, true, List.of(), 1, Set.of());
        assertEquals(List.of(), result.endpoints());
        assertTrue(result.limitations().stream().anyMatch(value -> value.contains("Ambiguous endpoint interface")));
    }

    @Test
    void shouldReportAmbiguousContractAlreadyLoadedBySearch() {
        var sources = new LinkedHashMap<>(customerSources());
        sources.put("other/" + CONTRACT, sources.get(CONTRACT));
        var result = inventory(sources);
        assertEquals(List.of(), result.endpoints());
        assertTrue(result.limitations().stream().anyMatch(value -> value.contains("Ambiguous endpoint interface")));
    }

    @Test
    void shouldReportUnresolvedRawGenericContract() {
        var result = inventory(Map.of(CONTROLLER, """
                package com.example.crm.web;
                import com.example.crm.contract.CustomerApi;
                @RestController class CustomerController implements CustomerApi {
                    public String getCustomer(Object id) { return "customer"; }
                }
                """, CONTRACT, """
                package com.example.crm.contract;
                public interface CustomerApi<T> {
                    @GetMapping("/crm/customers/{id}") String getCustomer(@PathVariable T id);
                }
                """));
        assertEquals(List.of(), result.endpoints());
        assertTrue(result.limitations().stream().anyMatch(value -> value.contains("Unresolved endpoint handler signature")));
    }

    @Test
    void shouldReportUnresolvedParameterWithMultipleWildcardImports() {
        var result = inventory(Map.of(CONTROLLER, """
                package com.example.crm.web;
                import com.example.crm.contract.*;
                import com.example.crm.model.*;
                @RestController class CustomerController {
                    @GetMapping("/crm/customers") public String customers(CustomerFilter filter) { return "customers"; }
                }
                """));
        assertEquals(List.of(), result.endpoints());
        assertTrue(result.limitations().stream().anyMatch(value -> value.contains("Unresolved endpoint handler signature")));
    }

    @Test
    void shouldReportIncompleteContractInsteadOfPublishingEndpoint() {
        var result = inventory(customerSources(), true, List.of(), 1, Set.of(CONTRACT));
        assertEquals(List.of(), result.endpoints());
        assertTrue(result.limitations().stream().anyMatch(value -> value.contains("Incomplete endpoint interface")));
    }

    @Test
    void shouldReportParseFailure() {
        var result = inventory(Map.of(CONTROLLER, "@RestController class CustomerController {"));
        assertEquals(List.of(), result.endpoints());
        assertTrue(result.limitations().stream().anyMatch(value -> value.contains("Could not parse Java")));
    }

    @Test
    void shouldKeepDirectMappingWhenWildcardContractLookupFails() {
        var result = inventory(Map.of(CONTROLLER, """
                package com.example.crm.web;
                import com.example.crm.contract.*;
                @RestController class CustomerController implements CustomerApi {
                    @GetMapping("/crm/customers") public String customers() { return "customers"; }
                }
                """), true, List.of(), 120, Set.of(), true);
        assertEquals(1, result.endpoints().size());
        assertTrue(result.limitations().stream().anyMatch(value -> value.contains("Could not resolve endpoint interface import")));
    }

    @Test
    void shouldBoundInheritanceDepth() {
        var sources = new LinkedHashMap<String, String>();
        sources.put(CONTROLLER, """
                package com.example.crm.web;
                import com.example.crm.contract.CustomerApi0;
                @RestController class CustomerController implements CustomerApi0 {
                    public String customers() { return "customers"; }
                }
                """);
        for (var index = 0; index < 10; index++) {
            sources.put("api/src/main/java/com/example/crm/contract/CustomerApi" + index + ".java",
                    "package com.example.crm.contract; interface CustomerApi" + index
                            + (index < 9 ? " extends CustomerApi" + (index + 1) + " {}"
                            : " { @GetMapping(\"/crm/customers\") String customers(); }"));
        }
        var result = inventory(sources, true, List.of(), 1, Set.of());
        assertEquals(List.of(), result.endpoints());
        assertTrue(result.limitations().stream().anyMatch(value -> value.contains("depth limit")));
    }

    @Test
    void shouldBoundTypeLookups() {
        var sources = new LinkedHashMap<String, String>();
        var names = new java.util.ArrayList<String>();
        for (var index = 0; index < 65; index++) {
            var name = "CustomerApi" + index;
            names.add("com.example.crm.contract." + name);
            sources.put("api/src/main/java/com/example/crm/contract/" + name + ".java",
                    "package com.example.crm.contract; interface " + name + " {}");
        }
        sources.put(CONTROLLER, "package com.example.crm.web; @RestController class CustomerController implements "
                + String.join(",", names) + " {}");
        var result = inventory(sources, true, List.of(), 1, Set.of());
        assertTrue(result.limitations().stream().anyMatch(value -> value.contains("type lookup limit")));
        assertEquals(1, result.scannedFileCount());
    }

    private Map<String, String> customerSources() {
        return Map.of(CONTROLLER, """
                package com.example.crm.web;
                import com.example.crm.contract.CustomerApi;
                @RestController class CustomerController implements CustomerApi {
                    public String customers() { return "customers"; }
                }
                """, CONTRACT, """
                package com.example.crm.contract;
                public interface CustomerApi { @GetMapping("/crm/customers") String customers(); }
                """);
    }

    private GitLabRepositoryEndpointListResult inventory(Map<String, String> sources) {
        return inventory(sources, true, List.of(), 120, Set.of());
    }

    private GitLabRepositoryEndpointListResult inventory(
            Map<String, String> sources, boolean searchAvailable, List<String> pathPrefixes,
            int maxScannedFiles, Set<String> truncatedPaths
    ) {
        return inventory(sources, searchAvailable, pathPrefixes, maxScannedFiles, truncatedPaths, false);
    }

    private GitLabRepositoryEndpointListResult inventory(
            Map<String, String> sources, boolean searchAvailable, List<String> pathPrefixes,
            int maxScannedFiles, Set<String> truncatedPaths, boolean failTree
    ) {
        var repository = mock(GitLabRepositoryPort.class);
        when(repository.searchRepositoryFilesByContent(anyString(), anyString(), anyString(), any(), anyInt()))
                .thenAnswer(invocation -> {
                    List<String> terms = invocation.getArgument(3);
                    return sources.entrySet().stream()
                            .filter(entry -> searchAvailable && terms.stream().anyMatch(entry.getValue()::contains))
                            .map(entry -> new GitLabRepositoryFileCandidate("CRM", "crm-customers", "main",
                                    entry.getKey(), "source signal", 1)).toList();
                });
        when(repository.listRepositoryFiles(anyString(), anyString(), anyString(), any()))
                .thenAnswer(invocation -> {
                    if (failTree) {
                        throw new IllegalStateException("CRM repository tree unavailable");
                    }
                    String prefix = invocation.getArgument(3);
                    return sources.keySet().stream().filter(path -> prefix == null || path.startsWith(prefix + "/"))
                            .map(path -> new GitLabRepositoryFile("CRM", "crm-customers", "main", path)).toList();
                });
        when(repository.readFile(anyString(), anyString(), anyString(), anyString(), anyInt()))
                .thenAnswer(invocation -> {
                    String path = invocation.getArgument(3);
                    if (!sources.containsKey(path) || !pathPrefixes.isEmpty()
                            && pathPrefixes.stream().noneMatch(prefix -> path.startsWith(prefix + "/"))) {
                        throw new AssertionError("Unexpected source read: " + path);
                    }
                    return new GitLabRepositoryFileContent("CRM", "crm-customers", "main", path,
                            sources.get(path), truncatedPaths.contains(path));
                });
        return endpointService(repository).listEndpoints(new GitLabRepositoryEndpointListRequest(
                "CRM", "crm-customers", "main", null, null, pathPrefixes, maxScannedFiles, false));
    }
}
