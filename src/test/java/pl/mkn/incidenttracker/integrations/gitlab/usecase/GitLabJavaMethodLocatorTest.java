package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFile;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryPort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitLabJavaMethodLocatorTest {

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
    private final GitLabJavaSourceResolver sourceResolver = new GitLabJavaSourceResolver();
    private final GitLabJavaMethodLocator locator = new GitLabJavaMethodLocator();

    @Test
    void shouldResolveEndpointHandlerByQualifiedTypeName() {
        var astFile = astFile("src/main/java/com/example/crm/product/DataProductController.java", """
                package com.example.crm.product;

                import org.springframework.http.ResponseEntity;

                class DataProductController {
                    public ResponseEntity<ProductWebModel> updateProduct(String ttaId, ProductWebModel webModel) {
                        return ResponseEntity.ok(webModel);
                    }
                }
                """);

        var resolution = locator.resolveMethod(
                astFile,
                "com.example.crm.product.DataProductController",
                "updateProduct",
                2
        );

        assertEquals(GitLabJavaMethodResolutionStatus.RESOLVED, resolution.status());
        assertEquals(GitLabEndpointUseCaseConfidence.HIGH, resolution.confidence());
        assertEquals("updateProduct", resolution.method().methodName());
        assertEquals("DataProductController", resolution.method().declaringTypeSimpleName());
        assertEquals("com.example.crm.product.DataProductController", resolution.method().declaringTypeQualifiedName());
        assertEquals("ResponseEntity<ProductWebModel>", resolution.method().returnType());
        assertEquals(2, resolution.method().parameterCount());
        assertEquals(List.of("String", "ProductWebModel"), resolution.method().parameterTypes());
        assertEquals(List.of("ttaId", "webModel"), resolution.method().parameterNames());
        assertTrue(resolution.method().publicMethod());
        assertFalse(resolution.method().privateMethod());
        assertEquals(1, resolution.candidates().size());
        assertTrue(locator.methodDeclaration(astFile, resolution.method()).isPresent());
    }

    @Test
    void shouldResolvePrivateHelperInSameClass() {
        var astFile = astFile("src/main/java/com/example/crm/product/DataProductController.java", """
                package com.example.crm.product;

                class DataProductController {
                    ProductWebModel getProduct(String ttaId) {
                        return getProductWebModel(ttaId);
                    }

                    private ProductWebModel getProductWebModel(String ttaId) {
                        return new ProductWebModel(ttaId);
                    }
                }
                """);

        var resolution = locator.resolveMethod(astFile, "DataProductController", "getProductWebModel", 1);

        assertEquals(GitLabJavaMethodResolutionStatus.RESOLVED, resolution.status());
        assertEquals("getProductWebModel", resolution.method().methodName());
        assertEquals("ProductWebModel", resolution.method().returnType());
        assertEquals(List.of("private"), resolution.method().modifiers());
        assertTrue(resolution.method().privateMethod());
    }

    @Test
    void shouldResolveOverloadByArgumentCount() {
        var astFile = astFile("src/main/java/com/example/crm/product/CustomerPolicy.java", """
                package com.example.crm.product;

                class CustomerPolicy {
                    Decision apply(String id) {
                        return new Decision(id);
                    }

                    Decision apply(String id, CustomerUpdate update) {
                        return new Decision(id);
                    }
                }
                """);

        var resolution = locator.resolveMethod(astFile, "CustomerPolicy", "apply", 2);

        assertEquals(GitLabJavaMethodResolutionStatus.RESOLVED, resolution.status());
        assertEquals(2, resolution.method().parameterCount());
        assertEquals(List.of("String", "CustomerUpdate"), resolution.method().parameterTypes());
    }

    @Test
    void shouldResolveOverloadByExpectedParameterTypes() {
        var astFile = astFile("src/main/java/com/example/crm/product/UpdateProductService.java", """
                package com.example.crm.product;

                import org.springframework.context.event.EventListener;

                class UpdateProductService {
                    void update(Product product) {
                    }

                    @EventListener
                    void update(DecisionChangeEvent event) {
                    }

                    @EventListener
                    void update(CreditCaseUpdatedEvent event) {
                    }
                }
                """);

        var resolution = locator.resolveMethod(astFile, "UpdateProductService", "update", 1, List.of("Product"));

        assertEquals(GitLabJavaMethodResolutionStatus.RESOLVED, resolution.status());
        assertEquals(List.of("Product"), resolution.method().parameterTypes());
        assertEquals(GitLabEndpointUseCaseConfidence.HIGH, resolution.confidence());
    }

    @Test
    void shouldPreferNonEventListenerOverloadWhenOnlyEventListenersCompete() {
        var astFile = astFile("src/main/java/com/example/crm/product/UpdateProductService.java", """
                package com.example.crm.product;

                import org.springframework.context.event.EventListener;

                class UpdateProductService {
                    void update(Product product) {
                    }

                    @EventListener
                    void update(Object event) {
                    }
                }
                """);

        var resolution = locator.resolveMethod(astFile, "UpdateProductService", "update", 1);

        assertEquals(GitLabJavaMethodResolutionStatus.RESOLVED, resolution.status());
        assertEquals(List.of("Product"), resolution.method().parameterTypes());
        assertEquals(GitLabEndpointUseCaseConfidence.MEDIUM, resolution.confidence());
    }

    @Test
    void shouldReturnAmbiguousForOverloadWithoutArgumentCount() {
        var astFile = astFile("src/main/java/com/example/crm/product/CustomerPolicy.java", """
                package com.example.crm.product;

                class CustomerPolicy {
                    Decision apply(String id) {
                        return new Decision(id);
                    }

                    Decision apply(String id, CustomerUpdate update) {
                        return new Decision(id);
                    }
                }
                """);

        var resolution = locator.resolveMethod(astFile, "CustomerPolicy", "apply");

        assertEquals(GitLabJavaMethodResolutionStatus.AMBIGUOUS, resolution.status());
        assertEquals(2, resolution.candidates().size());
        assertEquals(List.of("More than one method matched; provide argument count to disambiguate overloads."),
                resolution.limitations());
    }

    @Test
    void shouldResolveDefaultMethodInInterface() {
        var astFile = astFile("src/main/java/com/example/crm/product/DataProductApi.java", """
                package com.example.crm.product;

                interface DataProductApi {
                    default ProductWebModel getProduct(String ttaId) {
                        return null;
                    }
                }
                """);

        var resolution = locator.resolveMethod(astFile, "DataProductApi", "getProduct", 1);

        assertEquals(GitLabJavaMethodResolutionStatus.RESOLVED, resolution.status());
        assertEquals(GitLabJavaTypeKind.INTERFACE, resolution.method().declaringTypeKind());
        assertTrue(resolution.method().defaultMethod());
        assertEquals(List.of("default"), resolution.method().modifiers());
    }

    @Test
    void shouldReturnNotFoundWhenMethodDoesNotExist() {
        var astFile = astFile("src/main/java/com/example/crm/product/DataProductController.java", """
                package com.example.crm.product;

                class DataProductController {
                    ProductWebModel getProduct(String ttaId) {
                        return null;
                    }
                }
                """);

        var resolution = locator.resolveMethod(astFile, "DataProductController", "updateProduct", 2);

        assertEquals(GitLabJavaMethodResolutionStatus.NOT_FOUND, resolution.status());
        assertTrue(resolution.candidates().isEmpty());
        assertEquals(List.of("Method was not found in parsed Java source. Target: DataProductController#updateProduct."),
                resolution.limitations());
    }

    @Test
    void shouldReturnParseFailedWhenAstFileWasNotParsed() {
        var astFile = new GitLabJavaAstFile(
                "src/main/java/com/example/crm/product/BrokenController.java",
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of("Could not parse Java source.")
        );

        var resolution = locator.resolveMethod(astFile, "BrokenController", "getProduct", 1);

        assertEquals(GitLabJavaMethodResolutionStatus.PARSE_FAILED, resolution.status());
        assertEquals(List.of("Could not parse Java source."), resolution.limitations());
    }

    private GitLabJavaAstFile astFile(String path, String content) {
        when(repositoryPort.listRepositoryFiles("CRM", "crm-customer-service", "main", "src/main/java"))
                .thenReturn(List.of(new GitLabRepositoryFile("CRM", "crm-customer-service", "main", path)));
        when(repositoryPort.readFile("CRM", "crm-customer-service", "main", path, 120_000))
                .thenReturn(new GitLabRepositoryFileContent(
                        "CRM",
                        "crm-customer-service",
                        "main",
                        path,
                        content,
                        false
                ));
        return sourceResolver.astFile(session, path);
    }
}
