package pl.mkn.incidenttracker.analysis.ai.copilot.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.evidence.AnalysisAiToolEvidenceListener;
import pl.mkn.incidenttracker.analysis.ai.evidence.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.evidence.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.evidence.AnalysisEvidenceSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Optional;

@Component
@Slf4j
public class CopilotToolEvidenceSessionStore {

    private static final String TOOL_CAPTURE_ORDER_ATTRIBUTE = "toolCaptureOrder";

    private final Map<String, SessionToolEvidence> sessions = new ConcurrentHashMap<>();

    public void registerSession(String sessionId, AnalysisAiToolEvidenceListener listener) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }

        sessions.put(
                sessionId,
                new SessionToolEvidence(
                        listener != null ? listener : AnalysisAiToolEvidenceListener.NO_OP
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
            session.listener().onToolEvidenceUpdated(section);
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
            AnalysisAiToolEvidenceListener listener,
            LinkedHashMap<EvidenceSectionKey, LinkedHashMap<String, AnalysisEvidenceItem>> itemsBySection,
            LinkedHashMap<String, Integer> orderByItemKey,
            AtomicInteger nextOrder
    ) {
        SessionToolEvidence(AnalysisAiToolEvidenceListener listener) {
            this(
                    listener,
                    new LinkedHashMap<>(),
                    new LinkedHashMap<>(),
                    new AtomicInteger(1)
            );
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
}
