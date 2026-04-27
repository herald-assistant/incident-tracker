package pl.mkn.incidenttracker.analysis.ai.copilot.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisAiToolEvidenceListener;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@Slf4j
public class CopilotToolEvidenceCaptureRegistry {

    private static final String DATABASE_PROVIDER = "database";
    private static final String DATABASE_TOOL_CATEGORY = "tool-results";
    private static final String GITLAB_PROVIDER = "gitlab";
    private static final String GITLAB_TOOL_CATEGORY = "tool-fetched-code";
    private static final String TOOL_CAPTURE_ORDER_ATTRIBUTE = "toolCaptureOrder";

    private final GitLabToolEvidenceMapper gitLabMapper;
    private final DatabaseToolEvidenceMapper databaseMapper;
    private final Map<String, SessionArtifactAccumulator> sessionAccumulators = new ConcurrentHashMap<>();

    @Autowired
    public CopilotToolEvidenceCaptureRegistry(
            GitLabToolEvidenceMapper gitLabMapper,
            DatabaseToolEvidenceMapper databaseMapper
    ) {
        this.gitLabMapper = gitLabMapper;
        this.databaseMapper = databaseMapper;
    }

    public void registerSession(String sessionId, AnalysisAiToolEvidenceListener listener) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }

        sessionAccumulators.put(
                sessionId,
                new SessionArtifactAccumulator(
                        listener != null ? listener : AnalysisAiToolEvidenceListener.NO_OP
                )
        );
    }

    public void unregisterSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }

        sessionAccumulators.remove(sessionId);
    }

    public void captureToolResult(
            String sessionId,
            String toolCallId,
            String toolName,
            String rawArguments,
            String rawResult
    ) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(toolName) || !StringUtils.hasText(rawResult)) {
            return;
        }

        var accumulator = sessionAccumulators.get(sessionId);
        if (accumulator == null) {
            return;
        }

        AnalysisEvidenceSection updatedSection = null;
        if (gitLabMapper.supports(toolName)) {
            updatedSection = gitLabMapper.capture(toolCallId, toolName, rawArguments, rawResult, accumulator);
        } else if (databaseMapper.supports(toolName)) {
            updatedSection = databaseMapper.capture(toolCallId, toolName, rawArguments, rawResult, accumulator);
        }

        if (updatedSection == null || !updatedSection.hasItems()) {
            return;
        }

        try {
            accumulator.listener().onToolEvidenceUpdated(updatedSection);
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to publish captured tool evidence sessionId={} toolName={} reason={}",
                    sessionId,
                    toolName,
                    exception.getMessage(),
                    exception
            );
        }
    }

    static record SessionArtifactAccumulator(
            AnalysisAiToolEvidenceListener listener,
            LinkedHashMap<String, AnalysisEvidenceItem> gitLabItems,
            LinkedHashMap<String, AnalysisEvidenceItem> databaseItems,
            LinkedHashMap<String, Integer> itemCaptureOrders,
            AtomicInteger nextCaptureOrder
    ) {
        SessionArtifactAccumulator(AnalysisAiToolEvidenceListener listener) {
            this(
                    listener,
                    new LinkedHashMap<>(),
                    new LinkedHashMap<>(),
                    new LinkedHashMap<>(),
                    new AtomicInteger(1)
            );
        }

        synchronized AnalysisEvidenceSection upsertGitLabItem(String key, AnalysisEvidenceItem candidate) {
            var current = gitLabItems.get(key);
            if (current != null && !isChunkItem(current) && isChunkItem(candidate)) {
                return new AnalysisEvidenceSection(
                        GITLAB_PROVIDER,
                        GITLAB_TOOL_CATEGORY,
                        List.copyOf(gitLabItems.values())
                );
            }

            var orderedCandidate = withCaptureOrder(
                    candidate,
                    captureOrderFor("gitlab", key)
            );
            gitLabItems.put(key, orderedCandidate);
            return new AnalysisEvidenceSection(
                    GITLAB_PROVIDER,
                    GITLAB_TOOL_CATEGORY,
                    List.copyOf(gitLabItems.values())
            );
        }

        synchronized AnalysisEvidenceSection appendDatabaseItem(
                String key,
                AnalysisEvidenceItem candidate
        ) {
            appendItem(databaseItems, key, "database", "db-tool", candidate);
            return new AnalysisEvidenceSection(
                    DATABASE_PROVIDER,
                    DATABASE_TOOL_CATEGORY,
                    List.copyOf(databaseItems.values())
            );
        }

        private void appendItem(
                LinkedHashMap<String, AnalysisEvidenceItem> items,
                String key,
                String orderNamespace,
                String fallbackKey,
                AnalysisEvidenceItem candidate
        ) {
            var effectiveKey = key;
            if (!StringUtils.hasText(effectiveKey) || items.containsKey(effectiveKey)) {
                effectiveKey = (StringUtils.hasText(key) ? key : fallbackKey)
                        + "::"
                        + (items.size() + 1);
            }

            var orderedCandidate = withCaptureOrder(
                    candidate,
                    captureOrderFor(orderNamespace, effectiveKey)
            );
            items.put(effectiveKey, orderedCandidate);
        }

        private int captureOrderFor(String namespace, String key) {
            var orderKey = namespace + "::" + key;
            return itemCaptureOrders.computeIfAbsent(
                    orderKey,
                    ignored -> nextCaptureOrder.getAndIncrement()
            );
        }

        private AnalysisEvidenceItem withCaptureOrder(AnalysisEvidenceItem item, int captureOrder) {
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            for (var attribute : item.attributes()) {
                if (!TOOL_CAPTURE_ORDER_ATTRIBUTE.equals(attribute.name())) {
                    attributes.add(attribute);
                }
            }
            attributes.add(new AnalysisEvidenceAttribute(
                    TOOL_CAPTURE_ORDER_ATTRIBUTE,
                    String.valueOf(captureOrder)
            ));

            return new AnalysisEvidenceItem(item.title(), List.copyOf(attributes));
        }

        private static boolean isChunkItem(AnalysisEvidenceItem item) {
            return item.attributes().stream()
                    .anyMatch(attribute -> "startLine".equals(attribute.name()));
        }
    }
}
