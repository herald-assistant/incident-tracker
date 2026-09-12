package pl.mkn.tdw.aiplatform.copilot.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotSdkPropertiesTest {

    @TempDir
    Path tempDirectory;

    @Test
    void shouldCreateConfiguredCliWorkingDirectoryBeforeRuntimeStarts() {
        var properties = new CopilotSdkProperties();
        var workingDirectory = tempDirectory.resolve("neutral").resolve("copilot-workspace");
        properties.setWorkingDirectory(workingDirectory.toString());

        properties.initializeRuntimeConfiguration();

        assertTrue(Files.isDirectory(workingDirectory));
        assertEquals(workingDirectory.toAbsolutePath().normalize().toString(), properties.getWorkingDirectory());
    }

    @Test
    void shouldFailForMissingOrUnusableCliWorkingDirectory() throws Exception {
        var properties = new CopilotSdkProperties();
        assertThrows(IllegalStateException.class, properties::initializeRuntimeConfiguration);

        properties.setWorkingDirectory(" ");
        assertThrows(IllegalStateException.class, properties::initializeRuntimeConfiguration);

        var regularFile = Files.createFile(tempDirectory.resolve("not-a-directory"));
        properties.setWorkingDirectory(regularFile.toString());
        assertThrows(IllegalStateException.class, properties::initializeRuntimeConfiguration);

        properties.setWorkingDirectory("invalid\0path");
        var invalidPath = assertThrows(IllegalStateException.class, properties::initializeRuntimeConfiguration);
        assertTrue(invalidPath.getMessage().contains("analysis.ai.copilot.working-directory"));
    }
}
