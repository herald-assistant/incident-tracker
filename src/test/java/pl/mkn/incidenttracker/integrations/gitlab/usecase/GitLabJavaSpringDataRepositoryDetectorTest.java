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
            "main"
    );
    private final GitLabEndpointUseCaseSourceSession session = new GitLabEndpointUseCaseSourceSession(
            repositoryPort,
            repository
    );
    private final GitLabJavaSourceResolver sourceResolver = new GitLabJavaSourceResolver();
    private final GitLabJavaSpringDataRepositoryDetector detector = new GitLabJavaSpringDataRepositoryDetector();

    @Test
    void shouldDetectPackagePrivateJpaRepository() {
        var astFile = astFile("src/main/java/com/example/crm/customer/CustomerQueryRepository.java", """
                package com.example.crm.customer;

                import org.springframework.data.jpa.repository.JpaRepository;

                interface CustomerQueryJpaRepository extends JpaRepository<CustomerEntity, Long> {
                }
                """);

        var detection = detector.detect(astFile, "CustomerQueryJpaRepository");

        assertEquals(GitLabJavaSpringDataRepositoryStatus.DETECTED, detection.status());
        assertEquals(GitLabEndpointUseCaseConfidence.HIGH, detection.confidence());
        assertEquals("CustomerQueryJpaRepository", detection.repository().simpleName());
        assertEquals("com.example.crm.customer.CustomerQueryJpaRepository", detection.repository().qualifiedName());
        assertEquals("src/main/java/com/example/crm/customer/CustomerQueryRepository.java", detection.repository().filePath());
        assertEquals("JpaRepository", detection.repository().baseInterface());
        assertEquals("CustomerEntity", detection.repository().entityType());
        assertEquals("Long", detection.repository().idType());
        assertTrue(detection.repository().declaredMethodNames().isEmpty());
    }

    @Test
    void shouldDetectDeclaredRepositoryMethods() {
        var astFile = astFile("src/main/java/com/example/crm/customer/CustomerQueryRepository.java", """
                package com.example.crm.customer;

                interface CustomerQueryJpaRepository
                        extends org.springframework.data.jpa.repository.JpaRepository<CustomerEntity, Long> {

                    CustomerProjection findProjectionByTtaId(String ttaId);

                    boolean existsByTtaId(String ttaId);
                }
                """);

        var detection = detector.detect(astFile, "CustomerQueryJpaRepository");

        assertEquals(GitLabJavaSpringDataRepositoryStatus.DETECTED, detection.status());
        assertEquals("JpaRepository", detection.repository().baseInterface());
        assertEquals(List.of("findProjectionByTtaId", "existsByTtaId"), detection.repository().declaredMethodNames());
    }

    @Test
    void shouldDetectCrudRepositoryAndPagingAndSortingRepository() {
        var astFile = astFile("src/main/java/com/example/crm/customer/CustomerRepositories.java", """
                package com.example.crm.customer;

                import org.springframework.data.repository.CrudRepository;
                import org.springframework.data.repository.PagingAndSortingRepository;

                interface CustomerCommandJpaRepository extends CrudRepository<CustomerEntity, Long> {
                }

                interface CustomerPageJpaRepository extends PagingAndSortingRepository<CustomerEntity, Long> {
                }
                """);

        var command = detector.detect(astFile, "CustomerCommandJpaRepository");
        var page = detector.detect(astFile, "CustomerPageJpaRepository");

        assertEquals(GitLabJavaSpringDataRepositoryStatus.DETECTED, command.status());
        assertEquals("CrudRepository", command.repository().baseInterface());
        assertEquals("CustomerEntity", command.repository().entityType());
        assertEquals(GitLabJavaSpringDataRepositoryStatus.DETECTED, page.status());
        assertEquals("PagingAndSortingRepository", page.repository().baseInterface());
        assertEquals("CustomerEntity", page.repository().entityType());
    }

    @Test
    void shouldDetectAllRepositoriesInOneFile() {
        var astFile = astFile("src/main/java/com/example/crm/customer/CustomerRepositories.java", """
                package com.example.crm.customer;

                import org.springframework.data.jpa.repository.JpaRepository;

                interface CustomerQueryJpaRepository extends JpaRepository<CustomerEntity, Long> {
                }

                class CustomerRepositorySupport {
                }

                interface MultilineQueryJpaRepository extends JpaRepository<MultilineEntity, Long> {
                }
                """);

        var repositories = detector.detectAll(astFile);

        assertEquals(List.of("CustomerQueryJpaRepository", "MultilineQueryJpaRepository"),
                repositories.stream().map(GitLabJavaSpringDataRepository::simpleName).toList());
        assertEquals(List.of("CustomerEntity", "MultilineEntity"),
                repositories.stream().map(GitLabJavaSpringDataRepository::entityType).toList());
    }

    @Test
    void shouldReturnNotSpringDataRepositoryForPlainInterface() {
        var astFile = astFile("src/main/java/com/example/crm/customer/CustomerRepositoryPort.java", """
                package com.example.crm.customer;

                interface CustomerRepositoryPort {
                    interface Query {
                    }
                }
                """);

        var detection = detector.detect(astFile, "CustomerRepositoryPort");

        assertEquals(GitLabJavaSpringDataRepositoryStatus.NOT_SPRING_DATA_REPOSITORY, detection.status());
        assertEquals(List.of("Interface does not extend a supported Spring Data repository base interface. Type: CustomerRepositoryPort."),
                detection.limitations());
    }

    @Test
    void shouldReturnTypeNotFoundWhenTypeIsMissing() {
        var astFile = astFile("src/main/java/com/example/crm/customer/CustomerRepositoryPort.java", """
                package com.example.crm.customer;

                interface CustomerRepositoryPort {
                }
                """);

        var detection = detector.detect(astFile, "MissingJpaRepository");

        assertEquals(GitLabJavaSpringDataRepositoryStatus.TYPE_NOT_FOUND, detection.status());
        assertEquals(List.of("Type was not found in parsed Java source: MissingJpaRepository."), detection.limitations());
    }

    @Test
    void shouldReturnParseFailedWhenAstFileWasNotParsed() {
        var astFile = new GitLabJavaAstFile(
                "src/main/java/com/example/crm/customer/BrokenRepository.java",
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
