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
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.projection
        .RuntimeConfigurationDiffDocument;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.projection
        .RuntimeConfigurationDiffFile;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.projection
        .RuntimeConfigurationDiffFileFormat;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.projection
        .RuntimeConfigurationDiffNode;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.projection
        .RuntimeConfigurationDiffProjection;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.projection
        .RuntimeConfigurationDiffValue;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.projection
        .RuntimeConfigurationDiffValuePresence;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationFileRole;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationJobStateSnapshot;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationComponentRunSnapshot;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationResult;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.error.RuntimeConfigurationVerificationImportException;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.export.RuntimeConfigurationVerificationExportEnvelope;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.importing.RuntimeConfigurationVerificationImportService;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.localworkspace
        .RuntimeConfigurationVerificationLocalRunPersister;
import pl.mkn.tdw.features.runtimeconfigurationverification.presentation
        .RuntimeConfigurationDiffAnnotation;
import pl.mkn.tdw.features.runtimeconfigurationverification.presentation
        .RuntimeConfigurationDiffAnnotationKind;
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

        persister.persistRunSnapshot(batchSnapshot());

        var indexCaptor = ArgumentCaptor.forClass(LocalAnalysisRunIndexEntry.class);
        var recordCaptor = ArgumentCaptor.forClass(LocalAnalysisRunRecord.class);
        verify(store).save(indexCaptor.capture(), recordCaptor.capture());
        var serialized = objectMapper.writeValueAsString(recordCaptor.getValue());

        assertEquals("runtime-configuration-verification", indexCaptor.getValue().feature());
        assertTrue(indexCaptor.getValue().name().startsWith("2 komponentów · "));
        assertTrue(indexCaptor.getValue().name().contains("dev1 → zt001"));
        assertFalse(recordCaptor.getValue().continuation().enabled());
        assertFalse(serialized.contains("raw-source-secret"));
        assertFalse(serialized.contains("raw-target-secret"));
        assertTrue(serialized.contains("\"sourceValueToken\":null"));
        assertTrue(serialized.contains("vault:dev/db-password"));
        assertTrue(serialized.contains("vault:zt/db-password"));
        assertTrue(serialized.contains("Zmiana może przełączyć bazę danych."));
        assertTrue(serialized.contains("\"preparedPrompt\":null"));
        assertTrue(serialized.contains("\"prompt\":null"));
    }

    @Test
    void shouldRoundTripCompletedExportAsReadOnlySnapshot() {
        var source = batchSnapshot();
        var document = objectMapper.valueToTree(
                RuntimeConfigurationVerificationExportEnvelope.from(source, Instant.parse("2026-07-30T08:00:00Z"))
        );

        var imported = new RuntimeConfigurationVerificationImportService(objectMapper)
                .importReadOnly(document);
        var sourceResult = source.components().get(0).result();
        var importedResult = imported.components().get(0).result();

        assertEquals(source.jobId(), imported.jobId());
        assertEquals(1, document.path("version").asInt());
        assertEquals(
                "runtime-configuration-verification-result-v1",
                document.at("/payload/resultContract").asText()
        );
        assertEquals(sourceResult.status(), importedResult.status());
        assertEquals(List.of("crm-backend", "billing-backend"), imported.systemIds());
        assertEquals(2, imported.components().size());
        assertEquals("FAILED", imported.components().get(1).status());
        assertEquals("BILLING_SCOPE_FAILED", imported.components().get(1).errorCode());
        assertEquals(sourceResult.configurationDiff(), importedResult.configurationDiff());
        assertEquals(
                sourceResult.configurationDiffAnnotations(),
                importedResult.configurationDiffAnnotations()
        );
        assertTrue(imported.imported());
        assertFalse(document.toString().contains("raw-source-secret"));
        assertFalse(document.toString().contains("raw-target-secret"));
        assertTrue(document.toString().contains("vault:dev/db-password"));
        assertTrue(document.toString().contains("vault:zt/db-password"));
    }

    @Test
    void shouldRejectIncompleteV1WithoutProjectionAnnotations() {
        var document = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.valueToTree(
                RuntimeConfigurationVerificationExportEnvelope.from(
                        snapshotWithContaminatedSensitiveTokens(),
                        Instant.parse("2026-07-30T08:00:00Z")
                )
        );
        var result = (com.fasterxml.jackson.databind.node.ObjectNode) document
                .path("payload")
                .path("job")
                .path("components")
                .path(0)
                .path("result");
        result.remove("configurationDiffAnnotations");
        result.putNull("configurationDiff");

        var exception = assertThrows(
                RuntimeConfigurationVerificationImportException.class,
                () -> new RuntimeConfigurationVerificationImportService(objectMapper)
                        .importReadOnly(document)
        );

        assertEquals("RUNTIME_CONFIGURATION_VERIFICATION_IMPORT_INVALID", exception.code());
        assertTrue(exception.getMessage().contains("invalid structure"));
    }

    @Test
    void shouldRejectFormerV2WithoutCompatibility() {
        var document = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.valueToTree(
                RuntimeConfigurationVerificationExportEnvelope.from(
                        snapshotWithContaminatedSensitiveTokens(),
                        Instant.now()
                )
        );
        document.put("version", 2);

        var exception = assertThrows(
                RuntimeConfigurationVerificationImportException.class,
                () -> new RuntimeConfigurationVerificationImportService(objectMapper)
                        .importReadOnly(document)
        );

        assertEquals("RUNTIME_CONFIGURATION_VERIFICATION_IMPORT_INVALID", exception.code());
        assertTrue(exception.getMessage().contains("version"));
    }

    @Test
    void shouldRejectV1WithMismatchedBatchOrder() {
        var document = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.valueToTree(
                RuntimeConfigurationVerificationExportEnvelope.from(
                        batchSnapshot(),
                        Instant.now()
                )
        );
        var systemIds = (com.fasterxml.jackson.databind.node.ArrayNode) document
                .at("/payload/job/systemIds");
        systemIds.set(1, objectMapper.getNodeFactory().textNode("other-system"));

        var exception = assertThrows(
                RuntimeConfigurationVerificationImportException.class,
                () -> new RuntimeConfigurationVerificationImportService(objectMapper)
                        .importReadOnly(document)
        );

        assertEquals("RUNTIME_CONFIGURATION_VERIFICATION_IMPORT_INVALID", exception.code());
        assertTrue(exception.getMessage().contains("batch structure"));
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
                "crm-backend",
                "CRM Backend",
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
                RuntimeConfigurationVerificationMode.DEEP,
                deterministic,
                configurationDiff(),
                List.of(new RuntimeConfigurationDiffAnnotation(
                        "observation-1",
                        RuntimeConfigurationDiffAnnotationKind.OBSERVATION,
                        "Zmiana może przełączyć bazę danych.",
                        null,
                        false,
                        List.of("difference-1"),
                        List.of()
                )),
                null,
                null,
                null,
                List.of(),
                null,
                null
        );
        var now = Instant.parse("2026-07-30T08:00:00Z");
        return new RuntimeConfigurationVerificationJobStateSnapshot(
                "job-portable",
                RuntimeConfigurationVerificationMode.DEEP,
                "runtime-config",
                List.of("crm-backend"),
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
                List.of(new RuntimeConfigurationComponentRunSnapshot(
                        "job-portable:0",
                        "crm-backend",
                        "CRM Backend",
                        "backend",
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
                        null,
                        result,
                        null
                )),
                false
        );
    }

    private RuntimeConfigurationVerificationJobStateSnapshot batchSnapshot() {
        var source = snapshotWithContaminatedSensitiveTokens();
        var now = source.completedAt();
        return new RuntimeConfigurationVerificationJobStateSnapshot(
                source.jobId(),
                source.mode(),
                source.repositoryId(),
                List.of("crm-backend", "billing-backend"),
                source.sourceBranch(),
                source.targetBranch(),
                source.codeRef(),
                source.aiModel(),
                source.reasoningEffort(),
                "COMPLETED_WITH_LIMITATIONS",
                null,
                null,
                null,
                null,
                source.createdAt(),
                now,
                now,
                source.steps(),
                List.of(
                        source.components().get(0),
                        new RuntimeConfigurationComponentRunSnapshot(
                                source.jobId() + ":1",
                                "billing-backend",
                                "Billing Backend",
                                "billing",
                                "FAILED",
                                null,
                                null,
                                "BILLING_SCOPE_FAILED",
                                "Billing configuration scope could not be resolved.",
                                source.createdAt(),
                                now,
                                now,
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                null,
                                null,
                                null
                        )
                ),
                false
        );
    }

    private RuntimeConfigurationDiffProjection configurationDiff() {
        var password = new RuntimeConfigurationDiffNode(
                "datasource.password",
                "datasource.password",
                RuntimeConfigurationChangeKind.CHANGED,
                new RuntimeConfigurationDiffValue(
                        RuntimeConfigurationDiffValuePresence.PRESENT,
                        RuntimeConfigurationValueType.STRING,
                        "vault:dev/db-password",
                        null
                ),
                new RuntimeConfigurationDiffValue(
                        RuntimeConfigurationDiffValuePresence.PRESENT,
                        RuntimeConfigurationValueType.STRING,
                        "vault:zt/db-password",
                        null
                ),
                List.of("difference-1"),
                List.of()
        );
        var document = new RuntimeConfigurationDiffDocument(
                0,
                true,
                true,
                RuntimeConfigurationDiffValue.absent(),
                RuntimeConfigurationDiffValue.absent(),
                password
        );
        var file = new RuntimeConfigurationDiffFile(
                RuntimeConfigurationFileRole.LOCAL_VAR,
                RuntimeConfigurationDiffFileFormat.VAR,
                "backend/local.var",
                "backend/local.var",
                true,
                true,
                List.of(document)
        );
        return new RuntimeConfigurationDiffProjection("dev1", "zt001", List.of(file));
    }
}
