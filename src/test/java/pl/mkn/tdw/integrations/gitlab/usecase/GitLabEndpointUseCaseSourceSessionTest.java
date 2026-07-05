package pl.mkn.tdw.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFile;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitLabEndpointUseCaseSourceSessionTest {

    private final GitLabRepositoryPort repositoryPort = mock(GitLabRepositoryPort.class);
    private final GitLabEndpointUseCaseRepositoryContext repository = new GitLabEndpointUseCaseRepositoryContext(
            "CRM",
            "crm-customer-service",
            "main"
    );

    @Test
    void shouldCacheReadFileByRepositoryPathAndCharacterLimit() {
        when(repositoryPort.readFile("CRM", "crm-customer-service", "main",
                "src/main/java/com/example/crm/CustomerService.java", 120_000))
                .thenReturn(new GitLabRepositoryFileContent(
                        "CRM",
                        "crm-customer-service",
                        "main",
                        "src/main/java/com/example/crm/CustomerService.java",
                        "package com.example.crm; class CustomerService {}",
                        false
                ));
        var session = new GitLabEndpointUseCaseSourceSession(repositoryPort, repository);

        var first = session.readFile("\\src\\main\\java\\com\\example\\crm\\CustomerService.java");
        var second = session.readFile("src/main/java/com/example/crm/CustomerService.java");

        assertSame(first, second);
        assertTrue(first.readSuccessful());
        assertEquals("package com.example.crm; class CustomerService {}", first.content());
        assertEquals(1, session.readFileCount());
        verify(repositoryPort, times(1)).readFile("CRM", "crm-customer-service", "main",
                "src/main/java/com/example/crm/CustomerService.java", 120_000);
    }

    @Test
    void shouldCacheRepositoryFilesByPathPrefix() {
        var files = List.of(new GitLabRepositoryFile(
                "CRM",
                "crm-customer-service",
                "main",
                "src/main/java/com/example/crm/CustomerService.java"
        ));
        when(repositoryPort.listRepositoryFiles("CRM", "crm-customer-service", "main", "src/main/java"))
                .thenReturn(files);
        var session = new GitLabEndpointUseCaseSourceSession(repositoryPort, repository);

        var first = session.listRepositoryFiles("/src/main/java/");
        var second = session.listRepositoryFiles("src/main/java");

        assertEquals(files, first);
        assertEquals(files, second);
        verify(repositoryPort, times(1)).listRepositoryFiles("CRM", "crm-customer-service", "main", "src/main/java");
    }

    @Test
    void shouldReturnLimitationWhenReadFileLimitIsReached() {
        when(repositoryPort.readFile("CRM", "crm-customer-service", "main",
                "src/main/java/com/example/crm/CustomerService.java", 20))
                .thenReturn(new GitLabRepositoryFileContent(
                        "CRM",
                        "crm-customer-service",
                        "main",
                        "src/main/java/com/example/crm/CustomerService.java",
                        "class CustomerService {}",
                        false
                ));
        var session = new GitLabEndpointUseCaseSourceSession(repositoryPort, repository, 1, 20);

        var first = session.readFile("src/main/java/com/example/crm/CustomerService.java");
        var second = session.readFile("src/main/java/com/example/crm/CustomerProfileRepository.java");

        assertTrue(first.readSuccessful());
        assertFalse(second.readSuccessful());
        assertTrue(second.limitations().contains(
                "Source read file limit reached before reading src/main/java/com/example/crm/CustomerProfileRepository.java."
        ));
        assertEquals(1, session.readFileCount());
        assertTrue(session.readFileLimitReached());
        assertEquals(1, session.limitsSnapshot(5, 25).readFileCount());
        assertTrue(session.limitsSnapshot(5, 25).readFileLimitReached());
    }

    @Test
    void shouldParseJava21SourceAndCacheAst() {
        when(repositoryPort.readFile("CRM", "crm-customer-service", "main",
                "src/main/java/com/example/crm/CustomerClassifier.java", 120_000))
                .thenReturn(new GitLabRepositoryFileContent(
                        "CRM",
                        "crm-customer-service",
                        "main",
                        "src/main/java/com/example/crm/CustomerClassifier.java",
                        """
                                package com.example.crm;

                                sealed interface Customer permits RetailCustomer {
                                }

                                record RetailCustomer(String segment) implements Customer {
                                }

                                class CustomerClassifier {
                                    String segment(Customer customer) {
                                        return switch (customer) {
                                            case RetailCustomer retail -> retail.segment();
                                        };
                                    }
                                }
                                """,
                        false
                ));
        var session = new GitLabEndpointUseCaseSourceSession(repositoryPort, repository);

        var first = session.parseJava("src/main/java/com/example/crm/CustomerClassifier.java");
        var second = session.parseJava("src/main/java/com/example/crm/CustomerClassifier.java");

        assertSame(first, second);
        assertTrue(first.parseSuccessful(), () -> String.join("\n", first.limitations()));
        assertTrue(first.hasCompilationUnit());
        assertNotNull(first.compilationUnit().getPackageDeclaration().orElseThrow());
        assertEquals("com.example.crm", first.compilationUnit().getPackageDeclaration().orElseThrow().getNameAsString());
        verify(repositoryPort, times(1)).readFile("CRM", "crm-customer-service", "main",
                "src/main/java/com/example/crm/CustomerClassifier.java", 120_000);
    }

    @Test
    void shouldReturnParseLimitationForInvalidJavaWithoutThrowing() {
        when(repositoryPort.readFile("CRM", "crm-customer-service", "main",
                "src/main/java/com/example/crm/BrokenCustomer.java", 120_000))
                .thenReturn(new GitLabRepositoryFileContent(
                        "CRM",
                        "crm-customer-service",
                        "main",
                        "src/main/java/com/example/crm/BrokenCustomer.java",
                        "package com.example.crm; class BrokenCustomer { void broken( }",
                        false
                ));
        var session = new GitLabEndpointUseCaseSourceSession(repositoryPort, repository);

        var parsed = session.parseJava("src/main/java/com/example/crm/BrokenCustomer.java");

        assertFalse(parsed.parseSuccessful());
        assertFalse(parsed.limitations().isEmpty());
        assertTrue(parsed.limitations().get(0).startsWith(
                "Could not parse Java source src/main/java/com/example/crm/BrokenCustomer.java:"
        ));
    }
}
