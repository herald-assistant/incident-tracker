package pl.mkn.tdw.aiplatform.copilot.tools.evidence;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

@Component
@Slf4j
public class CopilotToolEvidenceSessionStore {

    private static final String TOOL_CAPTURE_ORDER_ATTRIBUTE = "toolCaptureOrder";
    private static final int MAX_INVOCATION_HISTORY = 40;
    private static final Consumer<AnalysisEvidenceSection> NO_OP_EVIDENCE_SINK = section -> {
    };

    private final Map<String, SessionToolEvidence> sessions = new ConcurrentHashMap<>();

    public void registerSession(String sessionId, Consumer<AnalysisEvidenceSection> evidenceSink) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }

        sessions.put(
                sessionId,
                new SessionToolEvidence(
                        evidenceSink != null ? evidenceSink : NO_OP_EVIDENCE_SINK
                )
        );
    }

    public void unregisterSession(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }

        sessions.remove(sessionId);
    }

    public Optional<SessionToolEvidence> sessionEvidence(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessions.get(sessionId));
    }

    public void publishSection(String sessionId, String toolName, AnalysisEvidenceSection section) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        if (section == null || !section.hasItems()) {
            return;
        }

        var session = sessions.get(sessionId);
        if (session == null) {
            return;
        }

        try {
            session.evidenceSink().accept(section);
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

    public static record SessionToolEvidence(
            Consumer<AnalysisEvidenceSection> evidenceSink,
            LinkedHashMap<EvidenceSectionKey, LinkedHashMap<String, AnalysisEvidenceItem>> itemsBySection,
            LinkedHashMap<String, Integer> orderByItemKey,
            AtomicInteger nextOrder,
            Deque<ToolInvocationSummary> invocationHistory
    ) {
        SessionToolEvidence(Consumer<AnalysisEvidenceSection> evidenceSink) {
            this(
                    evidenceSink,
                    new LinkedHashMap<>(),
                    new LinkedHashMap<>(),
                    new AtomicInteger(1),
                    new ArrayDeque<>()
            );
        }

        public synchronized void recordInvocation(ToolInvocationSummary invocation) {
            if (invocation == null || !StringUtils.hasText(invocation.toolName())) {
                return;
            }

            invocationHistory.addLast(invocation);
            while (invocationHistory.size() > MAX_INVOCATION_HISTORY) {
                invocationHistory.removeFirst();
            }
        }

        public synchronized Optional<ToolInvocationSummary> findInvocationByCallId(String toolCallId) {
            if (!StringUtils.hasText(toolCallId)) {
                return Optional.empty();
            }

            var iterator = invocationHistory.descendingIterator();
            while (iterator.hasNext()) {
                var invocation = iterator.next();
                if (toolCallId.trim().equals(invocation.toolCallId())) {
                    return Optional.of(invocation);
                }
            }
            return Optional.empty();
        }

        public synchronized Optional<ToolInvocationSummary> findLatestInvocationByName(String toolName) {
            if (!StringUtils.hasText(toolName)) {
                return Optional.empty();
            }

            var iterator = invocationHistory.descendingIterator();
            while (iterator.hasNext()) {
                var invocation = iterator.next();
                if (toolName.trim().equals(invocation.toolName())) {
                    return Optional.of(invocation);
                }
            }
            return Optional.empty();
        }

        public synchronized Optional<ToolInvocationSummary> findLatestInvocation() {
            return Optional.ofNullable(invocationHistory.peekLast());
        }

        public synchronized AnalysisEvidenceSection upsertItem(
                String provider,
                String category,
                String key,
                String orderNamespace,
                String fallbackKey,
                AnalysisEvidenceItem candidate,
                BiPredicate<AnalysisEvidenceItem, AnalysisEvidenceItem> keepExisting
        ) {
            var sectionKey = new EvidenceSectionKey(provider, category);
            var items = itemsBySection.computeIfAbsent(sectionKey, ignored -> new LinkedHashMap<>());
            var itemKey = itemKey(key, fallbackKey);
            var current = items.get(itemKey);
            if (current != null && keepExisting != null && keepExisting.test(current, candidate)) {
                return toSection(sectionKey, items);
            }

            var orderedCandidate = withCaptureOrder(
                    candidate,
                    captureOrder(orderNamespace, itemKey)
            );
            items.put(itemKey, orderedCandidate);
            return toSection(sectionKey, items);
        }

        public synchronized AnalysisEvidenceSection appendItem(
                String provider,
                String category,
                String key,
                String orderNamespace,
                String fallbackKey,
                AnalysisEvidenceItem candidate
        ) {
            var sectionKey = new EvidenceSectionKey(provider, category);
            var items = itemsBySection.computeIfAbsent(sectionKey, ignored -> new LinkedHashMap<>());
            putAppendedItem(items, key, orderNamespace, fallbackKey, candidate);
            return toSection(sectionKey, items);
        }

        private AnalysisEvidenceSection toSection(
                EvidenceSectionKey sectionKey,
                LinkedHashMap<String, AnalysisEvidenceItem> items
        ) {
            return new AnalysisEvidenceSection(
                    sectionKey.provider(),
                    sectionKey.category(),
                    List.copyOf(items.values())
            );
        }

        private String itemKey(
                String key,
                String fallbackKey
        ) {
            var effectiveKey = key;
            if (!StringUtils.hasText(effectiveKey)) {
                effectiveKey = fallbackKey;
            }
            if (!StringUtils.hasText(effectiveKey)) {
                effectiveKey = "tool-item";
            }
            return effectiveKey;
        }

        private void putAppendedItem(
                LinkedHashMap<String, AnalysisEvidenceItem> items,
                String key,
                String orderNamespace,
                String fallbackKey,
                AnalysisEvidenceItem candidate
        ) {
            var effectiveKey = itemKey(key, fallbackKey);
            if (items.containsKey(effectiveKey)) {
                effectiveKey = effectiveKey + "::" + (items.size() + 1);
            }
            var orderedCandidate = withCaptureOrder(
                    candidate,
                    captureOrder(orderNamespace, effectiveKey)
            );
            items.put(effectiveKey, orderedCandidate);
        }

        private int captureOrder(String namespace, String key) {
            var orderKey = namespace + "::" + key;
            return orderByItemKey.computeIfAbsent(
                    orderKey,
                    ignored -> nextOrder.getAndIncrement()
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
    }

    private record EvidenceSectionKey(String provider, String category) {
    }

    public record ToolInvocationSummary(
            String toolName,
            String toolCallId,
            String outcome,
            String rawArguments,
            String rawResult,
            long latencyMs
    ) {
    }
}
