package pl.mkn.tdw.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLabEndpointUseCaseResultCompressorTest {

    private final GitLabEndpointUseCaseResultCompressor compressor = new GitLabEndpointUseCaseResultCompressor();

    @Test
    void shouldMergeSortAndSuggestNextReads() {
        var repository = new GitLabEndpointUseCaseRepositoryContext("CRM", "crm-customer-service", "main");
        var result = compressor.compress(new GitLabEndpointUseCaseContextResult(
                repository,
                null,
                List.of(
                        file("src/main/java/com/example/CustomerMapper.java", GitLabEndpointUseCaseFileRole.MAPPER, 5,
                                List.of("from"),
                                List.of(method("src/main/java/com/example/CustomerMapper.java", "CustomerMapper",
                                        "from", 20, 24)),
                                "mapper call", GitLabEndpointUseCaseConfidence.MEDIUM),
                        file("src/main/java/com/example/CustomerController.java", GitLabEndpointUseCaseFileRole.CONTROLLER, 1,
                                List.of("getCustomer"), "endpoint", GitLabEndpointUseCaseConfidence.HIGH),
                        file("src/main/java/com/example/CustomerMapper.java", GitLabEndpointUseCaseFileRole.MAPPER, 5,
                                List.of("toWeb"),
                                List.of(method("src/main/java/com/example/CustomerMapper.java", "CustomerMapper",
                                        "toWeb", 30, 34)),
                                "mapper default method", GitLabEndpointUseCaseConfidence.HIGH)
                ),
                List.of(),
                List.of(),
                List.of(" raw limitation ", "raw limitation"),
                List.of(),
                new GitLabEndpointUseCaseLimits(5, 25, 60, false, false, 3, false),
                GitLabEndpointUseCaseConfidence.LOW
        ));

        assertEquals(2, result.files().size());
        assertEquals("src/main/java/com/example/CustomerController.java", result.files().get(0).path());
        assertEquals("src/main/java/com/example/CustomerMapper.java", result.files().get(1).path());
        assertEquals(List.of("from", "toWeb"), result.files().get(1).symbols());
        assertEquals(List.of("from", "toWeb"), result.files().get(1).methods().stream()
                .map(GitLabEndpointUseCaseMethodCandidate::methodName)
                .toList());
        assertEquals(GitLabEndpointUseCaseConfidence.HIGH, result.files().get(1).confidence());
        assertEquals(List.of("raw limitation"), result.limitations());
        assertEquals(2, result.suggestedNextReads().size());
        assertTrue(result.suggestedNextReads().get(0)
                .contains("crm-customer-service:src/main/java/com/example/CustomerController.java"));
        assertTrue(result.suggestedNextReads().get(0).contains("symbols: getCustomer"));
        assertEquals(GitLabEndpointUseCaseConfidence.HIGH, result.confidence());
    }

    @Test
    void shouldTruncateFilesAfterPrioritySorting() {
        var repository = new GitLabEndpointUseCaseRepositoryContext("CRM", "crm-customer-service", "main");
        var result = compressor.compress(new GitLabEndpointUseCaseContextResult(
                repository,
                null,
                List.of(
                        file("src/main/java/com/example/Domain.java", GitLabEndpointUseCaseFileRole.DOMAIN_MODEL, 6,
                                List.of("update"), "domain", GitLabEndpointUseCaseConfidence.HIGH),
                        file("src/main/java/com/example/Controller.java", GitLabEndpointUseCaseFileRole.CONTROLLER, 1,
                                List.of("update"), "controller", GitLabEndpointUseCaseConfidence.HIGH),
                        file("src/main/java/com/example/Service.java", GitLabEndpointUseCaseFileRole.USE_CASE_SERVICE, 2,
                                List.of("update"), "service", GitLabEndpointUseCaseConfidence.HIGH)
                ),
                List.of(),
                List.of(new GitLabEndpointUseCaseUnresolvedReference(
                        "MissingType",
                        "src/main/java/com/example/Controller.java",
                        "missing candidate",
                        List.of("MissingType"),
                        List.of()
                )),
                List.of(),
                List.of(),
                new GitLabEndpointUseCaseLimits(5, 2, 60, false, false, 3, false),
                GitLabEndpointUseCaseConfidence.HIGH
        ));

        assertEquals(2, result.files().size());
        assertEquals("src/main/java/com/example/Controller.java", result.files().get(0).path());
        assertEquals("src/main/java/com/example/Service.java", result.files().get(1).path());
        assertTrue(result.limits().maxFilesReached());
        assertTrue(result.limitations().stream()
                .anyMatch(limitation -> limitation.contains("truncated from 3 to maxFiles=2")));
        assertEquals(GitLabEndpointUseCaseConfidence.MEDIUM, result.confidence());
    }

    @Test
    void shouldPromoteSingleUnresolvedSourceCandidateToSuggestedFile() {
        var repository = new GitLabEndpointUseCaseRepositoryContext("CRM", "crm-customer-service", "main");
        var result = compressor.compress(new GitLabEndpointUseCaseContextResult(
                repository,
                null,
                List.of(file("src/main/java/com/example/CustomerController.java", GitLabEndpointUseCaseFileRole.CONTROLLER, 1,
                        List.of("updateCustomer"), "endpoint", GitLabEndpointUseCaseConfidence.HIGH)),
                List.of(),
                List.of(new GitLabEndpointUseCaseUnresolvedReference(
                        "CustomerMapper",
                        "src/main/java/com/example/CustomerController.java",
                        "Source read file limit reached before reading mapper.",
                        List.of("CustomerMapper"),
                        List.of("src/main/java/com/example/CustomerMapper.java")
                )),
                List.of(),
                List.of(),
                new GitLabEndpointUseCaseLimits(5, 25, 60, false, false, 60, true),
                GitLabEndpointUseCaseConfidence.MEDIUM
        ));

        assertEquals(2, result.files().size());
        var promoted = result.files().get(1);
        assertEquals("src/main/java/com/example/CustomerMapper.java", promoted.path());
        assertEquals(GitLabEndpointUseCaseFileRole.MAPPER, promoted.role());
        assertEquals(List.of("CustomerMapper"), promoted.symbols());
        assertEquals(GitLabEndpointUseCaseConfidence.MEDIUM, promoted.confidence());
        assertTrue(promoted.reason().contains("Exact source candidate was found"));
        assertTrue(result.suggestedNextReads().stream()
                .anyMatch(read -> read.contains("crm-customer-service:src/main/java/com/example/CustomerMapper.java")
                        && read.contains("symbols: CustomerMapper")));
        assertEquals(GitLabEndpointUseCaseConfidence.MEDIUM, result.confidence());
    }

    @Test
    void shouldNotPromoteAmbiguousOrNonSourceUnresolvedCandidates() {
        var repository = new GitLabEndpointUseCaseRepositoryContext("CRM", "crm-customer-service", "main");
        var result = compressor.compress(new GitLabEndpointUseCaseContextResult(
                repository,
                null,
                List.of(file("src/main/java/com/example/CustomerController.java", GitLabEndpointUseCaseFileRole.CONTROLLER, 1,
                        List.of("updateCustomer"), "endpoint", GitLabEndpointUseCaseConfidence.HIGH)),
                List.of(),
                List.of(
                        new GitLabEndpointUseCaseUnresolvedReference(
                                "Customer",
                                "src/main/java/com/example/CustomerController.java",
                                "More than one source file matched.",
                                List.of("Customer"),
                                List.of(
                                        "src/main/java/com/example/api/Customer.java",
                                        "src/main/java/com/example/domain/Customer.java"
                                )
                        ),
                        new GitLabEndpointUseCaseUnresolvedReference(
                                "ResponseEntity",
                                "src/main/java/com/example/CustomerController.java",
                                "Framework type.",
                                List.of("ResponseEntity"),
                                List.of("org.springframework.http.ResponseEntity")
                        )
                ),
                List.of(),
                List.of(),
                new GitLabEndpointUseCaseLimits(5, 25, 60, false, false, 4, false),
                GitLabEndpointUseCaseConfidence.MEDIUM
        ));

        assertEquals(1, result.files().size());
        assertEquals("src/main/java/com/example/CustomerController.java", result.files().get(0).path());
        assertEquals(1, result.suggestedNextReads().size());
    }

    private GitLabEndpointUseCaseFileCandidate file(
            String path,
            GitLabEndpointUseCaseFileRole role,
            int priority,
            List<String> symbols,
            String reason,
            GitLabEndpointUseCaseConfidence confidence
    ) {
        return new GitLabEndpointUseCaseFileCandidate(path, role, priority, symbols, reason, confidence);
    }

    private GitLabEndpointUseCaseFileCandidate file(
            String path,
            GitLabEndpointUseCaseFileRole role,
            int priority,
            List<String> symbols,
            List<GitLabEndpointUseCaseMethodCandidate> methods,
            String reason,
            GitLabEndpointUseCaseConfidence confidence
    ) {
        return new GitLabEndpointUseCaseFileCandidate(path, role, priority, symbols, methods, reason, confidence);
    }

    private GitLabEndpointUseCaseMethodCandidate method(
            String filePath,
            String typeName,
            String methodName,
            int lineStart,
            int lineEnd
    ) {
        return new GitLabEndpointUseCaseMethodCandidate(
                filePath,
                typeName,
                typeName,
                "com.example." + typeName,
                GitLabJavaTypeKind.CLASS,
                methodName,
                null,
                lineStart,
                lineEnd,
                0,
                List.of(),
                List.of(),
                "CustomerModel",
                List.of("public"),
                GitLabEndpointUseCaseFileRole.MAPPER,
                5,
                1,
                "mapper call",
                GitLabEndpointUseCaseConfidence.HIGH
        );
    }
}
