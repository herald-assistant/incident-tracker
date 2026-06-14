package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFile;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileChunk;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryProjectCandidate;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositorySearchQuery;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLabJavaInterfaceImplementorResolverTest {

    private final TestRepositoryPort repositoryPort = new TestRepositoryPort();
    private final GitLabEndpointUseCaseRepositoryContext repository = new GitLabEndpointUseCaseRepositoryContext(
            "CRM",
            "crm-customer-service",
            "main",
            "src/main/java"
    );
    private final GitLabJavaInterfaceImplementorResolver resolver = new GitLabJavaInterfaceImplementorResolver();

    @Test
    void shouldResolveSimplePortImplementation() {
        repositoryPort.add("src/main/java/com/example/crm/product/UpdateProductPort.java", """
                package com.example.crm.product;

                interface UpdateProductPort {
                    void update(Product product);
                }
                """);
        repositoryPort.add("src/main/java/com/example/crm/product/UpdateProductService.java", """
                package com.example.crm.product;

                class UpdateProductService implements UpdateProductPort {
                    public void update(Product product) {
                    }
                }
                """);

        var resolution = resolver.resolveImplementors(session(), "UpdateProductPort");

        assertEquals(GitLabJavaImplementorResolutionStatus.RESOLVED, resolution.status());
        assertEquals("UpdateProductPort", resolution.interfaceName());
        assertTrue(resolution.searchKeywords().contains("implements UpdateProductPort"));
        assertEquals(1, resolution.candidates().size());
        assertEquals("UpdateProductService", resolution.candidates().get(0).implementationSimpleName());
        assertEquals("com.example.crm.product.UpdateProductService", resolution.candidates().get(0).implementationQualifiedName());
        assertEquals("src/main/java/com/example/crm/product/UpdateProductService.java", resolution.candidates().get(0).filePath());
        assertEquals(List.of("UpdateProductPort"), resolution.candidates().get(0).implementedTypes());
        assertEquals(GitLabEndpointUseCaseConfidence.HIGH, resolution.candidates().get(0).confidence());
    }

    @Test
    void shouldResolveNestedRepositoryPortImplementation() {
        repositoryPort.add("src/main/java/com/example/crm/product/ProductRepositoryPort.java", """
                package com.example.crm.product;

                interface ProductRepositoryPort {
                    interface Query {
                    }

                    interface Command {
                    }
                }
                """);
        repositoryPort.add("src/main/java/com/example/crm/product/ProductQueryRepository.java", """
                package com.example.crm.product;

                class ProductQueryRepository implements ProductRepositoryPort.Query {
                }
                """);

        var resolution = resolver.resolveImplementors(session(), "ProductRepositoryPort.Query");

        assertEquals(GitLabJavaImplementorResolutionStatus.RESOLVED, resolution.status());
        assertEquals("ProductQueryRepository", resolution.candidates().get(0).implementationSimpleName());
        assertEquals(List.of("ProductRepositoryPort.Query"), resolution.candidates().get(0).implementedTypes());
        assertEquals(GitLabEndpointUseCaseConfidence.HIGH, resolution.candidates().get(0).confidence());
    }

    @Test
    void shouldResolveNestedInterfaceImportedBySimpleName() {
        repositoryPort.add("src/main/java/com/example/crm/product/ProductRepositoryPort.java", """
                package com.example.crm.product;

                interface ProductRepositoryPort {
                    interface Query {
                    }
                }
                """);
        repositoryPort.add("src/main/java/com/example/crm/product/ProductQueryRepository.java", """
                package com.example.crm.product;

                import com.example.crm.product.ProductRepositoryPort.Query;

                class ProductQueryRepository implements Query {
                }
                """);

        var resolution = resolver.resolveImplementors(
                session(),
                "com.example.crm.product.ProductRepositoryPort.Query"
        );

        assertEquals(GitLabJavaImplementorResolutionStatus.RESOLVED, resolution.status());
        assertEquals("ProductQueryRepository", resolution.candidates().get(0).implementationSimpleName());
        assertEquals(List.of("Query"), resolution.candidates().get(0).implementedTypes());
        assertEquals(GitLabEndpointUseCaseConfidence.MEDIUM, resolution.candidates().get(0).confidence());
    }

    @Test
    void shouldReturnAmbiguousWhenMultipleImplementationsMatch() {
        repositoryPort.add("src/main/java/com/example/crm/product/UpdateProductService.java", """
                package com.example.crm.product;

                class UpdateProductService implements UpdateProductPort {
                }
                """);
        repositoryPort.add("src/main/java/com/example/crm/product/LegacyUpdateProductService.java", """
                package com.example.crm.product;

                class LegacyUpdateProductService implements UpdateProductPort {
                }
                """);

        var resolution = resolver.resolveImplementors(session(), "UpdateProductPort");

        assertEquals(GitLabJavaImplementorResolutionStatus.AMBIGUOUS, resolution.status());
        assertEquals(2, resolution.candidates().size());
        assertEquals(List.of(
                        "src/main/java/com/example/crm/product/LegacyUpdateProductService.java",
                        "src/main/java/com/example/crm/product/UpdateProductService.java"
                ),
                resolution.candidates().stream().map(GitLabJavaImplementorCandidate::filePath).toList());
        assertEquals(List.of("More than one implementation matched interface UpdateProductPort."),
                resolution.limitations());
    }

    @Test
    void shouldReturnNotFoundWhenImplementationIsMissing() {
        repositoryPort.add("src/main/java/com/example/crm/product/UpdateProductPort.java", """
                package com.example.crm.product;

                interface UpdateProductPort {
                }
                """);
        repositoryPort.add("src/main/java/com/example/crm/product/ProductQueryRepository.java", """
                package com.example.crm.product;

                class ProductQueryRepository implements ProductRepositoryPort.Query {
                }
                """);

        var resolution = resolver.resolveImplementors(session(), "UpdateProductPort");

        assertEquals(GitLabJavaImplementorResolutionStatus.NOT_FOUND, resolution.status());
        assertTrue(resolution.candidates().isEmpty());
        assertEquals(List.of("No implementation was found for interface UpdateProductPort."),
                resolution.limitations());
    }

    @Test
    void shouldFindPackagePrivateImplementationAmongManyTypesInOneFile() {
        repositoryPort.add("src/main/java/com/example/crm/product/ProductRepositories.java", """
                package com.example.crm.product;

                class ProductRepositorySupport {
                }

                class ProductCommandRepository implements ProductRepositoryPort.Command {
                }
                """);

        var resolution = resolver.resolveImplementors(session(), "ProductRepositoryPort.Command");

        assertEquals(GitLabJavaImplementorResolutionStatus.RESOLVED, resolution.status());
        assertEquals("ProductCommandRepository", resolution.candidates().get(0).implementationSimpleName());
        assertEquals("ProductCommandRepository", resolution.candidates().get(0).implementationRelativeName());
        assertEquals(GitLabJavaTypeKind.CLASS, resolution.candidates().get(0).implementationKind());
    }

    @Test
    void shouldReturnInvalidRequestForBlankInterfaceName() {
        var resolution = resolver.resolveImplementors(session(), " ");

        assertEquals(GitLabJavaImplementorResolutionStatus.INVALID_REQUEST, resolution.status());
        assertEquals(List.of("Interface name is required."), resolution.limitations());
    }

    private GitLabEndpointUseCaseSourceSession session() {
        return new GitLabEndpointUseCaseSourceSession(repositoryPort, repository);
    }

    private static final class TestRepositoryPort implements GitLabRepositoryPort {

        private final Map<String, String> files = new LinkedHashMap<>();

        private void add(String filePath, String content) {
            files.put(filePath, content);
        }

        @Override
        public List<GitLabRepositoryProjectCandidate> searchProjects(String group, List<String> projectHints) {
            return List.of();
        }

        @Override
        public List<GitLabRepositoryFileCandidate> searchCandidateFiles(GitLabRepositorySearchQuery query) {
            return List.of();
        }

        @Override
        public List<GitLabRepositoryFile> listRepositoryFiles(
                String group,
                String projectName,
                String branch,
                String pathPrefix
        ) {
            return files.keySet().stream()
                    .filter(filePath -> pathPrefix == null || filePath.startsWith(pathPrefix))
                    .map(filePath -> new GitLabRepositoryFile(group, projectName, branch, filePath))
                    .toList();
        }

        @Override
        public GitLabRepositoryFileContent readFile(
                String group,
                String projectName,
                String branch,
                String filePath,
                int maxCharacters
        ) {
            return new GitLabRepositoryFileContent(
                    group,
                    projectName,
                    branch,
                    filePath,
                    files.getOrDefault(filePath, ""),
                    false
            );
        }

        @Override
        public GitLabRepositoryFileChunk readFileChunk(
                String group,
                String projectName,
                String branch,
                String filePath,
                int startLine,
                int endLine,
                int maxCharacters
        ) {
            return new GitLabRepositoryFileChunk(
                    group,
                    projectName,
                    branch,
                    filePath,
                    startLine,
                    endLine,
                    startLine,
                    endLine,
                    0,
                    "",
                    false
            );
        }
    }
}
