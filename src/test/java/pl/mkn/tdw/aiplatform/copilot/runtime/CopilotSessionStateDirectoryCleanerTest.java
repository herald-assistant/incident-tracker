package pl.mkn.tdw.aiplatform.copilot.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotSessionStateDirectoryCleanerTest {

    @TempDir
    Path tempDir;

    @Test
    void shouldDeleteOnlyMatchingSessionStateDirectory() throws Exception {
        var properties = new CopilotSdkProperties();
        properties.setCopilotHome(tempDir.resolve("copilot").toString());
        var cleaner = new CopilotSessionStateDirectoryCleaner(properties);
        var target = tempDir.resolve("copilot").resolve("session-state").resolve("session-1");
        var other = tempDir.resolve("copilot").resolve("session-state").resolve("session-2");
        Files.createDirectories(target);
        Files.createDirectories(other);
        Files.writeString(target.resolve("state.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(other.resolve("state.json"), "{}", StandardCharsets.UTF_8);

        cleaner.deleteSessionStateDirectory("session-1");

        assertFalse(Files.exists(target));
        assertTrue(Files.exists(other));
    }

    @Test
    void shouldSkipUnsafeSessionIds() throws Exception {
        var properties = new CopilotSdkProperties();
        properties.setCopilotHome(tempDir.resolve("copilot").toString());
        var cleaner = new CopilotSessionStateDirectoryCleaner(properties);
        var other = tempDir.resolve("copilot").resolve("session-state").resolve("session-2");
        Files.createDirectories(other);
        Files.writeString(other.resolve("state.json"), "{}", StandardCharsets.UTF_8);

        cleaner.deleteSessionStateDirectory("../session-2");

        assertTrue(Files.exists(other));
    }
}
