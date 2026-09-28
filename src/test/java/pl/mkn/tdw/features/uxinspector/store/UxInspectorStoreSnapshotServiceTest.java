package pl.mkn.tdw.features.uxinspector.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mkn.tdw.features.uxinspector.capture.UxInspectorCapture;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.localworkspace.analysisruns.*;
import pl.mkn.tdw.localworkspace.analysisruns.tools.RunStoreTools;
import pl.mkn.tdw.localworkspace.analysisruns.tools.RunStoreToolSetFactory;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspaceJsonFileStore;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspacePaths;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class UxInspectorStoreSnapshotServiceTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void storesClaimedStateInRunJsonAndReadsItAfterSubsequentSave() throws Exception {
        var runs = runs();
        var service = new UxInspectorStoreSnapshotService(mapper, runs);
        var auth = AnalysisAiAuthRef.localToken("CRM operator");
        var receipt = service.stage(json("""
                {"captureId":"cap_crm_contact_save","origin":"https://crm.example.com",
                 "state":{"contacts":{"selected":{"id":"crm-42","editable":false}},"accessToken":"private"}}
                """), auth);
        var capture = capture("cap_crm_contact_save", "https://crm.example.com");
        var claimed = service.claim(receipt.storeSnapshotRef(), capture, auth);
        assertThat(claimed).isNotNull();
        assertThat(service.claim(receipt.storeSnapshotRef(), capture, auth)).isNull();
        assertThat(claimed.state().at("/accessToken").asText()).isEqualTo("[REDACTED]");

        save(runs, "crm-run", "QUEUED");
        runs.updateStoreSnapshot("crm-run", claimed);
        save(runs, "crm-run", "COMPLETED");
        assertThat(service.find("crm-run").orElseThrow().capturedAt()).isEqualTo(capture.capturedAt());
        assertThat(Files.readString(temp.resolve("runs/crm-run/run.json")))
                .contains("storeSnapshot", "crm-42").doesNotContain("private");
        assertThat(Files.exists(temp.resolve("runs/crm-run/browser-store.json"))).isFalse();

        var tools = new RunStoreTools(runs);
        assertThat(tools.listPaths("crm-run", "", 0).children()).extracting(RunStoreTools.PathEntry::pointer)
                .containsExactly("/contacts", "/accessToken");
        assertThat(tools.readValue("crm-run", "/contacts/selected/editable", 0).jsonFragment()).isEqualTo("false");
        assertThat(tools.readValue("missing-run", "/contacts", 0).status()).isEqualTo("unavailable");
        var callbacks = new RunStoreToolSetFactory(runs).create("crm-run");
        assertThat(callbacks).extracting(callback -> callback.getToolDefinition().name())
                .containsExactlyInAnyOrder(RunStoreTools.LIST, RunStoreTools.READ);
        assertThat(callbacks.get(0).getToolDefinition().inputSchema()).contains("runId", "pointer");
    }

    @Test
    void checksCaptureScopeAndSupportsPointerEscapingAndBoundedChunks() throws Exception {
        var runs = runs();
        var service = new UxInspectorStoreSnapshotService(mapper, runs);
        var auth = AnalysisAiAuthRef.localToken("CRM operator");
        var receipt = service.stage(json("""
                {"captureId":"cap_crm_contact_save","origin":"https://crm.example.com",
                 "state":{"a/b":1,"items":[{"id":"crm-1"}]}}
                """), auth);
        assertThat(service.claim(receipt.storeSnapshotRef(),
                capture("cap_crm_contact_save", "https://wrong.example.com"), auth)).isNull();
        var second = service.stage(json("""
                {"captureId":"cap_crm_contact_save","origin":"https://crm.example.com",
                 "state":{"a/b":1,"items":[{"id":"crm-1"}]}}
                """), auth);
        save(runs, "crm-run", "QUEUED");
        runs.updateStoreSnapshot("crm-run", service.claim(second.storeSnapshotRef(),
                capture("cap_crm_contact_save", "https://crm.example.com"), auth));
        var tools = new RunStoreTools(runs);
        assertThat(tools.listPaths("crm-run", "", 0).children())
                .extracting(RunStoreTools.PathEntry::pointer).containsExactly("/a~1b", "/items");
        assertThat(tools.readValue("crm-run", "/items/0/id", 0).jsonFragment()).isEqualTo("\"crm-1\"");

        runs.updateStoreSnapshot("crm-run", new LocalAnalysisRunStoreSnapshot(
                mapper.createObjectNode().put("note", "C".repeat(20_000)), Instant.now(), "crm", 0));
        var first = tools.readValue("crm-run", "/note", 0);
        assertThat(first.jsonFragment().length()).isLessThanOrEqualTo(12_000);
        assertThat(first.hasMore()).isTrue();
        assertThat(tools.readValue("crm-run", "/note", first.nextOffset()).nextOffset())
                .isGreaterThan(first.nextOffset());
    }

    @Test
    void rejectsMissingStateAndOversizedUploads() {
        var service = new UxInspectorStoreSnapshotService(mapper, runs());
        assertThatThrownBy(() -> service.stage(json("""
                {"captureId":"cap_crm_contact_save","origin":"https://crm.example.com","state":null}
                """), AnalysisAiAuthRef.localToken("CRM operator"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.stage(new ByteArrayInputStream(new byte[UxInspectorStoreSnapshotService.MAX_BYTES + 1]),
                AnalysisAiAuthRef.localToken("CRM operator")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("size limit");
    }

    @Test
    void limitsPathListingEvenWhenStoreHasManyLongKeys() {
        var runs = runs();
        save(runs, "crm-run", "QUEUED");
        var state = mapper.createObjectNode();
        for (var i = 0; i < 100; i++) state.put("contact-" + i + "x".repeat(80), i);
        runs.updateStoreSnapshot("crm-run", new LocalAnalysisRunStoreSnapshot(
                state, Instant.parse("2026-09-15T10:00:00Z"), "https://crm.example.com", 0));
        var tools = new RunStoreTools(runs);
        var first = tools.listPaths("crm-run", "", 0);
        assertThat(first.children().size()).isLessThan(100);
        assertThat(first.hasMore()).isTrue();
        var second = tools.listPaths("crm-run", "", first.children().size());
        assertThat(second.children()).isNotEmpty();
    }

    private FileSystemLocalAnalysisRunStore runs() {
        var properties = new LocalWorkspaceProperties();
        properties.setDirectory(temp.toString());
        return new FileSystemLocalAnalysisRunStore(properties, new LocalWorkspacePaths(properties),
                new LocalWorkspaceJsonFileStore(mapper));
    }

    private void save(FileSystemLocalAnalysisRunStore runs, String id, String status) {
        var now = Instant.parse("2026-09-15T10:00:00Z");
        runs.save(new LocalAnalysisRunIndexEntry(id, LocalAnalysisRunRecord.SCHEMA, 1,
                        "runs/" + id + "/run.json", "ux-inspector", "CRM contact", status, now, now, null),
                LocalAnalysisRunRecord.v1(mapper.createObjectNode().put("status", status), null));
    }

    private ByteArrayInputStream json(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    private UxInspectorCapture capture(String id, String origin) {
        return new UxInspectorCapture(UxInspectorCapture.SCHEMA, UxInspectorCapture.VERSION, id, Instant.parse("2026-09-15T10:00:00Z"),
                UxInspectorCapture.CaptureProfile.ELEMENT_CONTEXT,
                new UxInspectorCapture.Page(origin, "/contacts/new", "CRM", "pl", List.of()),
                null, List.of(), null, null, List.of(), null);
    }
}
