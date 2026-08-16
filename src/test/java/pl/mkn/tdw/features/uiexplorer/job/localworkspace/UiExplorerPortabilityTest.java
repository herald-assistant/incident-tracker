package pl.mkn.tdw.features.uiexplorer.job.localworkspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.mkn.tdw.api.analysisruns.AnalysisRunHistoryService;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionCleanup;
import pl.mkn.tdw.features.uiexplorer.job.UiExplorerJobService;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStatus;
import pl.mkn.tdw.features.uiexplorer.job.error.UiExplorerExportUnavailableException;
import pl.mkn.tdw.features.uiexplorer.job.error.UiExplorerImportException;
import pl.mkn.tdw.features.uiexplorer.job.error.UiExplorerImportPersistenceException;
import pl.mkn.tdw.features.uiexplorer.job.error.UiExplorerJobNotFoundException;
import pl.mkn.tdw.features.uiexplorer.job.export.UiExplorerExportEnvelope;
import pl.mkn.tdw.features.uiexplorer.job.export.UiExplorerExportService;
import pl.mkn.tdw.features.uiexplorer.job.importing.UiExplorerImportService;
import pl.mkn.tdw.features.uiexplorer.report.DefaultUiExplorerResultReportAssembler;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.localworkspace.analysisruns.FileSystemLocalAnalysisRunStore;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspaceJsonFileStore;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspacePaths;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static pl.mkn.tdw.features.uiexplorer.job.localworkspace.UiExplorerLocalRunTestFixture.SOURCE_PATH;
import static pl.mkn.tdw.features.uiexplorer.job.localworkspace.UiExplorerLocalRunTestFixture.snapshot;

class UiExplorerPortabilityTest {

    private static final String IMPORT_PROMPT_SECRET = "CRM_IMPORTED_RAW_PROMPT_SECRET";
    private static final String IMPORT_SCOPE_SECRET = "CRM_IMPORTED_HIDDEN_SCOPE_SECRET";
    private static final String IMPORT_REPORT_SECRET = "CRM_IMPORTED_REPORT_SECRET";
    private static final String IMPORT_FEEDBACK_SECRET = "CRM_IMPORTED_FEEDBACK_SECRET";

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    private final UiExplorerLocalRunSnapshotSanitizer sanitizer =
            new UiExplorerLocalRunSnapshotSanitizer(new DefaultUiExplorerResultReportAssembler());

    @Test
    void shouldRoundTripSanitizedCrmExportThroughHistoryAndRestart(@TempDir Path workspace) throws Exception {
        var properties = workspaceProperties(workspace, true);
        var paths = new LocalWorkspacePaths(properties);
        var jsonFileStore = new LocalWorkspaceJsonFileStore(objectMapper);
        var store = new FileSystemLocalAnalysisRunStore(properties, paths, jsonFileStore);
        var persister = new UiExplorerLocalRunPersister(objectMapper, store, sanitizer);
        var source = snapshot(UiExplorerJobStatus.COMPLETED);
        var liveJobs = mock(UiExplorerJobService.class);
        when(liveJobs.getJob(source.jobId())).thenReturn(source);
        var exporter = new UiExplorerExportService(objectMapper, liveJobs, store, sanitizer);

        var portable = exporter.export(source.jobId());
        var document = (ObjectNode) objectMapper.valueToTree(portable);

        assertThat(portable.schema()).isEqualTo("tdw.ui-explorer-export");
        assertThat(portable.version()).isEqualTo(3);
        assertThat(portable.payload().resultContract()).isEqualTo("ui-explorer-result-v3");
        assertThat(document.toString()).doesNotContain(
                "CRM_RAW_PROMPT_SECRET",
                "CRM_RAW_SOURCE_SECRET",
                "CRM_HIDDEN_SCOPE_SECRET",
                "confidential-crm-group",
                "internal-crm-ui-project"
        );
        contaminateUntrustedImport(document);

        var importService = new UiExplorerImportService(objectMapper, properties, sanitizer, persister);
        var imported = importService.importReadOnly(document);

        assertThat(imported.jobId()).startsWith("ui-explorer-import-").isNotEqualTo(source.jobId());
        assertThat(imported.status()).isEqualTo(UiExplorerJobStatus.COMPLETED);
        assertThat(imported.exportAvailable()).isTrue();
        assertThat(imported.preparedPrompt()).isNull();
        assertThat(imported.toolFeedback()).isEmpty();
        assertThat(imported.sourceRevision()).isEqualTo(source.sourceRevision());
        assertThat(imported.result().visibilityLimits()).isEqualTo(source.result().visibilityLimits());
        assertThat(imported.report().header()).isEqualTo("UI Explorer: CRM Contact Preferences");
        assertThat(imported.report().meta().references()).allSatisfy(reference ->
                assertThat(reference.target()).doesNotContain("confidential", "internal-crm-ui-project"));

        var storedRecord = store.findById(imported.jobId()).orElseThrow();
        var storedJson = objectMapper.writeValueAsString(storedRecord);
        assertThat(storedJson).doesNotContain(
                IMPORT_PROMPT_SECRET,
                IMPORT_SCOPE_SECRET,
                IMPORT_REPORT_SECRET,
                IMPORT_FEEDBACK_SECRET
        );

        var restartedStore = new FileSystemLocalAnalysisRunStore(properties, paths, jsonFileStore);
        var history = new AnalysisRunHistoryService(restartedStore, List.of(), CopilotSessionCleanup.NO_OP);
        var historyDetail = history.getRun(imported.jobId());
        assertThat(historyDetail.feature()).isEqualTo("ui-explorer");
        assertThat(historyDetail.continuationEnabled()).isFalse();
        assertThat(historyDetail.exportEnvelope().at("/payload/job/sourceRevision/revision").asText())
                .isEqualTo("crm-commit-abc123");

        var restartedJobs = mock(UiExplorerJobService.class);
        when(restartedJobs.getJob(imported.jobId())).thenThrow(new UiExplorerJobNotFoundException(imported.jobId()));
        var reExported = new UiExplorerExportService(
                objectMapper,
                restartedJobs,
                restartedStore,
                sanitizer
        ).export(imported.jobId());
        var reExportedJson = objectMapper.writeValueAsString(reExported);

        assertThat(reExported.schema()).isEqualTo(UiExplorerExportEnvelope.SCHEMA);
        assertThat(reExported.payload().job().jobId()).isEqualTo(imported.jobId());
        assertThat(reExportedJson).doesNotContain(
                IMPORT_PROMPT_SECRET,
                IMPORT_SCOPE_SECRET,
                IMPORT_REPORT_SECRET,
                IMPORT_FEEDBACK_SECRET
        );
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 99})
    void shouldRejectEveryNonCurrentCrmExportVersion(int version) {
        var document = portableDocument();
        document.put("version", version);

        assertThatThrownBy(() -> importService(true).importReadOnly(document))
                .isInstanceOf(UiExplorerImportException.class)
                .hasMessage("Unsupported UI Explorer export version.");
    }

    @Test
    void shouldRejectUnknownSchemaResultContractAndInconsistentCrmRevision() {
        var unknownSchema = portableDocument();
        unknownSchema.put("schema", "tdw.ui-explorer-export-legacy");
        assertThatThrownBy(() -> importService(true).importReadOnly(unknownSchema))
                .isInstanceOf(UiExplorerImportException.class)
                .hasMessage("Unsupported UI Explorer export schema.");

        var unknownContract = portableDocument();
        ((ObjectNode) unknownContract.path("payload")).put("resultContract", "ui-explorer-result-v0");
        assertThatThrownBy(() -> importService(true).importReadOnly(unknownContract))
                .isInstanceOf(UiExplorerImportException.class)
                .hasMessage("Unsupported UI Explorer result contract.");

        var inconsistentRevision = portableDocument();
        ((ObjectNode) inconsistentRevision.at("/payload/job/result/sourceRevision"))
                .put("revision", "crm-other-commit");
        assertThatThrownBy(() -> importService(true).importReadOnly(inconsistentRevision))
                .isInstanceOf(UiExplorerImportException.class)
                .hasMessage("UI Explorer export has inconsistent screen or source revision data.");
    }

    @Test
    void shouldImportPartialCrmResultAndRejectCorruptedStructure() {
        var partial = (ObjectNode) objectMapper.valueToTree(UiExplorerExportEnvelope.from(
                sanitizer.sanitize(snapshot(UiExplorerJobStatus.PARTIAL)),
                UiExplorerLocalRunTestFixture.COMPLETED_AT
        ));
        var importedPartial = importService(true).importReadOnly(partial);
        assertThat(importedPartial.status()).isEqualTo(UiExplorerJobStatus.PARTIAL);
        assertThat(importedPartial.exportAvailable()).isTrue();

        var corrupted = portableDocument();
        corrupted.put("payload", "broken synthetic CRM payload");
        assertThatThrownBy(() -> importService(true).importReadOnly(corrupted))
                .isInstanceOf(UiExplorerImportException.class)
                .hasMessage("UI Explorer export has an invalid structure.");
    }

    @Test
    void shouldRejectCrmImportWithoutPublishableResultAndDisabledHistory() {
        var failed = portableDocument();
        ((ObjectNode) failed.at("/payload/job")).put("status", "FAILED");
        assertThatThrownBy(() -> importService(true).importReadOnly(failed))
                .isInstanceOf(UiExplorerImportException.class)
                .hasMessage("Only a completed UI Explorer result can be imported.");

        assertThatThrownBy(() -> importService(false).importReadOnly(portableDocument()))
                .isInstanceOf(UiExplorerImportPersistenceException.class)
                .hasMessage("UI Explorer import cannot be saved in local Analysis History.");
    }

    @Test
    void shouldRejectExportBeforeCrmResultIsAvailable() {
        var analyzing = snapshot(UiExplorerJobStatus.ANALYZING);
        var jobs = mock(UiExplorerJobService.class);
        when(jobs.getJob(analyzing.jobId())).thenReturn(analyzing);
        var store = mock(pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore.class);

        assertThatThrownBy(() -> new UiExplorerExportService(objectMapper, jobs, store, sanitizer)
                .export(analyzing.jobId()))
                .isInstanceOf(UiExplorerExportUnavailableException.class)
                .hasMessage("Only a completed UI Explorer run with a result and report can be exported.");
    }

    private ObjectNode portableDocument() {
        return (ObjectNode) objectMapper.valueToTree(UiExplorerExportEnvelope.from(
                sanitizer.sanitize(snapshot(UiExplorerJobStatus.COMPLETED)),
                UiExplorerLocalRunTestFixture.COMPLETED_AT
        ));
    }

    private UiExplorerImportService importService(boolean workspaceEnabled) {
        var properties = new LocalWorkspaceProperties();
        properties.setEnabled(workspaceEnabled);
        return new UiExplorerImportService(
                objectMapper,
                properties,
                sanitizer,
                UiExplorerLocalRunPersistence.NO_OP
        );
    }

    private LocalWorkspaceProperties workspaceProperties(Path workspace, boolean enabled) {
        var properties = new LocalWorkspaceProperties();
        properties.setEnabled(enabled);
        properties.setDirectory(workspace.resolve("crm-portability-history").toString());
        return properties;
    }

    private void contaminateUntrustedImport(ObjectNode document) {
        var job = (ObjectNode) document.at("/payload/job");
        job.put("preparedPrompt", IMPORT_PROMPT_SECRET);
        ((ObjectNode) job.at("/result/sections/0/sourceReferences/0"))
                .put("repository", "confidential-crm-group/internal-crm-ui-project");
        ((ObjectNode) job.path("report")).put("header", IMPORT_REPORT_SECRET);
        ((ObjectNode) job.at("/aiActivityEvents/0/details")).put("hiddenScope", IMPORT_SCOPE_SECRET);
        ((ArrayNode) job.at("/toolEvidenceSections/0/items/0/attributes"))
                .addObject()
                .put("name", "toolArguments")
                .put("value", IMPORT_SCOPE_SECRET);
        ((ArrayNode) job.at("/contextSections/0/items/0/attributes"))
                .addObject()
                .put("name", "content")
                .put("value", IMPORT_SCOPE_SECRET);
        ((ArrayNode) job.path("toolFeedback")).addObject()
                .put("feedbackId", "crm-import-feedback-1")
                .put("improvementArea", IMPORT_FEEDBACK_SECRET);
        assertThat(job.at("/result/sections/0/sourceReferences/0/path").asText()).isEqualTo(SOURCE_PATH);
    }
}
