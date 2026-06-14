package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFile;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryPort;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitLabEndpointUseCaseTraversalServiceTest {

    private static final String GROUP = "CRM";
    private static final String PROJECT = "crm-product-service";
    private static final String BRANCH = "main";
    private static final String SOURCE_PREFIX = "src/main/java";

    private final GitLabRepositoryPort repositoryPort = mock(GitLabRepositoryPort.class);
    private final GitLabEndpointUseCaseRepositoryContext repository = new GitLabEndpointUseCaseRepositoryContext(
            GROUP,
            PROJECT,
            BRANCH,
            SOURCE_PREFIX
    );
    private final GitLabEndpointUseCaseTraversalService traversalService = new GitLabEndpointUseCaseTraversalService();

    @Test
    void shouldBuildCompactGetEndpointUseCaseContext() {
        var files = crmProductSources();
        stubRepository(files);
        var session = new GitLabEndpointUseCaseSourceSession(repositoryPort, repository);

        var result = traversalService.traverse(
                session,
                endpoint("GET /api/products/{productId} -> DataProductController#getProduct", "GET", "getProduct"),
                new GitLabEndpointUseCaseLimits(6, 30, 80, false, false, 0, false)
        );

        var byPath = filesByPath(result);
        assertEquals(GitLabEndpointUseCaseFileRole.CONTROLLER,
                byPath.get(path("api/DataProductController.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.REPOSITORY_PORT,
                byPath.get(path("domain/ProductRepositoryPort.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.REPOSITORY_IMPLEMENTATION,
                byPath.get(path("adapter/out/ProductQueryRepository.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.MAPPER,
                byPath.get(path("api/ProductWebModelMapper.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.MAPPER,
                byPath.get(path("api/OverdraftWebModelMapper.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.MAPPER,
                byPath.get(path("api/MultilineWebModelMapper.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.PROJECTION,
                byPath.get(path("domain/OverdraftFormView.java")).role());
        assertTrue(byPath.containsKey(path("domain/MultilineFormView.java")), byPath.keySet().toString());
        assertEquals(GitLabEndpointUseCaseFileRole.PROJECTION,
                byPath.get(path("domain/MultilineFormView.java")).role());
        assertTrue(byPath.get(path("api/DataProductController.java")).symbols().contains("getProductWebModel"));
        assertTrue(result.relations().stream()
                .anyMatch(relation -> relation.kind() == GitLabEndpointUseCaseRelationKind.MAPPER_CALL
                        && relation.to().contains("ProductWebModelMapper#from")));
        assertFalse(result.limits().maxDepthReached());
        assertFalse(result.limits().maxFilesReached());
    }

    @Test
    void shouldBuildCompactPutEndpointUseCaseContextWithUseCaseServiceAndDomainMethod() {
        var files = crmProductSources();
        stubRepository(files);
        var session = new GitLabEndpointUseCaseSourceSession(repositoryPort, repository);

        var result = traversalService.traverse(
                session,
                endpoint("PUT /api/products/{productId} -> DataProductController#updateProduct", "PUT", "updateProduct"),
                new GitLabEndpointUseCaseLimits(6, 35, 90, false, false, 0, false)
        );

        var byPath = filesByPath(result);
        assertEquals(GitLabEndpointUseCaseFileRole.USE_CASE_PORT,
                byPath.get(path("application/UpdateProductPort.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.USE_CASE_SERVICE,
                byPath.get(path("application/UpdateProductService.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.REPOSITORY_IMPLEMENTATION,
                byPath.get(path("adapter/out/ProductQueryRepository.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.REPOSITORY_IMPLEMENTATION,
                byPath.get(path("adapter/out/ProductCommandRepository.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.DOMAIN_MODEL,
                byPath.get(path("domain/Product.java")).role());
        assertEquals(GitLabEndpointUseCaseFileRole.MAPPER,
                byPath.get(path("api/ProductWebModelMapper.java")).role());
        assertTrue(byPath.get(path("domain/Product.java")).symbols().contains("update"));
        assertTrue(byPath.get(path("domain/Product.java")).symbols().contains("calculateStatus"));
        assertTrue(result.relations().stream()
                .anyMatch(relation -> relation.kind() == GitLabEndpointUseCaseRelationKind.INTERFACE_IMPLEMENTATION
                        && relation.to().contains("UpdateProductService#update")));
        assertTrue(result.relations().stream()
                .anyMatch(relation -> relation.kind() == GitLabEndpointUseCaseRelationKind.DOMAIN_METHOD_CALL
                        && relation.to().contains("Product#update")));
    }

    @Test
    void shouldRespectMaxDepthLimit() {
        var files = crmProductSources();
        stubRepository(files);
        var session = new GitLabEndpointUseCaseSourceSession(repositoryPort, repository);

        var result = traversalService.traverse(
                session,
                endpoint("PUT /api/products/{productId} -> DataProductController#updateProduct", "PUT", "updateProduct"),
                new GitLabEndpointUseCaseLimits(1, 35, 90, false, false, 0, false)
        );

        assertTrue(result.limits().maxDepthReached());
        var product = filesByPath(result).get(path("domain/Product.java"));
        assertTrue(product == null || !product.symbols().contains("calculateStatus"));
    }

    private GitLabEndpointUseCaseEndpointContext endpoint(String endpointId, String method, String handlerMethod) {
        return new GitLabEndpointUseCaseEndpointContext(
                endpointId,
                List.of(method),
                "/api/products/{productId}",
                null,
                "com.example.crm.product.api.DataProductController",
                handlerMethod,
                path("api/DataProductController.java"),
                1,
                20,
                List.of("ProductWebModel"),
                List.of("ProductWebModel"),
                List.of("OpenAPI contract src/main/resources/openapi/product-api.yaml"),
                GitLabEndpointUseCaseConfidence.HIGH,
                List.of(),
                List.of("src/main/resources/openapi/product-api.yaml via gitlab_read_repository_file")
        );
    }

    private void stubRepository(Map<String, String> sources) {
        when(repositoryPort.listRepositoryFiles(GROUP, PROJECT, BRANCH, SOURCE_PREFIX))
                .thenReturn(sources.keySet().stream()
                        .map(filePath -> new GitLabRepositoryFile(GROUP, PROJECT, BRANCH, filePath))
                        .toList());
        sources.forEach((filePath, content) -> when(repositoryPort.readFile(GROUP, PROJECT, BRANCH, filePath, 120_000))
                .thenReturn(new GitLabRepositoryFileContent(GROUP, PROJECT, BRANCH, filePath, content, false)));
    }

    private Map<String, GitLabEndpointUseCaseFileCandidate> filesByPath(GitLabEndpointUseCaseContextResult result) {
        return result.files().stream()
                .collect(Collectors.toMap(
                        GitLabEndpointUseCaseFileCandidate::path,
                        Function.identity(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private Map<String, String> crmProductSources() {
        var sources = new LinkedHashMap<String, String>();
        sources.put(path("api/DataProductController.java"), """
                package com.example.crm.product.api;

                import com.example.crm.product.application.UpdateProductPort;
                import com.example.crm.product.domain.FormView;
                import com.example.crm.product.domain.Product;
                import com.example.crm.product.domain.ProductRepositoryPort;
                import com.example.crm.product.domain.ProductType;
                import lombok.RequiredArgsConstructor;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequiredArgsConstructor
                class DataProductController implements DataProductApi {
                    private final ProductRepositoryPort.Query productQueryRepository;
                    private final UpdateProductPort updateProductPort;

                    public ProductWebModel getProduct(String productId) {
                        return getProductWebModel(productQueryRepository.getProductFormView(productId));
                    }

                    public ProductWebModel updateProduct(String productId, ProductWebModel webModel) {
                        Product product = ProductWebModelMapper.INSTANCE.from(webModel);
                        updateProductPort.update(getTtaId(productId), product);
                        return getProductWebModel(productQueryRepository.getProductFormView(productId));
                    }

                    private String getTtaId(String productId) {
                        return productId;
                    }

                    private ProductWebModel getProductWebModel(FormView<ProductType> formView) {
                        return ProductWebModelMapper.INSTANCE.from(formView);
                    }
                }
                """);
        sources.put(path("api/DataProductApi.java"), """
                package com.example.crm.product.api;

                interface DataProductApi {
                    ProductWebModel getProduct(String productId);
                    ProductWebModel updateProduct(String productId, ProductWebModel webModel);
                }
                """);
        sources.put(path("api/ProductWebModel.java"), """
                package com.example.crm.product.api;

                record ProductWebModel(String type) {
                }
                """);
        sources.put(path("api/ProductWebModelMapper.java"), """
                package com.example.crm.product.api;

                import com.example.crm.product.domain.FormView;
                import com.example.crm.product.domain.Product;
                import com.example.crm.product.domain.ProductType;
                import org.mapstruct.Mapper;
                import org.mapstruct.factory.Mappers;

                @Mapper
                interface ProductWebModelMapper {
                    ProductWebModelMapper INSTANCE = Mappers.getMapper(ProductWebModelMapper.class);

                    default Product from(ProductWebModel webModel) {
                        return switch (webModel.type()) {
                            case "OVERDRAFT" -> OverdraftWebModelMapper.INSTANCE.fromOverdraft(webModel);
                            default -> MultilineWebModelMapper.INSTANCE.fromMultiline(webModel);
                        };
                    }

                    default ProductWebModel from(FormView<ProductType> formView) {
                        return switch (formView.type()) {
                            case OVERDRAFT -> OverdraftWebModelMapper.INSTANCE.toWebModel(formView);
                            case MULTILINE -> MultilineWebModelMapper.INSTANCE.toWebModel(formView);
                        };
                    }
                }
                """);
        sources.put(path("api/OverdraftWebModelMapper.java"), """
                package com.example.crm.product.api;

                import com.example.crm.product.domain.FormView;
                import com.example.crm.product.domain.Overdraft;
                import com.example.crm.product.domain.Product;
                import com.example.crm.product.domain.ProductType;
                import org.mapstruct.Mapper;
                import org.mapstruct.factory.Mappers;

                @Mapper
                interface OverdraftWebModelMapper {
                    OverdraftWebModelMapper INSTANCE = Mappers.getMapper(OverdraftWebModelMapper.class);

                    default Product fromOverdraft(ProductWebModel webModel) {
                        return new Overdraft();
                    }

                    ProductWebModel toWebModel(com.example.crm.product.domain.OverdraftFormView formView);
                }
                """);
        sources.put(path("api/MultilineWebModelMapper.java"), """
                package com.example.crm.product.api;

                import com.example.crm.product.domain.FormView;
                import com.example.crm.product.domain.Multiline;
                import com.example.crm.product.domain.Product;
                import com.example.crm.product.domain.ProductType;
                import org.mapstruct.Mapper;
                import org.mapstruct.factory.Mappers;

                @Mapper
                interface MultilineWebModelMapper {
                    MultilineWebModelMapper INSTANCE = Mappers.getMapper(MultilineWebModelMapper.class);

                    default Product fromMultiline(ProductWebModel webModel) {
                        return new Multiline();
                    }

                    ProductWebModel toWebModel(com.example.crm.product.domain.MultilineFormView formView);
                }
                """);
        sources.put(path("application/UpdateProductPort.java"), """
                package com.example.crm.product.application;

                import com.example.crm.product.domain.Product;

                interface UpdateProductPort {
                    void update(String productId, Product product);
                }
                """);
        sources.put(path("application/UpdateProductService.java"), """
                package com.example.crm.product.application;

                import com.example.crm.product.domain.Product;
                import com.example.crm.product.domain.ProductRepositoryPort;
                import lombok.RequiredArgsConstructor;
                import org.springframework.context.event.EventListener;
                import org.springframework.stereotype.Service;

                @Service
                @RequiredArgsConstructor
                class UpdateProductService implements UpdateProductPort {
                    private final ProductRepositoryPort.Query queryRepository;
                    private final ProductRepositoryPort.Command commandRepository;

                    public void update(String productId, Product product) {
                        Product current = queryRepository.getProductForUpdate(productId);
                        product.update(current);
                        commandRepository.update(product);
                    }

                    @EventListener
                    void afterProductUpdated(Object event) {
                    }
                }
                """);
        sources.put(path("domain/ProductRepositoryPort.java"), """
                package com.example.crm.product.domain;

                public interface ProductRepositoryPort {
                    interface Query {
                        FormView<ProductType> getProductFormView(String productId);
                        Product getProductForUpdate(String productId);
                    }

                    interface Command {
                        void update(Product product);
                    }
                }
                """);
        sources.put(path("adapter/out/ProductQueryRepository.java"), """
                package com.example.crm.product.adapter.out;

                import com.example.crm.product.domain.FormView;
                import com.example.crm.product.domain.OverdraftFormView;
                import com.example.crm.product.domain.Product;
                import com.example.crm.product.domain.ProductEntity;
                import com.example.crm.product.domain.ProductRepositoryPort;
                import com.example.crm.product.domain.ProductType;
                import lombok.RequiredArgsConstructor;
                import org.springframework.data.jpa.repository.JpaRepository;
                import org.springframework.stereotype.Repository;

                @Repository
                @RequiredArgsConstructor
                class ProductQueryRepository implements ProductRepositoryPort.Query {
                    private final ProductQueryJpaRepository productQueryJpaRepository;
                    private final OverdraftQueryJpaRepository overdraftQueryJpaRepository;

                    public FormView<ProductType> getProductFormView(String productId) {
                        productQueryJpaRepository.findByProductId(productId);
                        return new OverdraftFormView();
                    }

                    public Product getProductForUpdate(String productId) {
                        productQueryJpaRepository.findByProductId(productId);
                        return new Product();
                    }
                }

                interface ProductQueryJpaRepository extends JpaRepository<ProductEntity, Long> {
                    ProductEntity findByProductId(String productId);
                }

                interface OverdraftQueryJpaRepository extends JpaRepository<OverdraftFormView, Long> {
                }
                """);
        sources.put(path("adapter/out/ProductCommandRepository.java"), """
                package com.example.crm.product.adapter.out;

                import com.example.crm.product.domain.Product;
                import com.example.crm.product.domain.ProductEntity;
                import com.example.crm.product.domain.ProductRepositoryPort;
                import lombok.RequiredArgsConstructor;
                import org.springframework.data.jpa.repository.JpaRepository;
                import org.springframework.stereotype.Repository;

                @Repository
                @RequiredArgsConstructor
                class ProductCommandRepository implements ProductRepositoryPort.Command {
                    private final ProductCommandJpaRepository productCommandJpaRepository;

                    public void update(Product product) {
                        productCommandJpaRepository.save(product);
                    }
                }

                interface ProductCommandJpaRepository extends JpaRepository<ProductEntity, Long> {
                }
                """);
        sources.put(path("domain/FormView.java"), """
                package com.example.crm.product.domain;

                interface FormView<T> {
                    T type();
                }
                """);
        sources.put(path("domain/OverdraftFormView.java"), """
                package com.example.crm.product.domain;

                public class OverdraftFormView implements FormView<ProductType> {
                    public ProductType type() {
                        return ProductType.OVERDRAFT;
                    }
                }
                """);
        sources.put(path("domain/MultilineFormView.java"), """
                package com.example.crm.product.domain;

                public class MultilineFormView implements FormView<ProductType> {
                    public ProductType type() {
                        return ProductType.MULTILINE;
                    }
                }
                """);
        sources.put(path("domain/Product.java"), """
                package com.example.crm.product.domain;

                public class Product {
                    public void update(Product current) {
                        calculateStatus();
                    }

                    private ProductStatus calculateStatus() {
                        return ProductStatus.ACTIVE;
                    }
                }
                """);
        sources.put(path("domain/Overdraft.java"), """
                package com.example.crm.product.domain;

                public class Overdraft extends Product {
                }
                """);
        sources.put(path("domain/Multiline.java"), """
                package com.example.crm.product.domain;

                public class Multiline extends Product {
                }
                """);
        sources.put(path("domain/ProductEntity.java"), """
                package com.example.crm.product.domain;

                public class ProductEntity {
                }
                """);
        sources.put(path("domain/ProductStatus.java"), """
                package com.example.crm.product.domain;

                enum ProductStatus {
                    ACTIVE
                }
                """);
        sources.put(path("domain/ProductType.java"), """
                package com.example.crm.product.domain;

                public enum ProductType {
                    OVERDRAFT,
                    MULTILINE
                }
                """);
        sources.put("src/main/resources/openapi/product-api.yaml", """
                openapi: 3.0.0
                paths:
                  /api/products/{productId}:
                    get:
                      operationId: getProduct
                    put:
                      operationId: updateProduct
                """);
        return sources;
    }

    private String path(String relativePath) {
        return SOURCE_PREFIX + "/com/example/crm/product/" + relativePath;
    }
}
