package pl.mkn.incidenttracker.analysis.ai.copilot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSdkProperties;
import pl.mkn.incidenttracker.analysis.ai.copilot.preparation.CopilotSkillRuntimeLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotSkillRuntimeLoaderTest {

    @TempDir
    Path tempDirectory;

    @Test
    void shouldExtractClasspathSkillsToRuntimeDirectory() throws Exception {
        var properties = new CopilotSdkProperties();
        properties.setSkillResourceRoots(List.of("copilot/skills"));
        properties.setSkillRuntimeDirectory(tempDirectory.toString());

        var loader = new CopilotSkillRuntimeLoader(properties);

        var resolvedDirectories = loader.resolveSkillDirectories();

        assertEquals(1, resolvedDirectories.size());

        var runtimeRoot = Path.of(resolvedDirectories.get(0));
        var skillFile = runtimeRoot.resolve("incident-analysis-gitlab-tools").resolve("SKILL.md");

        assertTrue(Files.exists(skillFile));
        assertTrue(Files.readString(skillFile).contains("# Incident Analysis With GitLab And Elastic Tools"));
    }

    @Test
    void shouldAppendAdditionalExternalSkillDirectories() {
        var properties = new CopilotSdkProperties();
        properties.setSkillResourceRoots(List.of());
        properties.setSkillDirectories(List.of("C:\\external\\skills"));
        properties.setSkillRuntimeDirectory(tempDirectory.toString());

        var loader = new CopilotSkillRuntimeLoader(properties);

        var resolvedDirectories = loader.resolveSkillDirectories();

        assertEquals(List.of("C:\\external\\skills"), resolvedDirectories);
    }

}
