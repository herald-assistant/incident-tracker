package pl.mkn.tdw.features.configdriftviewer.job.importing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerJobStateSnapshot;
import pl.mkn.tdw.features.configdriftviewer.job.error.ConfigDriftViewerImportException;
import pl.mkn.tdw.features.configdriftviewer.job.export.ConfigDriftViewerExportEnvelope;
import pl.mkn.tdw.features.configdriftviewer.job.export.ConfigDriftViewerSnapshotSanitizer;

import java.util.Set;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class ConfigDriftViewerImportService {

    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "COMPLETED",
            "COMPLETED_WITH_LIMITATIONS",
            "FAILED"
    );

    private final ObjectMapper objectMapper;

    public ConfigDriftViewerJobStateSnapshot importReadOnly(JsonNode document) {
        if (document == null || document.isNull()) {
            throw invalid("Config Drift Viewer export is required.");
        }
        try {
            var envelope = objectMapper.treeToValue(
                    document,
                    ConfigDriftViewerExportEnvelope.class
            );
            validate(envelope);
            return ConfigDriftViewerSnapshotSanitizer
                    .sanitize(envelope.payload().job())
                    .asImported();
        } catch (ConfigDriftViewerImportException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("Config Drift Viewer export has an invalid structure.");
        }
    }

    private void validate(ConfigDriftViewerExportEnvelope envelope) {
        if (envelope == null
                || !ConfigDriftViewerExportEnvelope.SCHEMA.equals(envelope.schema())) {
            throw invalid("Unsupported Config Drift Viewer export schema.");
        }
        if (envelope.version() != ConfigDriftViewerExportEnvelope.VERSION) {
            throw invalid("Unsupported Config Drift Viewer export version.");
        }
        if (envelope.payload() == null
                || !ConfigDriftViewerExportEnvelope.PAYLOAD_TYPE
                .equals(envelope.payload().type())
                || !ConfigDriftViewerExportEnvelope.RESULT_CONTRACT
                .equals(envelope.payload().resultContract())) {
            throw invalid("Unsupported Config Drift Viewer result contract.");
        }
        var job = envelope.payload().job();
        if (job == null
                || job.components().stream().noneMatch(component -> component.result() != null)
                || !TERMINAL_STATUSES.contains(job.status())) {
            throw invalid("Only a completed Config Drift Viewer result can be imported.");
        }
        if (job.systemIds().isEmpty()
                || job.components().isEmpty()
                || job.systemIds().size() != job.components().size()
                || job.systemIds().stream().distinct().count() != job.systemIds().size()
                || IntStream.range(0, job.systemIds().size()).anyMatch(index ->
                !job.systemIds().get(index).equals(job.components().get(index).systemId()))) {
            throw invalid("Config Drift Viewer export has an invalid batch structure.");
        }
    }

    private ConfigDriftViewerImportException invalid(String message) {
        return new ConfigDriftViewerImportException(message);
    }
}
