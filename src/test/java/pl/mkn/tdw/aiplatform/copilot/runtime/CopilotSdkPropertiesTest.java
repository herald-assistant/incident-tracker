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
    void shouldResolveConfiguredGithubHost() {
        var properties = new CopilotSdkProperties();
        assertEquals("github.com", properties.resolvedGithubHost());

        properties.setGithubHost(" crm.ghe.com ");

        assertEquals("crm.ghe.com", properties.resolvedGithubHost());
    }

    @Test
    void shouldRejectMissingGithubHost() {
        var properties = new CopilotSdkProperties();
        properties.setGithubHost(null);
        assertThrows(IllegalStateException.class, properties::resolvedGithubHost);

        properties.setGithubHost(" ");
        var failure = assertThrows(IllegalStateException.class, properties::initializeRuntimeConfiguration);
        assertEquals("analysis.ai.copilot.github-host must not be blank", failure.getMessage());
    }

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
