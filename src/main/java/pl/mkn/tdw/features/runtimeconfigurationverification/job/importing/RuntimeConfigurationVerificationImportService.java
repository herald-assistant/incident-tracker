package pl.mkn.tdw.features.runtimeconfigurationverification.job.importing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationJobStateSnapshot;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.error.RuntimeConfigurationVerificationImportException;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.export.RuntimeConfigurationVerificationExportEnvelope;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.export.RuntimeConfigurationVerificationSnapshotSanitizer;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class RuntimeConfigurationVerificationImportService {

    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "COMPLETED",
            "COMPLETED_WITH_LIMITATIONS",
            "FAILED"
    );

    private final ObjectMapper objectMapper;

    public RuntimeConfigurationVerificationJobStateSnapshot importReadOnly(JsonNode document) {
        if (document == null || document.isNull()) {
            throw invalid("Runtime Configuration Verification export is required.");
        }
        try {
            var envelope = objectMapper.treeToValue(
                    document,
                    RuntimeConfigurationVerificationExportEnvelope.class
            );
            validate(envelope);
            return RuntimeConfigurationVerificationSnapshotSanitizer
                    .sanitize(envelope.payload().job())
                    .asImported();
        } catch (RuntimeConfigurationVerificationImportException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalid("Runtime Configuration Verification export has an invalid structure.");
        }
    }

    private void validate(RuntimeConfigurationVerificationExportEnvelope envelope) {
        if (envelope == null
                || !RuntimeConfigurationVerificationExportEnvelope.SCHEMA.equals(envelope.schema())) {
            throw invalid("Unsupported Runtime Configuration Verification export schema.");
        }
        if (envelope.version() != RuntimeConfigurationVerificationExportEnvelope.VERSION) {
            throw invalid("Unsupported Runtime Configuration Verification export version.");
        }
        if (envelope.payload() == null
                || !RuntimeConfigurationVerificationExportEnvelope.PAYLOAD_TYPE
                .equals(envelope.payload().type())
                || !RuntimeConfigurationVerificationExportEnvelope.RESULT_CONTRACT
                .equals(envelope.payload().resultContract())) {
            throw invalid("Unsupported Runtime Configuration Verification result contract.");
        }
        var job = envelope.payload().job();
        if (job == null || job.result() == null || !TERMINAL_STATUSES.contains(job.status())) {
            throw invalid("Only a completed Runtime Configuration Verification result can be imported.");
        }
    }

    private RuntimeConfigurationVerificationImportException invalid(String message) {
        return new RuntimeConfigurationVerificationImportException(message);
    }
}
