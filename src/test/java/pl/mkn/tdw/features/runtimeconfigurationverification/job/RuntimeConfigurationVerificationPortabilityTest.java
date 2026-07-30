package pl.mkn.tdw.features.runtimeconfigurationverification.job;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.model.RuntimeConfigurationVerificationStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationChangeKind;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDifference;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationSensitivity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationValueType;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.SanitizedConfigurationDocument;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.SanitizedConfigurationNode;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationFileRole;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationJobStateSnapshot;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationResult;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.error.RuntimeConfigurationVerificationImportException;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.export.RuntimeConfigurationVerificationExportEnvelope;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.importing.RuntimeConfigurationVerificationImportService;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.localworkspace
        .RuntimeConfigurationVerificationLocalRunPersister;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RuntimeConfigurationVerificationPortabilityTest {

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            JsonMapper.builder().findAndAddModules().build();

    @Test
    void shouldPersistSafeHistoryEntryWithoutChatContinuationOrSensitiveTokens() throws Exception {
        var store = mock(LocalAnalysisRunStore.class);
        var persister = new RuntimeConfigurationVerificationLocalRunPersister(objectMapper, store);

        persister.persistRunSnapshot(snapshotWithContaminatedSensitiveTokens());

        var indexCaptor = ArgumentCaptor.forClass(LocalAnalysisRunIndexEntry.class);
        var recordCaptor = ArgumentCaptor.forClass(LocalAnalysisRunRecord.class);
        verify(store).save(indexCaptor.capture(), recordCaptor.capture());
        var serialized = objectMapper.writeValueAsString(recordCaptor.getValue());

        assertEquals("runtime-configuration-verification", indexCaptor.getValue().feature());
        assertTrue(indexCaptor.getValue().name().contains("dev1 → zt001"));
        assertFalse(recordCaptor.getValue().continuation().enabled());
        assertFalse(serialized.contains("raw-source-secret"));
        assertFalse(serialized.contains("raw-target-secret"));
        assertTrue(serialized.contains("\"sourceValueToken\":null"));
    }

    @Test
    void shouldRoundTripCompletedExportAsReadOnlySnapshot() {
        var source = snapshotWithContaminatedSensitiveTokens();
        var document = objectMapper.valueToTree(
                RuntimeConfigurationVerificationExportEnvelope.from(source, Instant.parse("2026-07-30T08:00:00Z"))
        );

        var imported = new RuntimeConfigurationVerificationImportService(objectMapper)
                .importReadOnly(document);

        assertEquals(source.jobId(), imported.jobId());
        assertEquals(source.result().status(), imported.result().status());
        assertTrue(imported.imported());
        assertFalse(document.toString().contains("raw-source-secret"));
        assertFalse(document.toString().contains("raw-target-secret"));
    }

    @Test
    void shouldRejectUnsupportedVersion() {
        var document = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.valueToTree(
                RuntimeConfigurationVerificationExportEnvelope.from(
                        snapshotWithContaminatedSensitiveTokens(),
                        Instant.now()
                )
        );
        document.put("version", 99);

        var exception = assertThrows(
                RuntimeConfigurationVerificationImportException.class,
                () -> new RuntimeConfigurationVerificationImportService(objectMapper)
                        .importReadOnly(document)
        );

        assertEquals("RUNTIME_CONFIGURATION_VERIFICATION_IMPORT_INVALID", exception.code());
        assertTrue(exception.getMessage().contains("version"));
    }

    private RuntimeConfigurationVerificationJobStateSnapshot snapshotWithContaminatedSensitiveTokens() {
        var sensitiveNode = new SanitizedConfigurationNode(
                "password",
                "datasource.password",
                RuntimeConfigurationValueType.STRING,
                RuntimeConfigurationValueType.STRING,
                RuntimeConfigurationChangeKind.CHANGED,
                RuntimeConfigurationSensitivity.SENSITIVE,
                "raw-source-secret",
                "raw-target-secret",
                null,
                null,
                List.of()
        );
        var deterministic = new RuntimeConfigurationDeterministicContext(
                "runtime-config",
                "clp-backend",
                "CLP Backend",
                "backend",
                "dev1",
                "zt001",
                RuntimeConfigurationDeterministicStatus.REVIEW_REQUIRED,
                null,
                null,
                List.of(new SanitizedConfigurationDocument(
                        RuntimeConfigurationFileRole.LOCAL_VAR,
                        "backend/local.var",
                        "backend/local.var",
                        0,
                        true,
                        true,
                        null,
                        null,
                        sensitiveNode
                )),
                List.of(),
                List.of(new RuntimeConfigurationDifference(
                        "difference-1",
                        RuntimeConfigurationFileRole.LOCAL_VAR,
                        0,
                        "datasource.password",
                        RuntimeConfigurationChangeKind.CHANGED,
                        RuntimeConfigurationValueType.STRING,
                        RuntimeConfigurationValueType.STRING,
                        RuntimeConfigurationSensitivity.SENSITIVE,
                        "raw-source-secret",
                        "raw-target-secret"
                )),
                List.of()
        );
        var result = new RuntimeConfigurationVerificationResult(
                RuntimeConfigurationVerificationStatus.REVIEW_REQUIRED,
                RuntimeConfigurationVerificationMode.BASIC,
                deterministic,
                null,
                null,
                null,
                List.of(),
                "safe prompt",
                null
        );
        var now = Instant.parse("2026-07-30T08:00:00Z");
        return new RuntimeConfigurationVerificationJobStateSnapshot(
                "job-portable",
                RuntimeConfigurationVerificationMode.BASIC,
                "runtime-config",
                "clp-backend",
                "dev1",
                "zt001",
                null,
                null,
                null,
                "COMPLETED",
                null,
                null,
                null,
                null,
                now.minusSeconds(10),
                now,
                now,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "safe prompt",
                result,
                null,
                false
        );
    }
}
