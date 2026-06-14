package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFile;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryPort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitLabJavaDependencyModelBuilderTest {

    private final GitLabRepositoryPort repositoryPort = mock(GitLabRepositoryPort.class);
    private final GitLabEndpointUseCaseRepositoryContext repository = new GitLabEndpointUseCaseRepositoryContext(
            "CRM",
            "crm-customer-service",
            "main",
            "src/main/java"
    );
    private final GitLabEndpointUseCaseSourceSession session = new GitLabEndpointUseCaseSourceSession(
            repositoryPort,
            repository
    );
    private final GitLabJavaSourceResolver sourceResolver = new GitLabJavaSourceResolver();
    private final GitLabJavaDependencyModelBuilder builder = new GitLabJavaDependencyModelBuilder();

    @Test
    void shouldDetectLombokRequiredArgsConstructorFinalFieldDependencies() {
        var astFile = astFile("src/main/java/com/example/crm/product/DataProductController.java", """
                package com.example.crm.product;

                import lombok.RequiredArgsConstructor;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequiredArgsConstructor
                class DataProductController {
                    private final UpdateProductPort updateProductPort;
                    private final ProductRepositoryPort.Query productQueryRepository;
                    private final String systemName;
                    private ProductRepositoryPort.Command commandRepository;
                    private final ProductRepositoryPort.Command initializedCommandRepository = null;
                }
                """);

        var model = builder.build(astFile, "DataProductController");

        assertEquals("DataProductController", model.bean().simpleName());
        assertTrue(model.bean().potentialBean());
        assertEquals(List.of("RestController"), model.bean().stereotypeAnnotations());
        assertEquals(2, model.injectedDependencies().size());
        assertDependency(
                model.injectedDependencies().get(0),
                "updateProductPort",
                "UpdateProductPort",
                GitLabJavaInjectionSource.LOMBOK_REQUIRED_ARGS,
                null
        );
        assertDependency(
                model.injectedDependencies().get(1),
                "productQueryRepository",
                "ProductRepositoryPort.Query",
                GitLabJavaInjectionSource.LOMBOK_REQUIRED_ARGS,
                null
        );
    }

    @Test
    void shouldDetectConstructorInjectionAndSkipFrameworkValues() {
        var astFile = astFile("src/main/java/com/example/crm/product/UpdateProductService.java", """
                package com.example.crm.product;

                import java.time.Clock;
                import org.springframework.stereotype.Service;

                @Service
                class UpdateProductService {
                    UpdateProductService(ProductRepositoryPort.Query productQueryRepository,
                                         ProductRepositoryPort.Command productCommandRepository,
                                         Clock clock) {
                    }
                }
                """);

        var model = builder.build(astFile, "UpdateProductService");

        assertTrue(model.bean().potentialBean());
        assertEquals(List.of("Service"), model.bean().stereotypeAnnotations());
        assertEquals(2, model.injectedDependencies().size());
        assertDependency(
                model.injectedDependencies().get(0),
                "productQueryRepository",
                "ProductRepositoryPort.Query",
                GitLabJavaInjectionSource.CONSTRUCTOR,
                null
        );
        assertDependency(
                model.injectedDependencies().get(1),
                "productCommandRepository",
                "ProductRepositoryPort.Command",
                GitLabJavaInjectionSource.CONSTRUCTOR,
                null
        );
    }

    @Test
    void shouldDetectAutowiredFieldInjectionWithQualifier() {
        var astFile = astFile("src/main/java/com/example/crm/customer/CustomerAdapter.java", """
                package com.example.crm.customer;

                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.beans.factory.annotation.Qualifier;
                import org.springframework.stereotype.Component;

                @Component
                class CustomerAdapter {
                    @Autowired
                    @Qualifier("crmCustomerClient")
                    private CustomerClient customerClient;
                }
                """);

        var model = builder.build(astFile, "CustomerAdapter");

        assertEquals(1, model.injectedDependencies().size());
        assertDependency(
                model.injectedDependencies().get(0),
                "customerClient",
                "CustomerClient",
                GitLabJavaInjectionSource.AUTOWIRED_FIELD,
                "crmCustomerClient"
        );
        assertEquals(List.of("Autowired", "Qualifier"), model.injectedDependencies().get(0).annotations());
    }

    @Test
    void shouldDetectAutowiredConstructorInjectionWithParameterQualifier() {
        var astFile = astFile("src/main/java/com/example/crm/customer/CustomerSyncService.java", """
                package com.example.crm.customer;

                import org.springframework.beans.factory.annotation.Autowired;
                import org.springframework.beans.factory.annotation.Qualifier;

                class CustomerSyncService {
                    @Autowired
                    CustomerSyncService(@Qualifier(CustomerClients.PRIMARY) CustomerClient customerClient) {
                    }
                }
                """);

        var model = builder.build(astFile, "CustomerSyncService");

        assertEquals(1, model.injectedDependencies().size());
        assertDependency(
                model.injectedDependencies().get(0),
                "customerClient",
                "CustomerClient",
                GitLabJavaInjectionSource.AUTOWIRED_CONSTRUCTOR,
                "CustomerClients.PRIMARY"
        );
        assertEquals(List.of("Autowired", "Qualifier"), model.injectedDependencies().get(0).annotations());
        assertEquals(GitLabEndpointUseCaseConfidence.HIGH, model.injectedDependencies().get(0).confidence());
    }

    @Test
    void shouldDetectCustomBeanStereotypes() {
        var astFile = astFile("src/main/java/com/example/crm/product/ProductQueryRepository.java", """
                package com.example.crm.product;

                @AdapterBean
                @UseCaseBean
                class ProductQueryRepository {
                }
                """);

        var model = builder.build(astFile, "ProductQueryRepository");

        assertTrue(model.bean().potentialBean());
        assertEquals(List.of("AdapterBean", "UseCaseBean"), model.bean().stereotypeAnnotations());
        assertEquals("com.example.crm.product.ProductQueryRepository", model.bean().qualifiedName());
    }

    @Test
    void shouldNotCreateDependencyForPlainNonFinalFields() {
        var astFile = astFile("src/main/java/com/example/crm/product/ProductQueryRepository.java", """
                package com.example.crm.product;

                class ProductQueryRepository {
                    private ProductMapper mapper;
                    private static final ProductMapper STATIC_MAPPER = null;
                }
                """);

        var model = builder.build(astFile, "ProductQueryRepository");

        assertTrue(model.injectedDependencies().isEmpty());
        assertEquals(GitLabEndpointUseCaseConfidence.MEDIUM, model.confidence());
    }

    @Test
    void shouldReturnLimitationWhenTypeIsMissing() {
        var astFile = astFile("src/main/java/com/example/crm/product/ProductQueryRepository.java", """
                package com.example.crm.product;

                class ProductQueryRepository {
                }
                """);

        var model = builder.build(astFile, "MissingType");

        assertEquals(null, model.bean());
        assertTrue(model.injectedDependencies().isEmpty());
        assertEquals(List.of("Type was not found in parsed Java source: MissingType."), model.limitations());
    }

    private void assertDependency(
            GitLabJavaInjectedDependency dependency,
            String name,
            String typeName,
            GitLabJavaInjectionSource source,
            String qualifier
    ) {
        assertEquals(name, dependency.name());
        assertEquals(typeName, dependency.typeName());
        assertEquals(source, dependency.source());
        assertEquals(qualifier, dependency.qualifier());
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
