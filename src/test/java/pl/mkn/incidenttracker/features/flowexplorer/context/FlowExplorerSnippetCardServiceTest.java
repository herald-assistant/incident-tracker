package pl.mkn.incidenttracker.features.flowexplorer.context;

import org.junit.jupiter.api.Test;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerDocumentationPreset;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerFocusArea;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileChunk;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.incidenttracker.integrations.gitlab.source.GitLabJavaMethodSliceMethodCandidate;
import pl.mkn.incidenttracker.integrations.gitlab.source.GitLabJavaMethodSliceRequest;
import pl.mkn.incidenttracker.integrations.gitlab.source.GitLabJavaMethodSliceResponse;
import pl.mkn.incidenttracker.integrations.gitlab.source.GitLabJavaMethodSliceService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FlowExplorerSnippetCardServiceTest {

    private final GitLabRepositoryPort repositoryPort = mock(GitLabRepositoryPort.class);
    private final GitLabJavaMethodSliceService javaMethodSliceService = mock(GitLabJavaMethodSliceService.class);
    private final FlowExplorerSnippetCardService service = new FlowExplorerSnippetCardService(
            repositoryPort,
            javaMethodSliceService
    );

    @Test
    void shouldBuildSnippetCardsFromMethodSlicesForPrioritizedFlowNodes() {
        when(javaMethodSliceService.readMethodSlice(any()))
                .thenAnswer(invocation -> methodSliceResponse(invocation.getArgument(0)));

        var result = service.buildSnippetCards(
                "platform/backend",
                "feature/FLOW-42",
                repository(),
                List.of(
                        node("src/main/java/app/CustomerController.java", "CONTROLLER", "getCustomer", 12, 24),
                        node("src/main/java/app/CustomerService.java", "USE_CASE_SERVICE", "getCustomer", 40, 65),
                        node("src/main/java/app/CustomerRepository.java", "SPRING_DATA_REPOSITORY", "findCustomer", 8, 12),
                        node("src/main/java/app/CustomerMapper.java", "MAPPER", "toResponse", 20, 32)
                ),
                FlowExplorerDocumentationPreset.ANALYST_OVERVIEW,
                List.of(FlowExplorerFocusArea.PERSISTENCE)
        );

        assertEquals(4, result.cards().size());
        assertFalse(result.budgetReached());
        assertEquals("CONTROLLER", result.cards().get(0).role());
        assertEquals("USE_CASE_SERVICE", result.cards().get(1).role());
        assertEquals("SPRING_DATA_REPOSITORY", result.cards().get(2).role());
        assertTrue(result.cards().get(0).content().contains("public void getCustomer"));
        assertTrue(result.limitations().stream()
                .noneMatch(limitation -> limitation.contains("maxCards=20")));
        verify(javaMethodSliceService, times(4)).readMethodSlice(argThat(request ->
                "platform/backend".equals(request.group())
                        && "crm-service".equals(request.projectName())
                        && "feature/FLOW-42".equals(request.branch())
                        && request.methodSelectors().stream().allMatch(selector -> selector.lineStart() == null)
                        && Boolean.TRUE.equals(request.includeDirectPrivateHelpers())
                        && Boolean.TRUE.equals(request.includeRelevantFields())
                        && Boolean.TRUE.equals(request.includeRelevantImports())
                        && request.maxCharacters() == GitLabJavaMethodSliceService.MAX_OUTPUT_CHARACTERS
        ));
        verify(repositoryPort, never()).readFileChunk(
                anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt()
        );
    }

    @Test
    void shouldLimitSnippetCardsToTwentyFlowNodes() {
        when(javaMethodSliceService.readMethodSlice(any()))
                .thenAnswer(invocation -> methodSliceResponse(invocation.getArgument(0)));

        var result = service.buildSnippetCards(
                "platform/backend",
                "feature/FLOW-42",
                repository(),
                java.util.stream.IntStream.rangeClosed(1, 21)
                        .mapToObj(index -> node(
                                "src/main/java/app/CustomerFlowStep" + index + ".java",
                                "USE_CASE_SERVICE",
                                "step" + index,
                                index * 10,
                                index * 10 + 5
                        ))
                        .toList(),
                FlowExplorerDocumentationPreset.ANALYST_OVERVIEW,
                List.of(FlowExplorerFocusArea.BUSINESS_FLOW)
        );

        assertEquals(20, result.cards().size());
        assertTrue(result.budgetReached());
        assertTrue(result.limitations().stream()
                .anyMatch(limitation -> limitation.contains("maxCards=20")));
        verify(javaMethodSliceService, times(20)).readMethodSlice(any());
    }

    @Test
    void shouldFallbackToLineChunkWhenMethodSliceDoesNotReturnContent() {
        when(javaMethodSliceService.readMethodSlice(any()))
                .thenAnswer(invocation -> methodSliceFailure(invocation.getArgument(0), "NOT_FOUND"));
        when(repositoryPort.readFileChunk(anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt()))
                .thenAnswer(invocation -> chunk(
                        invocation.getArgument(3),
                        invocation.getArgument(4),
                        invocation.getArgument(5),
                        "public void selectedMethod() {\n    service.call();\n}",
                        false
                ));

        var result = service.buildSnippetCards(
                "platform/backend",
                "main",
                repository(),
                List.of(node("src/main/java/app/CustomerController.java", "CONTROLLER", "getCustomer", 12, 24)),
                FlowExplorerDocumentationPreset.ANALYST_OVERVIEW,
                List.of()
        );

        assertEquals(1, result.cards().size());
        assertTrue(result.cards().get(0).content().contains("// ... omitted earlier lines ..."));
        assertTrue(result.cards().get(0).content().contains("public void selectedMethod"));
        assertTrue(result.limitations().stream()
                .anyMatch(limitation -> limitation.contains("Method slice returned NOT_FOUND")));
        verify(javaMethodSliceService).readMethodSlice(any());
        verify(repositoryPort).readFileChunk(
                anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt()
        );
    }

    @Test
    void shouldSurfaceReadFailuresAsLimitations() {
        when(javaMethodSliceService.readMethodSlice(any()))
                .thenAnswer(invocation -> methodSliceFailure(invocation.getArgument(0), "NOT_FOUND"));
        when(repositoryPort.readFileChunk(anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt(), anyInt()))
                .thenThrow(new IllegalStateException("GitLab unavailable"));

        var result = service.buildSnippetCards(
                "platform/backend",
                "main",
                repository(),
                List.of(node("src/main/java/app/CustomerController.java", "CONTROLLER", "getCustomer", 12, 24)),
                FlowExplorerDocumentationPreset.ANALYST_OVERVIEW,
                List.of()
        );

        assertTrue(result.cards().isEmpty());
        assertFalse(result.budgetReached());
        assertTrue(result.limitations().stream()
                .anyMatch(limitation -> limitation.contains("GitLab unavailable")));
    }

    @Test
    void shouldSkipSnippetCardsWhenRepositoryIsMissing() {
        var result = service.buildSnippetCards(
                "platform/backend",
                "main",
                null,
                List.of(node("src/main/java/app/CustomerController.java", "CONTROLLER", "getCustomer", 12, 24)),
                FlowExplorerDocumentationPreset.ANALYST_OVERVIEW,
                List.of()
        );

        assertTrue(result.cards().isEmpty());
        assertTrue(result.limitations().stream()
                .anyMatch(limitation -> limitation.contains("selected repository is not resolved")));
    }

    private static GitLabJavaMethodSliceResponse methodSliceResponse(GitLabJavaMethodSliceRequest request) {
        var selector = request.methodSelectors().get(0);
        var content = """
                package app;

                public class %s {
                    public void %s() {
                        service.call();
                    }
                }
                """.formatted(simpleClassName(request.filePath()), selector.methodName()).strip();
        return new GitLabJavaMethodSliceResponse(
                request.group(),
                request.projectName(),
                request.branch(),
                request.filePath(),
                "OK",
                simpleClassName(request.filePath()),
                request.methodSelectors(),
                12,
                24,
                80,
                content,
                content.length(),
                false,
                List.of(),
                List.of("service"),
                List.of(new GitLabJavaMethodSliceMethodCandidate(
                        simpleClassName(request.filePath()),
                        selector.methodName(),
                        "public void " + selector.methodName() + "()",
                        12,
                        24,
                        0,
                        List.of()
                )),
                0,
                1,
                List.of(),
                List.of()
        );
    }

    private static GitLabJavaMethodSliceResponse methodSliceFailure(
            GitLabJavaMethodSliceRequest request,
            String status
    ) {
        return new GitLabJavaMethodSliceResponse(
                request.group(),
                request.projectName(),
                request.branch(),
                request.filePath(),
                status,
                null,
                request.methodSelectors(),
                0,
                0,
                80,
                "",
                0,
                false,
                List.of(),
                List.of(),
                List.of(),
                0,
                0,
                List.of(),
                List.of("No method matched requested selectors.")
        );
    }

    private static FlowExplorerRepositoryContext repository() {
        return new FlowExplorerRepositoryContext(
                "crm-service",
                "crm-service",
                "platform/backend/crm-service",
                "feature/FLOW-42",
                true,
                true,
                List.of()
        );
    }

    private static FlowExplorerFlowNode node(String filePath, String role, String methodName, int lineStart, int lineEnd) {
        return new FlowExplorerFlowNode(
                filePath,
                role,
                filePath,
                List.of(new FlowExplorerFlowMethod(methodName, lineStart, lineEnd)),
                "Selected because it participates in endpoint flow.",
                "HIGH",
                List.of()
        );
    }

    private static GitLabRepositoryFileChunk chunk(
            String filePath,
            int startLine,
            int endLine,
            String content,
            boolean truncated
    ) {
        return new GitLabRepositoryFileChunk(
                "platform/backend",
                "crm-service",
                "feature/FLOW-42",
                filePath,
                startLine,
                endLine,
                startLine,
                endLine,
                endLine + 20,
                content,
                truncated
        );
    }

    private static String simpleClassName(String filePath) {
        var fileName = filePath.substring(filePath.lastIndexOf('/') + 1);
        return fileName.substring(0, fileName.length() - ".java".length());
    }
}
