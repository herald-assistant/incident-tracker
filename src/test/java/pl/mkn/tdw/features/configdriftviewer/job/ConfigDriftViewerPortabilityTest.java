package pl.mkn.tdw.features.configdriftviewer.job;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.features.configdriftviewer.ai.model.ConfigDriftViewerStatus;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerChangeKind;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicStatus;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDifference;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerSensitivity;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerValueType;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.SanitizedConfigurationDocument;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.SanitizedConfigurationNode;
import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffDocument;
import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffFile;
import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffFileFormat;
import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffNode;
import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffProjection;
import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffValue;
import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffValuePresence;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerFileRole;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerJobStateSnapshot;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerComponentRunSnapshot;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerMode;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerResult;
import pl.mkn.tdw.features.configdriftviewer.job.error.ConfigDriftViewerImportException;
import pl.mkn.tdw.features.configdriftviewer.job.export.ConfigDriftViewerExportEnvelope;
import pl.mkn.tdw.features.configdriftviewer.job.importing.ConfigDriftViewerImportService;
import pl.mkn.tdw.features.configdriftviewer.job.localworkspace
        .ConfigDriftViewerLocalRunPersister;
import pl.mkn.tdw.features.configdriftviewer.presentation
        .ConfigDriftViewerDiffAnnotation;
import pl.mkn.tdw.features.configdriftviewer.presentation
        .ConfigDriftViewerDiffAnnotationKind;
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

class ConfigDriftViewerPortabilityTest {

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper =
            JsonMapper.builder().findAndAddModules().build();

    @Test
    void shouldPersistSafeHistoryEntryWithoutChatContinuationOrSensitiveTokens() throws Exception {
        var store = mock(LocalAnalysisRunStore.class);
        var persister = new ConfigDriftViewerLocalRunPersister(objectMapper, store);

        persister.persistRunSnapshot(batchSnapshot());

        var indexCaptor = ArgumentCaptor.forClass(LocalAnalysisRunIndexEntry.class);
        var recordCaptor = ArgumentCaptor.forClass(LocalAnalysisRunRecord.class);
        verify(store).save(indexCaptor.capture(), recordCaptor.capture());
        var serialized = objectMapper.writeValueAsString(recordCaptor.getValue());

        assertEquals("config-drift-viewer", indexCaptor.getValue().feature());
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
                ConfigDriftViewerExportEnvelope.from(source, Instant.parse("2026-07-30T08:00:00Z"))
        );

        var imported = new ConfigDriftViewerImportService(objectMapper)
                .importReadOnly(document);
        var sourceResult = source.components().get(0).result();
        var importedResult = imported.components().get(0).result();

        assertEquals(source.jobId(), imported.jobId());
        assertEquals(1, document.path("version").asInt());
        assertEquals(
                "config-drift-viewer-result-v1",
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
                ConfigDriftViewerExportEnvelope.from(
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
                ConfigDriftViewerImportException.class,
                () -> new ConfigDriftViewerImportService(objectMapper)
                        .importReadOnly(document)
        );

        assertEquals("RUNTIME_CONFIGURATION_VERIFICATION_IMPORT_INVALID", exception.code());
        assertTrue(exception.getMessage().contains("invalid structure"));
    }

    @Test
    void shouldRejectFormerV2WithoutCompatibility() {
        var document = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.valueToTree(
                ConfigDriftViewerExportEnvelope.from(
                        snapshotWithContaminatedSensitiveTokens(),
                        Instant.now()
                )
        );
        document.put("version", 2);

        var exception = assertThrows(
                ConfigDriftViewerImportException.class,
                () -> new ConfigDriftViewerImportService(objectMapper)
                        .importReadOnly(document)
        );

        assertEquals("RUNTIME_CONFIGURATION_VERIFICATION_IMPORT_INVALID", exception.code());
        assertTrue(exception.getMessage().contains("version"));
    }

    @Test
    void shouldRejectV1WithMismatchedBatchOrder() {
        var document = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.valueToTree(
                ConfigDriftViewerExportEnvelope.from(
                        batchSnapshot(),
                        Instant.now()
                )
        );
        var systemIds = (com.fasterxml.jackson.databind.node.ArrayNode) document
                .at("/payload/job/systemIds");
        systemIds.set(1, objectMapper.getNodeFactory().textNode("other-system"));

        var exception = assertThrows(
                ConfigDriftViewerImportException.class,
                () -> new ConfigDriftViewerImportService(objectMapper)
                        .importReadOnly(document)
        );

        assertEquals("RUNTIME_CONFIGURATION_VERIFICATION_IMPORT_INVALID", exception.code());
        assertTrue(exception.getMessage().contains("batch structure"));
    }

    @Test
    void shouldRejectUnsupportedVersion() {
        var document = (com.fasterxml.jackson.databind.node.ObjectNode) objectMapper.valueToTree(
                ConfigDriftViewerExportEnvelope.from(
                        snapshotWithContaminatedSensitiveTokens(),
                        Instant.now()
                )
        );
        document.put("version", 99);

        var exception = assertThrows(
                ConfigDriftViewerImportException.class,
                () -> new ConfigDriftViewerImportService(objectMapper)
                        .importReadOnly(document)
        );

        assertEquals("RUNTIME_CONFIGURATION_VERIFICATION_IMPORT_INVALID", exception.code());
        assertTrue(exception.getMessage().contains("version"));
    }

    private ConfigDriftViewerJobStateSnapshot snapshotWithContaminatedSensitiveTokens() {
        var sensitiveNode = new SanitizedConfigurationNode(
                "password",
                "datasource.password",
                ConfigDriftViewerValueType.STRING,
                ConfigDriftViewerValueType.STRING,
                ConfigDriftViewerChangeKind.CHANGED,
                ConfigDriftViewerSensitivity.SENSITIVE,
                "raw-source-secret",
                "raw-target-secret",
                null,
                null,
                List.of()
        );
        var deterministic = new ConfigDriftViewerDeterministicContext(
                "runtime-config",
                "crm-backend",
                "CRM Backend",
                "backend",
                "dev1",
                "zt001",
                ConfigDriftViewerDeterministicStatus.REVIEW_REQUIRED,
                null,
                null,
                List.of(new SanitizedConfigurationDocument(
                        ConfigDriftViewerFileRole.LOCAL_VAR,
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
                List.of(new ConfigDriftViewerDifference(
                        "difference-1",
                        ConfigDriftViewerFileRole.LOCAL_VAR,
                        0,
                        "datasource.password",
                        ConfigDriftViewerChangeKind.CHANGED,
                        ConfigDriftViewerValueType.STRING,
                        ConfigDriftViewerValueType.STRING,
                        ConfigDriftViewerSensitivity.SENSITIVE,
                        "raw-source-secret",
                        "raw-target-secret"
                )),
                List.of()
        );
        var result = new ConfigDriftViewerResult(
                ConfigDriftViewerStatus.REVIEW_REQUIRED,
                ConfigDriftViewerMode.DEEP,
                deterministic,
                configurationDiff(),
                List.of(new ConfigDriftViewerDiffAnnotation(
                        "observation-1",
                        ConfigDriftViewerDiffAnnotationKind.OBSERVATION,
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
        return new ConfigDriftViewerJobStateSnapshot(
                "job-portable",
                ConfigDriftViewerMode.DEEP,
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
                List.of(new ConfigDriftViewerComponentRunSnapshot(
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

    private ConfigDriftViewerJobStateSnapshot batchSnapshot() {
        var source = snapshotWithContaminatedSensitiveTokens();
        var now = source.completedAt();
        return new ConfigDriftViewerJobStateSnapshot(
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
                        new ConfigDriftViewerComponentRunSnapshot(
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

    private ConfigDriftViewerDiffProjection configurationDiff() {
        var password = new ConfigDriftViewerDiffNode(
                "datasource.password",
                "datasource.password",
                ConfigDriftViewerChangeKind.CHANGED,
                new ConfigDriftViewerDiffValue(
                        ConfigDriftViewerDiffValuePresence.PRESENT,
                        ConfigDriftViewerValueType.STRING,
                        "vault:dev/db-password",
                        null
                ),
                new ConfigDriftViewerDiffValue(
                        ConfigDriftViewerDiffValuePresence.PRESENT,
                        ConfigDriftViewerValueType.STRING,
                        "vault:zt/db-password",
                        null
                ),
                List.of("difference-1"),
                List.of()
        );
        var document = new ConfigDriftViewerDiffDocument(
                0,
                true,
                true,
                ConfigDriftViewerDiffValue.absent(),
                ConfigDriftViewerDiffValue.absent(),
                password
        );
        var file = new ConfigDriftViewerDiffFile(
                ConfigDriftViewerFileRole.LOCAL_VAR,
                ConfigDriftViewerDiffFileFormat.VAR,
                "backend/local.var",
                "backend/local.var",
                true,
                true,
                List.of(document)
        );
        return new ConfigDriftViewerDiffProjection("dev1", "zt001", List.of(file));
    }
}
