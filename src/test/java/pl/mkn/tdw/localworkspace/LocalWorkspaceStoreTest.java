package pl.mkn.tdw.localworkspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mkn.tdw.localworkspace.analysisruns.FileSystemLocalAnalysisRunStore;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunContinuation;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspaceJsonFileStore;
import pl.mkn.tdw.localworkspace.storage.LocalWorkspacePaths;
import pl.mkn.tdw.localworkspace.tokens.FileSystemLocalAccessTokenStore;
import pl.mkn.tdw.localworkspace.tokens.LocalAccessTokenRecord;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalWorkspaceStoreTest {

    private static final Instant CREATED_AT = Instant.parse("2026-06-20T10:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-06-20T10:05:00Z");
    private static final Instant COMPLETED_AT = Instant.parse("2026-06-20T10:06:00Z");

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @TempDir
    Path tempDir;

    @Test
    void shouldSaveRunRecordAndKeepIndexLightweight() throws Exception {
        var fixture = fixture(true);

        fixture.runStore.save(indexEntry("analysis-1", "incident-analysis", "corr-123"), runRecord("analysis-1"));

        assertTrue(Files.exists(fixture.paths.indexFile()));
        assertTrue(Files.exists(fixture.paths.runFile("analysis-1")));

        var indexJson = Files.readString(fixture.paths.indexFile(), StandardCharsets.UTF_8);
        assertTrue(indexJson.contains("\"name\" : \"corr-123\""));
        assertFalse(indexJson.contains("very large prepared prompt"));
        assertFalse(indexJson.contains("dev3"));
        assertFalse(indexJson.contains("main"));
        assertFalse(indexJson.contains("gpt-5.4"));

        var runs = fixture.runStore.listRuns();

        assertEquals(1, runs.size());
        assertEquals("analysis-1", runs.get(0).analysisId());
        assertEquals("runs/analysis-1/run.json", runs.get(0).runPath());
        assertEquals("incident-analysis", runs.get(0).feature());
        assertEquals("corr-123", runs.get(0).name());
    }

    @Test
    void shouldListRunsFromIndexWithoutReadingRunJsonFiles() throws Exception {
        var fixture = fixture(true);
        fixture.runStore.save(indexEntry("analysis-1", "incident-analysis", "corr-123"), runRecord("analysis-1"));
        Files.writeString(fixture.paths.runFile("analysis-1"), "{broken-json", StandardCharsets.UTF_8);

        var runs = fixture.runStore.listRuns();

        assertEquals(1, runs.size());
        assertEquals("analysis-1", runs.get(0).analysisId());
        assertTrue(fixture.runStore.findById("analysis-1").isEmpty());
    }

    @Test
    void shouldRenameRunInIndexOnly() throws Exception {
        var fixture = fixture(true);
        fixture.runStore.save(indexEntry("analysis-1", "incident-analysis", "corr-123"), runRecord("analysis-1"));

        fixture.runStore.rename("analysis-1", "Awaria koszyka w dev3");

        var runs = fixture.runStore.listRuns();
        var storedRun = fixture.runStore.findById("analysis-1");
        assertEquals("Awaria koszyka w dev3", runs.get(0).name());
        assertTrue(storedRun.isPresent());
        assertFalse(Files.readString(fixture.paths.runFile("analysis-1"), StandardCharsets.UTF_8)
                .contains("Awaria koszyka w dev3"));
    }

    @Test
    void shouldNotCreateWorkspaceWhenStoreIsDisabled() {
        var fixture = fixture(false);

        fixture.runStore.save(indexEntry("analysis-1", "incident-analysis", "corr-123"), runRecord("analysis-1"));
        fixture.tokenStore.save(new LocalAccessTokenRecord(
                "local-token",
                "ghu_secret_token",
                "Local token",
                UPDATED_AT
        ));

        assertFalse(Files.exists(fixture.paths.root()));
        assertTrue(fixture.runStore.listRuns().isEmpty());
        assertTrue(fixture.tokenStore.listTokens().isEmpty());
    }

    @Test
    void shouldStoreAccessTokensOnlyInTokensJson() throws Exception {
        var fixture = fixture(true);

        fixture.tokenStore.save(new LocalAccessTokenRecord(
                "local-token",
                "ghu_secret_token",
                "Local token",
                UPDATED_AT
        ));
        fixture.runStore.save(indexEntry("analysis-1", "incident-analysis", "corr-123"), runRecord("analysis-1"));

        var indexJson = Files.readString(fixture.paths.indexFile(), StandardCharsets.UTF_8);
        var runJson = Files.readString(fixture.paths.runFile("analysis-1"), StandardCharsets.UTF_8);
        var tokensJson = Files.readString(fixture.paths.tokensFile(), StandardCharsets.UTF_8);

        assertTrue(tokensJson.contains("ghu_secret_token"));
        assertFalse(indexJson.contains("ghu_secret_token"));
        assertFalse(runJson.contains("ghu_secret_token"));
        assertEquals("ghu_secret_token", fixture.tokenStore.findByRef("local-token").orElseThrow().accessToken());
    }

    private Fixture fixture(boolean enabled) {
        var properties = new LocalWorkspaceProperties();
        properties.setEnabled(enabled);
        properties.setDirectory(tempDir.resolve("tdw-data").toString());
        var paths = new LocalWorkspacePaths(properties);
        var jsonFileStore = new LocalWorkspaceJsonFileStore(objectMapper);
        return new Fixture(
                paths,
                new FileSystemLocalAnalysisRunStore(properties, paths, jsonFileStore),
                new FileSystemLocalAccessTokenStore(properties, paths, jsonFileStore)
        );
    }

    private LocalAnalysisRunIndexEntry indexEntry(String analysisId, String feature, String name) {
        return new LocalAnalysisRunIndexEntry(
                analysisId,
                LocalAnalysisRunRecord.SCHEMA,
                LocalAnalysisRunRecord.VERSION,
                "runs/" + analysisId + "/run.json",
                feature,
                name,
                CREATED_AT,
                UPDATED_AT,
                COMPLETED_AT
        );
    }

    private LocalAnalysisRunRecord runRecord(String analysisId) {
        var envelope = objectMapper.createObjectNode();
        envelope.put("schema", "tdw.analysis-export");
        envelope.put("version", 1);
        envelope.put("exportedAt", COMPLETED_AT.toString());
        var payload = envelope.putObject("payload");
        payload.put("type", "analysis-job");
        var job = payload.putObject("job");
        job.put("analysisId", analysisId);
        job.put("correlationId", "corr-123");
        job.put("status", "COMPLETED");
        job.put("environment", "dev3");
        job.put("gitLabBranch", "main");
        job.put("aiModel", "gpt-5.4");
        job.put("preparedPrompt", "very large prepared prompt");

        return LocalAnalysisRunRecord.v1(
                envelope,
                new LocalAnalysisRunContinuation(true, "CRM/runtime", "LOCAL_TOKEN", "local-token")
        );
    }

    private record Fixture(
            LocalWorkspacePaths paths,
            FileSystemLocalAnalysisRunStore runStore,
            FileSystemLocalAccessTokenStore tokenStore
    ) {
    }
}
