package pl.mkn.incidenttracker.aiplatform.copilot.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSdkProperties;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotSkillRuntimeLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotSkillRuntimeLoaderTest {

    @TempDir
    Path tempDirectory;

    @Test
    void shouldUseRuntimeDirectoryAsBaseDirectory() {
        var properties = new CopilotSdkProperties();

        assertTrue(Path.of(properties.getSkillRuntimeDirectory())
                .endsWith(Path.of("incident-tracker", "copilot-runtime")));
    }

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
        var skillContent = Files.readString(skillFile);
        assertTrue(skillContent.contains("name: incident-analysis-gitlab-tools"));
        assertTrue(skillContent.contains("# Incident Analysis With GitLab Tools"));
    }

    @Test
    void shouldFallbackToDevelopmentResourceRootWhenClasspathSkillsAreMissing() throws Exception {
        var projectRoot = tempDirectory.resolve("project");
        var sourceSkillRoot = projectRoot
                .resolve(Path.of("src", "main", "resources", "copilot", "dev-skills", "local-skill"));
        Files.createDirectories(sourceSkillRoot);
        Files.writeString(
                sourceSkillRoot.resolve("SKILL.md"),
                """
                ---
                name: local-skill
                ---

                # Local Skill
                """
        );

        var properties = new CopilotSdkProperties();
        properties.setWorkingDirectory(projectRoot.toString());
        properties.setSkillResourceRoots(List.of("copilot/dev-skills"));
        properties.setSkillRuntimeDirectory(tempDirectory.resolve("runtime").toString());

        var loader = new CopilotSkillRuntimeLoader(properties);

        var resolvedDirectories = loader.resolveSkillDirectories();

        assertEquals(1, resolvedDirectories.size());
        assertEquals(
                projectRoot.resolve(Path.of("src", "main", "resources", "copilot", "dev-skills")).toString(),
                resolvedDirectories.get(0)
        );

        var runtimeRoot = Path.of(resolvedDirectories.get(0));
        var skillFile = runtimeRoot.resolve("local-skill").resolve("SKILL.md");

        assertTrue(Files.exists(skillFile));
        assertTrue(Files.readString(skillFile).contains("name: local-skill"));
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
