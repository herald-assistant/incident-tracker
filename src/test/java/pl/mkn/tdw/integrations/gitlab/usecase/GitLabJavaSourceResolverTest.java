package pl.mkn.tdw.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFile;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitLabJavaSourceResolverTest {

    private final GitLabRepositoryPort repositoryPort = mock(GitLabRepositoryPort.class);
    private final GitLabEndpointUseCaseRepositoryContext repository = new GitLabEndpointUseCaseRepositoryContext(
            "CRM",
            "crm-customer-service",
            "main"
    );
    private final GitLabEndpointUseCaseSourceSession session = new GitLabEndpointUseCaseSourceSession(
            repositoryPort,
            repository
    );
    private final GitLabJavaSourceResolver resolver = new GitLabJavaSourceResolver();

    @Test
    void shouldBuildAstModelWithMultipleTopLevelAndNestedTypes() {
        var path = "src/main/java/com/example/crm/customer/CustomerProfileController.java";
        repositoryFiles(path);
        source(path, """
                package com.example.crm.customer;

                import com.example.crm.customer.application.CustomerService;
                import static com.example.crm.customer.mapper.CustomerMapper.INSTANCE;

                class CustomerController {
                    interface LocalCommand {
                    }
                }

                interface CustomerPort {
                }

                record CustomerSnapshot(String id) {
                }
                """);

        var astFile = resolver.astFile(session, path);

        assertTrue(astFile.parsed());
        assertEquals("com.example.crm.customer", astFile.packageName());
        assertEquals(List.of("com.example.crm.customer.application.CustomerService"), astFile.imports());
        assertEquals(List.of("com.example.crm.customer.mapper.CustomerMapper.INSTANCE"), astFile.staticImports());
        assertEquals(4, astFile.types().size());
        assertEquals(List.of(
                        "CustomerController",
                        "CustomerController.LocalCommand",
                        "CustomerPort",
                        "CustomerSnapshot"
                ),
                astFile.types().stream().map(GitLabJavaTypeDeclaration::relativeName).toList());
        assertEquals(GitLabJavaTypeKind.CLASS, astFile.types().get(0).kind());
        assertEquals(GitLabJavaTypeKind.INTERFACE, astFile.types().get(1).kind());
        assertEquals(GitLabJavaTypeKind.INTERFACE, astFile.types().get(2).kind());
        assertEquals(GitLabJavaTypeKind.RECORD, astFile.types().get(3).kind());
    }

    @Test
    void shouldResolveNestedInterfaceInSameFile() {
        var path = "src/main/java/com/example/crm/customer/CustomerRepositoryPort.java";
        repositoryFiles(path);
        source(path, """
                package com.example.crm.customer;

                interface CustomerRepositoryPort {
                    interface Query {
                    }

                    interface Command {
                    }
                }
                """);
        var astFile = resolver.astFile(session, path);

        var resolved = resolver.resolveType(session, astFile, "CustomerRepositoryPort.Query");

        assertEquals(GitLabJavaTypeResolutionKind.NESTED_TYPE, resolved.kind());
        assertEquals(GitLabEndpointUseCaseConfidence.HIGH, resolved.confidence());
        assertEquals(path, resolved.filePath());
        assertEquals("com.example.crm.customer.CustomerRepositoryPort.Query", resolved.qualifiedName());
        assertEquals(GitLabJavaTypeKind.INTERFACE, resolved.type().kind());
    }

    @Test
    void shouldResolveTypeByExactImport() {
        var controllerPath = "src/main/java/com/example/crm/customer/CustomerProfileController.java";
        var servicePath = "src/main/java/com/example/crm/customer/application/CustomerService.java";
        repositoryFiles(controllerPath, servicePath);
        source(controllerPath, """
                package com.example.crm.customer;

                import com.example.crm.customer.application.CustomerService;

                class CustomerController {
                    private final CustomerService customerService;
                }
                """);
        source(servicePath, """
                package com.example.crm.customer.application;

                class CustomerService {
                }
                """);
        var astFile = resolver.astFile(session, controllerPath);

        var resolved = resolver.resolveType(session, astFile, "CustomerService");

        assertEquals(GitLabJavaTypeResolutionKind.EXACT_IMPORT, resolved.kind());
        assertEquals(GitLabEndpointUseCaseConfidence.HIGH, resolved.confidence());
        assertEquals(servicePath, resolved.filePath());
        assertEquals("com.example.crm.customer.application.CustomerService", resolved.qualifiedName());
    }

    @Test
    void shouldResolveExactImportByTreeInMultiModuleRepository() {
        var rootRepository = new GitLabEndpointUseCaseRepositoryContext(
                "CRM",
                "crm-customer-service",
                "main"
        );
        var rootSession = new GitLabEndpointUseCaseSourceSession(repositoryPort, rootRepository);
        var controllerPath = "crm-customer-service/customer-adapter/src/main/java/com/example/crm/customer/CustomerProfileController.java";
        var servicePath = "crm-customer-service/customer-application/src/main/java/com/example/crm/customer/application/CustomerService.java";
        repositoryFiles(controllerPath, servicePath);
        source(controllerPath, """
                package com.example.crm.customer;

                import com.example.crm.customer.application.CustomerService;

                class CustomerController {
                    private final CustomerService customerService;
                }
                """);
        source(servicePath, """
                package com.example.crm.customer.application;

                class CustomerService {
                }
                """);
        var astFile = resolver.astFile(rootSession, controllerPath);

        var resolved = resolver.resolveType(rootSession, astFile, "CustomerService");

        assertEquals(GitLabJavaTypeResolutionKind.EXACT_IMPORT, resolved.kind());
        assertEquals(GitLabEndpointUseCaseConfidence.HIGH, resolved.confidence());
        assertEquals(servicePath, resolved.filePath());
        assertEquals("com.example.crm.customer.application.CustomerService", resolved.qualifiedName());
    }

    @Test
    void shouldResolveTypeFromSamePackageWithoutImport() {
        var controllerPath = "src/main/java/com/example/crm/customer/CustomerProfileController.java";
        var servicePath = "src/main/java/com/example/crm/customer/CustomerService.java";
        repositoryFiles(controllerPath, servicePath);
        source(controllerPath, """
                package com.example.crm.customer;

                class CustomerController {
                    private final CustomerService customerService;
                }
                """);
        source(servicePath, """
                package com.example.crm.customer;

                class CustomerService {
                }
                """);
        var astFile = resolver.astFile(session, controllerPath);

        var resolved = resolver.resolveType(session, astFile, "CustomerService");

        assertEquals(GitLabJavaTypeResolutionKind.SAME_PACKAGE, resolved.kind());
        assertEquals(GitLabEndpointUseCaseConfidence.MEDIUM, resolved.confidence());
        assertEquals(servicePath, resolved.filePath());
        assertEquals("com.example.crm.customer.CustomerService", resolved.qualifiedName());
    }

    @Test
    void shouldReturnAmbiguousWhenTreeLookupFindsManyFiles() {
        var controllerPath = "src/main/java/com/example/crm/customer/CustomerProfileController.java";
        var apiCustomerPath = "src/main/java/com/example/crm/customer/api/Customer.java";
        var domainCustomerPath = "src/main/java/com/example/crm/customer/domain/Customer.java";
        repositoryFiles(controllerPath, apiCustomerPath, domainCustomerPath);
        source(controllerPath, """
                package com.example.crm.customer;

                class CustomerController {
                    private Customer customer;
                }
                """);
        var astFile = resolver.astFile(session, controllerPath);

        var resolved = resolver.resolveType(session, astFile, "Customer");

        assertEquals(GitLabJavaTypeResolutionKind.AMBIGUOUS, resolved.kind());
        assertEquals(GitLabEndpointUseCaseConfidence.LOW, resolved.confidence());
        assertEquals(List.of(apiCustomerPath, domainCustomerPath), resolved.candidates());
    }

    @Test
    void shouldReturnUnresolvedWhenTypeCannotBeFoundQuickly() {
        var controllerPath = "src/main/java/com/example/crm/customer/CustomerProfileController.java";
        repositoryFiles(controllerPath);
        source(controllerPath, """
                package com.example.crm.customer;

                class CustomerController {
                    private MissingCustomerPolicy missingCustomerPolicy;
                }
                """);
        var astFile = resolver.astFile(session, controllerPath);

        var resolved = resolver.resolveType(session, astFile, "MissingCustomerPolicy");

        assertEquals(GitLabJavaTypeResolutionKind.UNRESOLVED, resolved.kind());
        assertTrue(resolved.candidates().isEmpty());
        assertEquals(List.of("No source file named MissingCustomerPolicy.java was found in repository tree."),
                resolved.limitations());
    }

    @Test
    void shouldSkipRepositoryLookupForResponseEntityImport() {
        var controllerPath = "src/main/java/com/example/crm/customer/CustomerProfileController.java";
        source(controllerPath, """
                package com.example.crm.customer;

                import org.springframework.http.ResponseEntity;

                class CustomerController {
                    ResponseEntity<CustomerResponse> getCustomer() {
                        return null;
                    }
                }
                """);
        var astFile = resolver.astFile(session, controllerPath);

        var resolved = resolver.resolveType(session, astFile, "ResponseEntity");

        assertEquals(GitLabJavaTypeResolutionKind.EXTERNAL_BOUNDARY, resolved.kind());
        assertEquals("org.springframework.http.ResponseEntity", resolved.qualifiedName());
        assertEquals(List.of("org.springframework.http.ResponseEntity"), resolved.candidates());
        assertEquals(List.of("ResponseEntity is a response wrapper, not a source lookup target."), resolved.limitations());
        verify(repositoryPort, never()).listRepositoryFiles("CRM", "crm-customer-service", "main", null);
    }

    @Test
    void shouldResolveLocalGeneratedApiInterfaceBeforeTreatingItAsExternal() {
        var controllerPath = "src/main/java/com/example/crm/customer/CustomerProfileController.java";
        var apiPath = "src/main/java/com/example/crm/generated/CustomerApi.java";
        repositoryFiles(controllerPath, apiPath);
        source(controllerPath, """
                package com.example.crm.customer;

                import com.example.crm.generated.CustomerApi;

                class CustomerController implements CustomerApi {
                }
                """);
        source(apiPath, """
                package com.example.crm.generated;

                interface CustomerApi {
                }
                """);
        var astFile = resolver.astFile(session, controllerPath);

        var resolved = resolver.resolveType(session, astFile, "CustomerApi");

        assertEquals(GitLabJavaTypeResolutionKind.EXACT_IMPORT, resolved.kind());
        assertEquals(apiPath, resolved.filePath());
        assertEquals("com.example.crm.generated.CustomerApi", resolved.qualifiedName());
    }

    @Test
    void shouldReturnTerminalBoundaryForInternalImportMissingInRepositoryTree() {
        var controllerPath = "src/main/java/com/example/crm/customer/CustomerProfileController.java";
        repositoryFiles(controllerPath);
        source(controllerPath, """
                package com.example.crm.customer;

                import com.example.crm.contract.SharedCustomerPolicy;

                class CustomerController {
                    private SharedCustomerPolicy sharedCustomerPolicy;
                }
                """);
        var astFile = resolver.astFile(session, controllerPath);

        var resolved = resolver.resolveType(session, astFile, "SharedCustomerPolicy");

        assertEquals(GitLabJavaTypeResolutionKind.EXTERNAL_BOUNDARY, resolved.kind());
        assertEquals("com.example.crm.contract.SharedCustomerPolicy", resolved.qualifiedName());
        assertEquals(List.of(
                "Type looks like internal/shared library class, but no matching source file was found in the selected repository tree."
        ), resolved.limitations());
    }

    private void repositoryFiles(String... paths) {
        var files = List.of(paths).stream()
                .map(path -> new GitLabRepositoryFile("CRM", "crm-customer-service", "main", path))
                .toList();
        when(repositoryPort.listRepositoryFiles("CRM", "crm-customer-service", "main", null))
                .thenReturn(files);
    }

    private void source(String path, String content) {
        when(repositoryPort.readFile("CRM", "crm-customer-service", "main", path, 120_000))
                .thenReturn(new GitLabRepositoryFileContent(
                        "CRM",
                        "crm-customer-service",
                        "main",
                        path,
                        content,
                        false
                ));
    }
}
