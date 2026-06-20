package pl.mkn.incidenttracker.integrations.gitlab.source;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFile;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileChunk;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileContent;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryProjectCandidate;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositorySearchQuery;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GitLabJavaMethodSliceServiceTest {

    private static final String FILE_PATH = "src/main/java/com/example/orders/OrderService.java";

    private final GitLabJavaMethodSliceService service = new GitLabJavaMethodSliceService(new SliceRepositoryPort());

    @Test
    void shouldRenderFocusedMethodSliceWithRelevantFieldsImportsAndHelper() {
        var response = service.readMethodSlice(new GitLabJavaMethodSliceRequest(
                "CRM",
                "orders",
                "main",
                FILE_PATH,
                "OrderService",
                List.of(new GitLabJavaMethodSliceMethodSelector("createOrder", 18)),
                true,
                true,
                true,
                12_000
        ));

        assertEquals("OK", response.status());
        assertEquals("com.example.orders.OrderService", response.declaringTypeName());
        assertEquals(List.of(new GitLabJavaMethodSliceMethodSelector("createOrder", 18)), response.requestedMethods());
        assertFalse(response.truncated());
        assertTrue(response.includedFields().contains("orderRepository"));
        assertTrue(response.includedFields().contains("auditClient"));
        assertTrue(response.includedFields().contains("clock"));
        assertFalse(response.includedFields().contains("noiseClient"));
        assertTrue(response.includedMethods().stream().anyMatch(method -> "createOrder".equals(method.methodName())));
        assertTrue(response.includedMethods().stream().anyMatch(method -> "map".equals(method.methodName())));

        var content = response.content();
        assertTrue(content.contains("import java.time.Clock;"));
        assertTrue(content.contains("import java.time.Instant;"));
        assertTrue(content.contains("import lombok.RequiredArgsConstructor;"));
        assertFalse(content.contains("import java.util.UUID;"));
        assertTrue(content.contains("private final OrderRepository orderRepository;"));
        assertTrue(content.contains("private final AuditClient auditClient;"));
        assertFalse(content.contains("private final NoiseClient noiseClient;"));
        assertTrue(content.contains("public OrderResult createOrder"));
        assertTrue(content.contains("private OrderEntity map"));
        assertFalse(content.contains("public void unrelated"));
        assertTrue(content.contains("// ... omitted unrelated fields ..."));
        assertTrue(content.contains("// ... omitted unrelated methods ..."));
    }

    @Test
    void shouldRenderAllOverloadsWhenLineStartIsNotProvided() {
        var response = service.readMethodSlice(new GitLabJavaMethodSliceRequest(
                "CRM",
                "orders",
                "main",
                FILE_PATH,
                "OrderService",
                List.of(new GitLabJavaMethodSliceMethodSelector("createOrder", null)),
                true,
                true,
                true,
                12_000
        ));

        assertEquals("OK", response.status());
        assertTrue(response.candidates().isEmpty());
        assertEquals(2, response.includedMethods().stream()
                .filter(method -> "createOrder".equals(method.methodName()))
                .count());
        assertTrue(response.content().contains("public OrderResult createOrder(OrderRequest request)"));
        assertTrue(response.content().contains("public OrderResult createOrder(String externalId)"));
        assertTrue(response.content().contains("private OrderEntity map"));
    }

    private static final class SliceRepositoryPort implements GitLabRepositoryPort {

        @Override
        public List<GitLabRepositoryProjectCandidate> searchProjects(String group, List<String> projectHints) {
            return List.of();
        }

        @Override
        public List<GitLabRepositoryFileCandidate> searchCandidateFiles(GitLabRepositorySearchQuery query) {
            return List.of();
        }

        @Override
        public List<GitLabRepositoryFile> listRepositoryFiles(String group, String projectName, String branch, String pathPrefix) {
            return List.of();
        }

        @Override
        public GitLabRepositoryFileContent readFile(
                String group,
                String projectName,
                String branch,
                String filePath,
                int maxCharacters
        ) {
            return new GitLabRepositoryFileContent(group, projectName, branch, filePath, source(), false);
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
            throw new UnsupportedOperationException("not used");
        }

        private String source() {
            return """
                    package com.example.orders;

                    import java.time.Clock;
                    import java.time.Instant;
                    import java.util.UUID;
                    import lombok.RequiredArgsConstructor;
                    import org.springframework.stereotype.Service;

                    @Service
                    @RequiredArgsConstructor
                    public class OrderService {

                        private final OrderRepository orderRepository;
                        private final AuditClient auditClient;
                        private final Clock clock;
                        private final NoiseClient noiseClient;

                        public OrderResult createOrder(OrderRequest request) {
                            var entity = map(request);
                            orderRepository.save(entity);
                            auditClient.record(entity.id(), Instant.now(clock));
                            return new OrderResult(entity.id());
                        }

                        public OrderResult createOrder(String externalId) {
                            return new OrderResult(externalId);
                        }

                        private OrderEntity map(OrderRequest request) {
                            return new OrderEntity(request.customerId());
                        }

                        public void unrelated() {
                            noiseClient.call(UUID.randomUUID().toString());
                        }
                    }
                    """;
        }
    }
}
