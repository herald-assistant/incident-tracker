package pl.mkn.tdw.api.analysisruns;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionCleanup;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAuthMode;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuth;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunChatHandler;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunContinuation;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunContinuationException;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;

import java.util.List;

@Service
@Slf4j
public class AnalysisRunHistoryService {

    private final LocalAnalysisRunStore localAnalysisRunStore;
    private final List<LocalAnalysisRunChatHandler> chatHandlers;
    private final CopilotSessionCleanup copilotSessionCleanup;

    public AnalysisRunHistoryService(
            LocalAnalysisRunStore localAnalysisRunStore,
            List<LocalAnalysisRunChatHandler> chatHandlers,
            CopilotSessionCleanup copilotSessionCleanup
    ) {
        this.localAnalysisRunStore = localAnalysisRunStore;
        this.chatHandlers = chatHandlers != null ? List.copyOf(chatHandlers) : List.of();
        this.copilotSessionCleanup = copilotSessionCleanup != null
                ? copilotSessionCleanup
                : CopilotSessionCleanup.NO_OP;
    }

    public LocalAnalysisRunListResponse listRuns() {
        return new LocalAnalysisRunListResponse(
                localAnalysisRunStore.listRuns().stream()
                        .map(this::toListItem)
                        .toList()
        );
    }

    public LocalAnalysisRunDetailResponse getRun(String analysisId) {
        var indexEntry = indexEntryOrThrow(analysisId);
        var record = recordOrThrow(indexEntry.analysisId());
        return toDetail(indexEntry, record);
    }

    public JsonNode exportRun(String analysisId) {
        var indexEntry = indexEntryOrThrow(analysisId);
        var record = recordOrThrow(indexEntry.analysisId());
        return record.exportEnvelope();
    }

    public LocalAnalysisRunDetailResponse renameRun(String analysisId, String name) {
        var indexEntry = indexEntryOrThrow(analysisId);
        localAnalysisRunStore.rename(indexEntry.analysisId(), name.trim());
        return getRun(indexEntry.analysisId());
    }

    public LocalAnalysisRunDetailResponse sendChatMessage(
            String analysisId,
            LocalAnalysisRunChatMessageRequest request
    ) {
        var indexEntry = indexEntryOrThrow(analysisId);
        var record = recordOrThrow(indexEntry.analysisId());
        if (record.continuation() == null || !record.continuation().enabled()) {
            throw new LocalAnalysisRunContinuationUnavailableException(
                    "Local run cannot be continued because continuation metadata is disabled."
            );
        }

        var handler = chatHandler(indexEntry.feature());
        try {
            var result = handler.continueRun(indexEntry, record, request.message());
            var updatedEntry = indexEntry.withUpdatedAt(result.updatedAt());
            localAnalysisRunStore.save(updatedEntry, result.record());
            return toDetail(updatedEntry, result.record());
        } catch (LocalAnalysisRunContinuationException exception) {
            throw mapContinuationException(indexEntry.analysisId(), exception);
        }
    }

    public void deleteRun(String analysisId) {
        var indexEntry = indexEntryOrThrow(analysisId);
        cleanupCopilotSession(indexEntry.analysisId());
        localAnalysisRunStore.delete(indexEntry.analysisId());
    }

    private void cleanupCopilotSession(String analysisId) {
        var continuation = localAnalysisRunStore.findById(analysisId)
                .map(LocalAnalysisRunRecord::continuation)
                .orElse(null);
        if (continuation == null || !StringUtils.hasText(continuation.copilotSessionId())) {
            return;
        }
        if (StringUtils.hasText(continuation.copilotRuntime())
                && !LocalAnalysisRunContinuation.COPILOT_RUNTIME_GITHUB_COPILOT_SDK.equals(continuation.copilotRuntime())) {
            return;
        }

        try {
            copilotSessionCleanup.deleteSession(continuation.copilotSessionId(), copilotAuth(continuation));
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to cleanup Copilot session while deleting local run analysisId={} sessionId={} reason={}",
                    analysisId,
                    continuation.copilotSessionId(),
                    exception.getMessage(),
                    exception
            );
        }
    }

    private CopilotRunAuth copilotAuth(LocalAnalysisRunContinuation continuation) {
        var mode = CopilotAuthMode.from(continuation.authMode());
        return new CopilotRunAuth(
                mode,
                continuation.authPrincipalRef(),
                null,
                mode == CopilotAuthMode.GITHUB_APP
        );
    }

    private LocalAnalysisRunIndexEntry indexEntryOrThrow(String analysisId) {
        var normalizedAnalysisId = requireAnalysisId(analysisId);
        return localAnalysisRunStore.listRuns().stream()
                .filter(entry -> entry.analysisId().equals(normalizedAnalysisId))
                .findFirst()
                .orElseThrow(() -> new LocalAnalysisRunNotFoundException(normalizedAnalysisId));
    }

    private LocalAnalysisRunRecord recordOrThrow(String analysisId) {
        var normalizedAnalysisId = requireAnalysisId(analysisId);
        return localAnalysisRunStore.findById(normalizedAnalysisId)
                .orElseThrow(() -> new LocalAnalysisRunCorruptedException(normalizedAnalysisId));
    }

    private LocalAnalysisRunChatHandler chatHandler(String feature) {
        return chatHandlers.stream()
                .filter(handler -> handler.feature().equals(feature))
                .findFirst()
                .orElseThrow(() -> new LocalAnalysisRunContinuationUnavailableException(
                        "Local run feature does not support continuation yet: " + feature
                ));
    }

    private RuntimeException mapContinuationException(
            String analysisId,
            LocalAnalysisRunContinuationException exception
    ) {
        return switch (exception.reason()) {
            case CORRUPTED -> new LocalAnalysisRunCorruptedException(analysisId);
            case CHAT_FAILED -> new LocalAnalysisRunChatFailedException(
                    StringUtils.hasText(exception.getMessage())
                            ? exception.getMessage()
                            : "Local follow-up chat failed."
            );
            case UNAVAILABLE -> new LocalAnalysisRunContinuationUnavailableException(
                    StringUtils.hasText(exception.getMessage())
                            ? exception.getMessage()
                            : "Local run cannot be continued."
            );
        };
    }

    private LocalAnalysisRunListItemResponse toListItem(LocalAnalysisRunIndexEntry entry) {
        return new LocalAnalysisRunListItemResponse(
                entry.analysisId(),
                entry.feature(),
                entry.name(),
                entry.status(),
                entry.createdAt(),
                entry.updatedAt(),
                entry.completedAt()
        );
    }

    private LocalAnalysisRunDetailResponse toDetail(
            LocalAnalysisRunIndexEntry entry,
            LocalAnalysisRunRecord record
    ) {
        return new LocalAnalysisRunDetailResponse(
                entry.analysisId(),
                entry.feature(),
                entry.name(),
                entry.status(),
                entry.createdAt(),
                entry.updatedAt(),
                entry.completedAt(),
                record.exportEnvelope(),
                record.continuation() != null && record.continuation().enabled()
        );
    }

    private String requireAnalysisId(String analysisId) {
        if (!StringUtils.hasText(analysisId)) {
            throw new LocalAnalysisRunNotFoundException(analysisId);
        }
        return analysisId.trim();
    }
}
