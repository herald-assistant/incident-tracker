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

class GitLabJavaSpringDataRepositoryDetectorTest {

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
    private final GitLabJavaSpringDataRepositoryDetector detector = new GitLabJavaSpringDataRepositoryDetector();

    @Test
    void shouldDetectPackagePrivateJpaRepository() {
        var astFile = astFile("src/main/java/com/example/crm/product/ProductQueryRepository.java", """
                package com.example.crm.product;

                import org.springframework.data.jpa.repository.JpaRepository;

                interface ProductQueryJpaRepository extends JpaRepository<ProductEntity, Long> {
                }
                """);

        var detection = detector.detect(astFile, "ProductQueryJpaRepository");

        assertEquals(GitLabJavaSpringDataRepositoryStatus.DETECTED, detection.status());
        assertEquals(GitLabEndpointUseCaseConfidence.HIGH, detection.confidence());
        assertEquals("ProductQueryJpaRepository", detection.repository().simpleName());
        assertEquals("com.example.crm.product.ProductQueryJpaRepository", detection.repository().qualifiedName());
        assertEquals("src/main/java/com/example/crm/product/ProductQueryRepository.java", detection.repository().filePath());
        assertEquals("JpaRepository", detection.repository().baseInterface());
        assertEquals("ProductEntity", detection.repository().entityType());
        assertEquals("Long", detection.repository().idType());
        assertTrue(detection.repository().declaredMethodNames().isEmpty());
    }

    @Test
    void shouldDetectDeclaredRepositoryMethods() {
        var astFile = astFile("src/main/java/com/example/crm/product/ProductQueryRepository.java", """
                package com.example.crm.product;

                interface ProductQueryJpaRepository
                        extends org.springframework.data.jpa.repository.JpaRepository<ProductEntity, Long> {

                    ProductProjection findProjectionByTtaId(String ttaId);

                    boolean existsByTtaId(String ttaId);
                }
                """);

        var detection = detector.detect(astFile, "ProductQueryJpaRepository");

        assertEquals(GitLabJavaSpringDataRepositoryStatus.DETECTED, detection.status());
        assertEquals("JpaRepository", detection.repository().baseInterface());
        assertEquals(List.of("findProjectionByTtaId", "existsByTtaId"), detection.repository().declaredMethodNames());
    }

    @Test
    void shouldDetectCrudRepositoryAndPagingAndSortingRepository() {
        var astFile = astFile("src/main/java/com/example/crm/product/ProductRepositories.java", """
                package com.example.crm.product;

                import org.springframework.data.repository.CrudRepository;
                import org.springframework.data.repository.PagingAndSortingRepository;

                interface ProductCommandJpaRepository extends CrudRepository<ProductEntity, Long> {
                }

                interface ProductPageJpaRepository extends PagingAndSortingRepository<ProductEntity, Long> {
                }
                """);

        var command = detector.detect(astFile, "ProductCommandJpaRepository");
        var page = detector.detect(astFile, "ProductPageJpaRepository");

        assertEquals(GitLabJavaSpringDataRepositoryStatus.DETECTED, command.status());
        assertEquals("CrudRepository", command.repository().baseInterface());
        assertEquals("ProductEntity", command.repository().entityType());
        assertEquals(GitLabJavaSpringDataRepositoryStatus.DETECTED, page.status());
        assertEquals("PagingAndSortingRepository", page.repository().baseInterface());
        assertEquals("ProductEntity", page.repository().entityType());
    }

    @Test
    void shouldDetectAllRepositoriesInOneFile() {
        var astFile = astFile("src/main/java/com/example/crm/product/ProductRepositories.java", """
                package com.example.crm.product;

                import org.springframework.data.jpa.repository.JpaRepository;

                interface ProductQueryJpaRepository extends JpaRepository<ProductEntity, Long> {
                }

                class ProductRepositorySupport {
                }

                interface MultilineQueryJpaRepository extends JpaRepository<MultilineEntity, Long> {
                }
                """);

        var repositories = detector.detectAll(astFile);

        assertEquals(List.of("ProductQueryJpaRepository", "MultilineQueryJpaRepository"),
                repositories.stream().map(GitLabJavaSpringDataRepository::simpleName).toList());
        assertEquals(List.of("ProductEntity", "MultilineEntity"),
                repositories.stream().map(GitLabJavaSpringDataRepository::entityType).toList());
    }

    @Test
    void shouldReturnNotSpringDataRepositoryForPlainInterface() {
        var astFile = astFile("src/main/java/com/example/crm/product/ProductRepositoryPort.java", """
                package com.example.crm.product;

                interface ProductRepositoryPort {
                    interface Query {
                    }
                }
                """);

        var detection = detector.detect(astFile, "ProductRepositoryPort");

        assertEquals(GitLabJavaSpringDataRepositoryStatus.NOT_SPRING_DATA_REPOSITORY, detection.status());
        assertEquals(List.of("Interface does not extend a supported Spring Data repository base interface. Type: ProductRepositoryPort."),
                detection.limitations());
    }

    @Test
    void shouldReturnTypeNotFoundWhenTypeIsMissing() {
        var astFile = astFile("src/main/java/com/example/crm/product/ProductRepositoryPort.java", """
                package com.example.crm.product;

                interface ProductRepositoryPort {
                }
                """);

        var detection = detector.detect(astFile, "MissingJpaRepository");

        assertEquals(GitLabJavaSpringDataRepositoryStatus.TYPE_NOT_FOUND, detection.status());
        assertEquals(List.of("Type was not found in parsed Java source: MissingJpaRepository."), detection.limitations());
    }

    @Test
    void shouldReturnParseFailedWhenAstFileWasNotParsed() {
        var astFile = new GitLabJavaAstFile(
                "src/main/java/com/example/crm/product/BrokenRepository.java",
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of("Could not parse Java source.")
        );

        var detection = detector.detect(astFile, "BrokenRepository");

        assertEquals(GitLabJavaSpringDataRepositoryStatus.PARSE_FAILED, detection.status());
        assertEquals(List.of("Could not parse Java source."), detection.limitations());
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
