package pl.mkn.tdw.features.flowexplorer.context;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerAnalysisGoal;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerFocusArea;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileChunk;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.source.GitLabJavaMethodSliceMethodSelector;
import pl.mkn.tdw.integrations.gitlab.source.GitLabJavaMethodSliceRequest;
import pl.mkn.tdw.integrations.gitlab.source.GitLabJavaMethodSliceResponse;
import pl.mkn.tdw.integrations.gitlab.source.GitLabJavaMethodSliceService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FlowExplorerSnippetCardService {

    private static final int CONTEXT_LINES = 3;
    private static final int MAX_CARDS = 20;
    private static final int GITLAB_METHOD_SLICE_MAX_CHARACTERS = GitLabJavaMethodSliceService.MAX_OUTPUT_CHARACTERS;
    private static final String METHOD_SLICE_OK_STATUS = "OK";

    private final GitLabRepositoryPort gitLabRepositoryPort;
    private final GitLabJavaMethodSliceService javaMethodSliceService;

    public FlowExplorerSnippetCardResult buildSnippetCards(
            String gitLabGroup,
            String resolvedRef,
            FlowExplorerRepositoryContext repository,
            List<FlowExplorerFlowNode> flowNodes,
            FlowExplorerAnalysisGoal goal,
            List<FlowExplorerFocusArea> focusAreas
    ) {
        if (repository == null || !StringUtils.hasText(repository.projectName())) {
            return new FlowExplorerSnippetCardResult(
                    List.of(),
                    List.of("Snippet cards skipped because selected repository is not resolved."),
                    false,
                    0
            );
        }

        var eligibleNodes = eligibleNodes(flowNodes, goal, focusAreas);
        if (eligibleNodes.isEmpty()) {
            return FlowExplorerSnippetCardResult.empty();
        }

        var cards = new ArrayList<FlowExplorerSnippetCard>();
        var limitations = new ArrayList<String>();
        var totalCharacters = 0;
        var budgetReached = false;

        for (var node : eligibleNodes) {
            if (cards.size() >= MAX_CARDS) {
                budgetReached = true;
                break;
            }
            var boundaryLimitations = focusedReadBoundaryLimitations(repository, node.filePath());

            try {
                var attempt = tryBuildMethodSliceCard(
                        gitLabGroup,
                        resolvedRef,
                        repository,
                        node,
                        boundaryLimitations
                );
                var card = attempt.card() != null
                        ? attempt.card()
                        : fallbackSnippetCard(
                                gitLabGroup,
                                resolvedRef,
                                repository,
                                node,
                                mergeLimitations(boundaryLimitations, attempt.limitations())
                        );
                cards.add(card);
                totalCharacters += card.characterCount();
                limitations.addAll(card.limitations().stream()
                        .map(limitation -> node.filePath() + ": " + limitation)
                        .toList());
            } catch (RuntimeException exception) {
                limitations.add("Could not read snippet card " + repository.projectName()
                        + ":" + node.filePath() + ": " + safeMessage(exception));
            }
        }

        if (budgetReached) {
            limitations.add("Snippet card list was truncated to maxCards=" + MAX_CARDS
                    + " before all eligible flow nodes were embedded.");
        }

        return new FlowExplorerSnippetCardResult(
                cards,
                distinct(limitations),
                budgetReached,
                totalCharacters
        );
    }

    private List<FlowExplorerFlowNode> eligibleNodes(
            List<FlowExplorerFlowNode> flowNodes,
            FlowExplorerAnalysisGoal goal,
            List<FlowExplorerFocusArea> focusAreas
    ) {
        var safeFocusAreas = focusAreas != null
                ? focusAreas.stream().filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet())
                : Set.<FlowExplorerFocusArea>of();
        return flowNodes != null
                ? flowNodes.stream()
                .filter(node -> StringUtils.hasText(node.filePath()))
                .filter(node -> !node.methods().isEmpty())
                .filter(node -> !methodSelectors(node).isEmpty())
                .sorted(Comparator
                        .comparingInt((FlowExplorerFlowNode node) -> priority(node, goal, safeFocusAreas))
                        .thenComparing(FlowExplorerFlowNode::filePath))
                .toList()
                : List.of();
    }

    private int priority(
            FlowExplorerFlowNode node,
            FlowExplorerAnalysisGoal goal,
            Set<FlowExplorerFocusArea> focusAreas
    ) {
        var role = normalizeRole(node.role());
        var score = baseRoleScore(role);

        if (focusAreas.contains(FlowExplorerFocusArea.PERSISTENCE) && persistenceRole(role)) {
            score -= 35;
        }
        if (focusAreas.contains(FlowExplorerFocusArea.INTEGRATIONS) && externalIntegrationRole(role)) {
            score -= 35;
        }
        if (focusAreas.contains(FlowExplorerFocusArea.VALIDATIONS) && validationRole(role)) {
            score -= 25;
        }

        return Math.max(0, score);
    }

    private int baseRoleScore(String role) {
        return switch (role) {
            case "CONTROLLER" -> 10;
            case "USE_CASE_SERVICE" -> 20;
            case "USE_CASE_PORT" -> 30;
            case "EXTERNAL_CLIENT" -> 55;
            case "REPOSITORY_IMPLEMENTATION", "SPRING_DATA_REPOSITORY", "REPOSITORY_PORT" -> 60;
            case "MAPPER" -> 75;
            case "DOMAIN_MODEL", "WEB_MODEL", "PROJECTION" -> 85;
            case "API_INTERFACE", "OPENAPI_CONTRACT" -> 90;
            default -> 100;
        };
    }

    private boolean persistenceRole(String role) {
        return role.contains("REPOSITORY") || "DOMAIN_MODEL".equals(role) || "PROJECTION".equals(role);
    }

    private boolean externalIntegrationRole(String role) {
        return "EXTERNAL_CLIENT".equals(role);
    }

    private boolean validationRole(String role) {
        return role.contains("VALID") || "WEB_MODEL".equals(role) || "API_INTERFACE".equals(role);
    }

    private boolean technicalRole(String role) {
        return persistenceRole(role)
                || externalIntegrationRole(role)
                || "MAPPER".equals(role)
                || "CONFIGURATION".equals(role);
    }

    private MethodSliceAttempt tryBuildMethodSliceCard(
            String gitLabGroup,
            String resolvedRef,
            FlowExplorerRepositoryContext repository,
            FlowExplorerFlowNode node,
            List<String> boundaryLimitations
    ) {
        if (!javaFile(node.filePath())) {
            return MethodSliceAttempt.empty();
        }

        var methodSelectors = methodSelectors(node);
        if (methodSelectors.isEmpty()) {
            return MethodSliceAttempt.empty();
        }

        var limitations = new ArrayList<String>();
        try {
            var response = javaMethodSliceService.readMethodSlice(new GitLabJavaMethodSliceRequest(
                    gitLabGroup,
                    repository.projectName(),
                    resolvedRef,
                    node.filePath(),
                    null,
                    methodSelectors,
                    true,
                    true,
                    true,
                    GITLAB_METHOD_SLICE_MAX_CHARACTERS
            ));
            if (!usableMethodSlice(response)) {
                limitations.add("Method slice returned " + safeStatus(response)
                        + " and snippet card fell back to line chunk.");
                if (response != null) {
                    limitations.addAll(response.limitations());
                }
                return new MethodSliceAttempt(null, distinct(limitations));
            }
            return new MethodSliceAttempt(methodSliceCard(repository, node, response, boundaryLimitations), List.of());
        } catch (RuntimeException exception) {
            limitations.add("Method slice failed and snippet card fell back to line chunk: "
                    + safeMessage(exception));
            return new MethodSliceAttempt(null, distinct(limitations));
        }
    }

    private FlowExplorerSnippetCard fallbackSnippetCard(
            String gitLabGroup,
            String resolvedRef,
            FlowExplorerRepositoryContext repository,
            FlowExplorerFlowNode node,
            List<String> fallbackLimitations
    ) {
        var lineRange = requestedLineRange(node);
        if (lineRange == null) {
            throw new IllegalStateException("Method line range is missing for fallback snippet card.");
        }

        var chunk = gitLabRepositoryPort.readFileChunk(
                gitLabGroup,
                repository.projectName(),
                resolvedRef,
                node.filePath(),
                lineRange.startLine(),
                lineRange.endLine(),
                GITLAB_METHOD_SLICE_MAX_CHARACTERS
        );
        return snippetCard(repository, node, lineRange, chunk, fallbackLimitations);
    }

    private boolean javaFile(String filePath) {
        return StringUtils.hasText(filePath) && filePath.trim().toLowerCase(java.util.Locale.ROOT).endsWith(".java");
    }

    private List<GitLabJavaMethodSliceMethodSelector> methodSelectors(FlowExplorerFlowNode node) {
        if (node == null || node.methods() == null) {
            return List.of();
        }
        var methodNames = new LinkedHashSet<String>();
        for (var method : node.methods()) {
            if (method != null && StringUtils.hasText(method.methodName())) {
                methodNames.add(method.methodName().trim());
            }
        }
        return methodNames.stream()
                .limit(20)
                .map(methodName -> new GitLabJavaMethodSliceMethodSelector(methodName, null))
                .toList();
    }

    private boolean usableMethodSlice(GitLabJavaMethodSliceResponse response) {
        return response != null
                && METHOD_SLICE_OK_STATUS.equals(response.status())
                && StringUtils.hasText(response.content());
    }

    private String safeStatus(GitLabJavaMethodSliceResponse response) {
        return response != null && StringUtils.hasText(response.status())
                ? response.status()
                : "without content";
    }

    private LineRange requestedLineRange(FlowExplorerFlowNode node) {
        var methods = node.methods().stream()
                .filter(method -> method.lineStart() > 0)
                .toList();
        if (methods.isEmpty()) {
            return null;
        }

        var start = methods.stream()
                .mapToInt(FlowExplorerFlowMethod::lineStart)
                .min()
                .orElse(0);
        var end = methods.stream()
                .mapToInt(FlowExplorerFlowMethod::lineEnd)
                .max()
                .orElse(start);
        return new LineRange(Math.max(1, start - CONTEXT_LINES), Math.max(start, end + CONTEXT_LINES));
    }

    private FlowExplorerSnippetCard snippetCard(
            FlowExplorerRepositoryContext repository,
            FlowExplorerFlowNode node,
            LineRange requestedLineRange,
            GitLabRepositoryFileChunk chunk
    ) {
        return snippetCard(repository, node, requestedLineRange, chunk, List.of());
    }

    private FlowExplorerSnippetCard snippetCard(
            FlowExplorerRepositoryContext repository,
            FlowExplorerFlowNode node,
            LineRange requestedLineRange,
            GitLabRepositoryFileChunk chunk,
            List<String> extraLimitations
    ) {
        var limitations = new ArrayList<String>(node.limitations());
        limitations.addAll(extraLimitations != null ? extraLimitations : List.of());
        if (chunk.truncated()) {
            limitations.add("Snippet content was truncated by GitLab read output limit.");
        }

        var content = renderSnippetContent(repository, node, chunk);
        return new FlowExplorerSnippetCard(
                cardId(repository.projectName(), node.filePath(), requestedLineRange),
                repository.projectName(),
                node.filePath(),
                node.role(),
                node.methods(),
                requestedLineRange.startLine(),
                requestedLineRange.endLine(),
                chunk.returnedStartLine(),
                chunk.returnedEndLine(),
                chunk.totalLines(),
                chunk.truncated(),
                node.reason(),
                content,
                content.length(),
                limitations
        );
    }

    private FlowExplorerSnippetCard methodSliceCard(
            FlowExplorerRepositoryContext repository,
            FlowExplorerFlowNode node,
            GitLabJavaMethodSliceResponse response,
            List<String> extraLimitations
    ) {
        var requestedLineRange = requestedLineRange(node);
        var requestedStartLine = requestedLineRange != null ? requestedLineRange.startLine() : response.returnedLineStart();
        var requestedEndLine = requestedLineRange != null ? requestedLineRange.endLine() : response.returnedLineEnd();
        var limitations = new ArrayList<String>(node.limitations());
        limitations.addAll(extraLimitations != null ? extraLimitations : List.of());
        limitations.addAll(response.limitations());
        if (response.truncated()) {
            limitations.add("Snippet content was truncated by GitLab method slice output limit.");
        }

        return new FlowExplorerSnippetCard(
                cardId(repository.projectName(), node.filePath(), response.returnedLineStart(), response.returnedLineEnd()),
                repository.projectName(),
                node.filePath(),
                node.role(),
                node.methods(),
                requestedStartLine,
                requestedEndLine,
                response.returnedLineStart(),
                response.returnedLineEnd(),
                response.totalLines(),
                response.truncated(),
                node.reason(),
                response.content().strip(),
                response.returnedCharacters(),
                distinct(limitations)
        );
    }

    private String renderSnippetContent(
            FlowExplorerRepositoryContext repository,
            FlowExplorerFlowNode node,
            GitLabRepositoryFileChunk chunk
    ) {
        var content = new StringBuilder();
        content.append("// project: ").append(repository.projectName()).append(System.lineSeparator());
        content.append("// file: ").append(node.filePath()).append(System.lineSeparator());
        content.append("// role: ").append(node.role()).append(System.lineSeparator());
        content.append("// lines: L").append(chunk.returnedStartLine())
                .append("-L").append(chunk.returnedEndLine())
                .append(System.lineSeparator());
        if (chunk.returnedStartLine() > 1) {
            content.append("// ... omitted earlier lines ...").append(System.lineSeparator());
        }
        content.append(chunk.content() != null ? chunk.content().strip() : "");
        if (chunk.returnedEndLine() > 0 && chunk.totalLines() > chunk.returnedEndLine()) {
            content.append(System.lineSeparator())
                    .append("// ... omitted later lines ...");
        }
        return content.toString().strip();
    }

    private String cardId(String projectName, String filePath, LineRange lineRange) {
        return cardId(projectName, filePath, lineRange.startLine(), lineRange.endLine());
    }

    private String cardId(String projectName, String filePath, int startLine, int endLine) {
        return projectName + ":" + filePath + ":L" + startLine + "-L" + endLine;
    }

    private String normalizeRole(String role) {
        return StringUtils.hasText(role) ? role.trim().toUpperCase() : "UNKNOWN";
    }

    private boolean pathWithinBoundary(FlowExplorerRepositoryContext repository, String filePath) {
        if (repository == null || repository.pathPrefixes().isEmpty()) {
            return true;
        }
        var normalizedPath = normalizeFilePath(filePath);
        if (!StringUtils.hasText(normalizedPath)) {
            return false;
        }
        return repository.pathPrefixes().stream()
                .map(this::normalizeFilePath)
                .filter(StringUtils::hasText)
                .anyMatch(prefix -> normalizedPath.equals(prefix) || normalizedPath.startsWith(prefix + "/"));
    }

    private List<String> focusedReadBoundaryLimitations(FlowExplorerRepositoryContext repository, String filePath) {
        if (pathWithinBoundary(repository, filePath)) {
            return List.of();
        }
        return List.of("File is outside default repository discovery scope and was read because it was explicitly requested.");
    }

    private List<String> mergeLimitations(List<String> first, List<String> second) {
        var merged = new ArrayList<String>();
        merged.addAll(first != null ? first : List.of());
        merged.addAll(second != null ? second : List.of());
        return distinct(merged);
    }

    private String normalizeFilePath(String path) {
        if (!StringUtils.hasText(path)) {
            return null;
        }
        var normalized = path.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private List<String> distinct(List<String> values) {
        var distinctValues = new LinkedHashSet<String>();
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                distinctValues.add(value.trim());
            }
        }
        return List.copyOf(distinctValues);
    }

    private String safeMessage(RuntimeException exception) {
        return StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
    }

    private record LineRange(int startLine, int endLine) {
    }

    private record MethodSliceAttempt(FlowExplorerSnippetCard card, List<String> limitations) {
        private MethodSliceAttempt {
            limitations = limitations != null ? List.copyOf(limitations) : List.of();
        }

        private static MethodSliceAttempt empty() {
            return new MethodSliceAttempt(null, List.of());
        }
    }
}
