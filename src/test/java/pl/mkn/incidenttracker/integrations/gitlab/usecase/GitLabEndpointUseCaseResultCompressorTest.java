package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLabEndpointUseCaseResultCompressorTest {

    private final GitLabEndpointUseCaseResultCompressor compressor = new GitLabEndpointUseCaseResultCompressor();

    @Test
    void shouldMergeSortAndSuggestNextReads() {
        var repository = new GitLabEndpointUseCaseRepositoryContext("CRM", "crm-product-service", "main");
        var result = compressor.compress(new GitLabEndpointUseCaseContextResult(
                repository,
                null,
                List.of(
                        file("src/main/java/com/example/ProductMapper.java", GitLabEndpointUseCaseFileRole.MAPPER, 5,
                                List.of("from"), "mapper call", GitLabEndpointUseCaseConfidence.MEDIUM),
                        file("src/main/java/com/example/ProductController.java", GitLabEndpointUseCaseFileRole.CONTROLLER, 1,
                                List.of("getProduct"), "endpoint", GitLabEndpointUseCaseConfidence.HIGH),
                        file("src/main/java/com/example/ProductMapper.java", GitLabEndpointUseCaseFileRole.MAPPER, 5,
                                List.of("toWeb"), "mapper default method", GitLabEndpointUseCaseConfidence.HIGH)
                ),
                List.of(),
                List.of(),
                List.of(" raw limitation ", "raw limitation"),
                List.of(),
                new GitLabEndpointUseCaseLimits(5, 25, 60, false, false, 3, false),
                GitLabEndpointUseCaseConfidence.LOW
        ));

        assertEquals(2, result.files().size());
        assertEquals("src/main/java/com/example/ProductController.java", result.files().get(0).path());
        assertEquals("src/main/java/com/example/ProductMapper.java", result.files().get(1).path());
        assertEquals(List.of("from", "toWeb"), result.files().get(1).symbols());
        assertEquals(GitLabEndpointUseCaseConfidence.HIGH, result.files().get(1).confidence());
        assertEquals(List.of("raw limitation"), result.limitations());
        assertEquals(2, result.suggestedNextReads().size());
        assertTrue(result.suggestedNextReads().get(0)
                .contains("crm-product-service:src/main/java/com/example/ProductController.java"));
        assertTrue(result.suggestedNextReads().get(0).contains("symbols: getProduct"));
        assertEquals(GitLabEndpointUseCaseConfidence.HIGH, result.confidence());
    }

    @Test
    void shouldTruncateFilesAfterPrioritySorting() {
        var repository = new GitLabEndpointUseCaseRepositoryContext("CRM", "crm-product-service", "main");
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
        var repository = new GitLabEndpointUseCaseRepositoryContext("CRM", "crm-product-service", "main");
        var result = compressor.compress(new GitLabEndpointUseCaseContextResult(
                repository,
                null,
                List.of(file("src/main/java/com/example/ProductController.java", GitLabEndpointUseCaseFileRole.CONTROLLER, 1,
                        List.of("updateProduct"), "endpoint", GitLabEndpointUseCaseConfidence.HIGH)),
                List.of(),
                List.of(new GitLabEndpointUseCaseUnresolvedReference(
                        "ProductWebModelMapper",
                        "src/main/java/com/example/ProductController.java",
                        "Source read file limit reached before reading mapper.",
                        List.of("ProductWebModelMapper"),
                        List.of("src/main/java/com/example/ProductWebModelMapper.java")
                )),
                List.of(),
                List.of(),
                new GitLabEndpointUseCaseLimits(5, 25, 60, false, false, 60, true),
                GitLabEndpointUseCaseConfidence.MEDIUM
        ));

        assertEquals(2, result.files().size());
        var promoted = result.files().get(1);
        assertEquals("src/main/java/com/example/ProductWebModelMapper.java", promoted.path());
        assertEquals(GitLabEndpointUseCaseFileRole.MAPPER, promoted.role());
        assertEquals(List.of("ProductWebModelMapper"), promoted.symbols());
        assertEquals(GitLabEndpointUseCaseConfidence.MEDIUM, promoted.confidence());
        assertTrue(promoted.reason().contains("Exact source candidate was found"));
        assertTrue(result.suggestedNextReads().stream()
                .anyMatch(read -> read.contains("crm-product-service:src/main/java/com/example/ProductWebModelMapper.java")
                        && read.contains("symbols: ProductWebModelMapper")));
        assertEquals(GitLabEndpointUseCaseConfidence.MEDIUM, result.confidence());
    }

    @Test
    void shouldNotPromoteAmbiguousOrNonSourceUnresolvedCandidates() {
        var repository = new GitLabEndpointUseCaseRepositoryContext("CRM", "crm-product-service", "main");
        var result = compressor.compress(new GitLabEndpointUseCaseContextResult(
                repository,
                null,
                List.of(file("src/main/java/com/example/ProductController.java", GitLabEndpointUseCaseFileRole.CONTROLLER, 1,
                        List.of("updateProduct"), "endpoint", GitLabEndpointUseCaseConfidence.HIGH)),
                List.of(),
                List.of(
                        new GitLabEndpointUseCaseUnresolvedReference(
                                "Product",
                                "src/main/java/com/example/ProductController.java",
                                "More than one source file matched.",
                                List.of("Product"),
                                List.of(
                                        "src/main/java/com/example/api/Product.java",
                                        "src/main/java/com/example/domain/Product.java"
                                )
                        ),
                        new GitLabEndpointUseCaseUnresolvedReference(
                                "ResponseEntity",
                                "src/main/java/com/example/ProductController.java",
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
        assertEquals("src/main/java/com/example/ProductController.java", result.files().get(0).path());
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
}
