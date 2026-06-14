package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLabEndpointUseCaseResultCompressorTest {

    private final GitLabEndpointUseCaseResultCompressor compressor = new GitLabEndpointUseCaseResultCompressor();

    @Test
    void shouldMergeSortAndSuggestNextReads() {
        var repository = new GitLabEndpointUseCaseRepositoryContext("CRM", "crm-product-service", "main", "src/main/java");
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
        var repository = new GitLabEndpointUseCaseRepositoryContext("CRM", "crm-product-service", "main", "src/main/java");
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
