package pl.mkn.tdw.features.uiexplorer.job.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uiexplorer.job.UiExplorerJobService;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStateSnapshot;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStatus;
import pl.mkn.tdw.features.uiexplorer.job.error.UiExplorerExportUnavailableException;
import pl.mkn.tdw.features.uiexplorer.job.error.UiExplorerJobNotFoundException;
import pl.mkn.tdw.features.uiexplorer.job.localworkspace.UiExplorerLocalRunEnvelope;
import pl.mkn.tdw.features.uiexplorer.job.localworkspace.UiExplorerLocalRunSnapshotSanitizer;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UiExplorerExportService {

    private final ObjectMapper objectMapper;
    private final UiExplorerJobService jobService;
    private final LocalAnalysisRunStore localAnalysisRunStore;
    private final UiExplorerLocalRunSnapshotSanitizer sanitizer;

    public UiExplorerExportEnvelope export(String jobId) {
        var normalizedJobId = normalize(jobId);
        var snapshot = sanitizer.sanitize(resolveSnapshot(normalizedJobId));
        validateExportable(snapshot);
        return UiExplorerExportEnvelope.from(snapshot, Instant.now());
    }

    private UiExplorerJobStateSnapshot resolveSnapshot(String jobId) {
        try {
            return jobService.getJob(jobId);
        } catch (UiExplorerJobNotFoundException exception) {
            return localAnalysisRunStore.findById(jobId)
                    .map(record -> readLocalSnapshot(jobId, record.exportEnvelope()))
                    .orElseThrow(() -> exception);
        }
    }

    private UiExplorerJobStateSnapshot readLocalSnapshot(
            String requestedJobId,
            com.fasterxml.jackson.databind.JsonNode document
    ) {
        try {
            var envelope = objectMapper.treeToValue(document, UiExplorerLocalRunEnvelope.class);
            if (envelope == null
                    || !UiExplorerLocalRunEnvelope.SCHEMA.equals(envelope.schema())
                    || envelope.version() != UiExplorerLocalRunEnvelope.VERSION
                    || envelope.payload() == null
                    || !UiExplorerLocalRunEnvelope.PAYLOAD_TYPE.equals(envelope.payload().type())
                    || !UiExplorerLocalRunEnvelope.RESULT_CONTRACT.equals(envelope.payload().resultContract())
                    || envelope.payload().job() == null
                    || !requestedJobId.equals(envelope.payload().job().jobId())) {
                throw unavailableHistory();
            }
            return envelope.payload().job();
        } catch (UiExplorerExportUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailableHistory();
        }
    }

    private void validateExportable(UiExplorerJobStateSnapshot snapshot) {
        var status = snapshot.status();
        if ((status != UiExplorerJobStatus.COMPLETED && status != UiExplorerJobStatus.PARTIAL)
                || snapshot.result() == null
                || snapshot.report() == null) {
            throw new UiExplorerExportUnavailableException(
                    "Only a completed UI Explorer run with a result and report can be exported."
            );
        }
    }

    private UiExplorerExportUnavailableException unavailableHistory() {
        return new UiExplorerExportUnavailableException(
                "The local UI Explorer history entry cannot be exported."
        );
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }
}
