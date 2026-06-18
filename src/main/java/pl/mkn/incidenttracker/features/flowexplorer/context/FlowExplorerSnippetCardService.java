package pl.mkn.incidenttracker.features.flowexplorer.context;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerDocumentationPreset;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerFocusArea;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryFileChunk;
import pl.mkn.incidenttracker.integrations.gitlab.GitLabRepositoryPort;

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
    private static final int DEFAULT_MAX_CARDS = 3;
    private static final int TECHNICAL_MAX_CARDS = 4;
    private static final int MAX_CHARACTERS_PER_CARD = 6_000;
    private static final int MAX_TOTAL_CHARACTERS = 14_000;

    private final GitLabRepositoryPort gitLabRepositoryPort;

    public FlowExplorerSnippetCardResult buildSnippetCards(
            String gitLabGroup,
            String resolvedRef,
            FlowExplorerRepositoryContext repository,
            List<FlowExplorerFlowNode> flowNodes,
            FlowExplorerDocumentationPreset preset,
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

        var eligibleNodes = eligibleNodes(flowNodes, preset, focusAreas);
        if (eligibleNodes.isEmpty()) {
            return FlowExplorerSnippetCardResult.empty();
        }

        var maxCards = maxCards(preset);
        var cards = new ArrayList<FlowExplorerSnippetCard>();
        var limitations = new ArrayList<String>();
        var totalCharacters = 0;
        var budgetReached = false;

        for (var node : eligibleNodes) {
            if (cards.size() >= maxCards) {
                budgetReached = true;
                break;
            }
            var remainingCharacters = MAX_TOTAL_CHARACTERS - totalCharacters;
            if (remainingCharacters <= 0) {
                budgetReached = true;
                break;
            }

            var lineRange = requestedLineRange(node);
            if (lineRange == null) {
                limitations.add("Snippet card skipped for " + node.filePath()
                        + " because method line range is missing.");
                continue;
            }

            try {
                var chunk = gitLabRepositoryPort.readFileChunk(
                        gitLabGroup,
                        repository.projectName(),
                        resolvedRef,
                        node.filePath(),
                        lineRange.startLine(),
                        lineRange.endLine(),
                        Math.min(MAX_CHARACTERS_PER_CARD, remainingCharacters)
                );
                var card = snippetCard(repository, node, lineRange, chunk);
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
            limitations.add("Snippet card budget reached before all eligible flow nodes were embedded.");
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
            FlowExplorerDocumentationPreset preset,
            List<FlowExplorerFocusArea> focusAreas
    ) {
        var safeFocusAreas = focusAreas != null
                ? focusAreas.stream().filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet())
                : Set.<FlowExplorerFocusArea>of();
        return flowNodes != null
                ? flowNodes.stream()
                .filter(node -> StringUtils.hasText(node.filePath()))
                .filter(node -> !node.methods().isEmpty())
                .filter(node -> node.methods().stream().anyMatch(method -> method.lineStart() > 0))
                .sorted(Comparator
                        .comparingInt((FlowExplorerFlowNode node) -> priority(node, preset, safeFocusAreas))
                        .thenComparing(FlowExplorerFlowNode::filePath))
                .toList()
                : List.of();
    }

    private int priority(
            FlowExplorerFlowNode node,
            FlowExplorerDocumentationPreset preset,
            Set<FlowExplorerFocusArea> focusAreas
    ) {
        var role = normalizeRole(node.role());
        var score = baseRoleScore(role);

        if (focusAreas.contains(FlowExplorerFocusArea.PERSISTENCE) && persistenceRole(role)) {
            score -= 35;
        }
        if (focusAreas.contains(FlowExplorerFocusArea.EXTERNAL_INTEGRATIONS) && externalIntegrationRole(role)) {
            score -= 35;
        }
        if (focusAreas.contains(FlowExplorerFocusArea.VALIDATIONS) && validationRole(role)) {
            score -= 25;
        }
        if (preset == FlowExplorerDocumentationPreset.TECHNICAL_HANDOFF && technicalRole(role)) {
            score -= 15;
        }
        if (preset == FlowExplorerDocumentationPreset.CHANGE_IMPACT && changeImpactRole(role)) {
            score -= 15;
        }
        if (preset == FlowExplorerDocumentationPreset.TEST_PREPARATION && validationRole(role)) {
            score -= 10;
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

    private boolean changeImpactRole(String role) {
        return technicalRole(role) || "DOMAIN_MODEL".equals(role) || "WEB_MODEL".equals(role);
    }

    private int maxCards(FlowExplorerDocumentationPreset preset) {
        return preset == FlowExplorerDocumentationPreset.TECHNICAL_HANDOFF
                || preset == FlowExplorerDocumentationPreset.CHANGE_IMPACT
                ? TECHNICAL_MAX_CARDS
                : DEFAULT_MAX_CARDS;
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
        var limitations = new ArrayList<String>(node.limitations());
        if (chunk.truncated()) {
            limitations.add("Snippet content was truncated by character budget.");
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
                    .append("// ... omitted unrelated or later lines ...");
        }
        return content.toString().strip();
    }

    private String cardId(String projectName, String filePath, LineRange lineRange) {
        return projectName + ":" + filePath + ":L" + lineRange.startLine() + "-L" + lineRange.endLine();
    }

    private String normalizeRole(String role) {
        return StringUtils.hasText(role) ? role.trim().toUpperCase() : "UNKNOWN";
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
}
