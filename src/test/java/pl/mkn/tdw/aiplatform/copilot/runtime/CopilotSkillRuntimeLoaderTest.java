package pl.mkn.tdw.aiplatform.copilot.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotSkillRuntimeLoaderTest {

    @TempDir
    Path tempDirectory;

    @Test
    void shouldResolvePlatformSkillDirectoryUnderCopilotHome() {
        var properties = new CopilotSdkProperties();

        assertTrue(properties.resolvedSkillDirectory().endsWith(Path.of("tdw-data", "copilot", "skills")));
    }

    @Test
    void shouldMirrorAllClasspathSkillsToPlatformDirectory() throws Exception {
        var properties = propertiesWithCopilotHome(tempDirectory.resolve("copilot"));
        var loader = new CopilotSkillRuntimeLoader(properties);

        var directories = loader.platformSkillDirectories();

        assertEquals(List.of(properties.resolvedSkillDirectory().toString()), directories);
        var skillFile = properties.resolvedSkillDirectory()
                .resolve("incident-analysis-orchestrator")
                .resolve("SKILL.md");
        assertTrue(Files.isRegularFile(skillFile));
        assertTrue(Files.readString(skillFile).contains("name: incident-analysis-orchestrator"));
        assertTrue(loader.availableSkillNames().containsAll(List.of(
                "incident-analysis-orchestrator",
                "flow-explorer-orchestrator",
                "change-verification-orchestrator",
                "config-drift-viewer-deep-review"
        )));
    }

    @Test
    void shouldReplacePreviousPlatformMirrorWithoutSelectedRoots() throws Exception {
        var properties = propertiesWithCopilotHome(tempDirectory.resolve("copilot"));
        var targetRoot = properties.resolvedSkillDirectory();
        Files.createDirectories(targetRoot.resolve("selected-skills-stale"));
        Files.writeString(targetRoot.resolve("stale.txt"), "stale");

        var loader = new CopilotSkillRuntimeLoader(properties);
        loader.platformSkillDirectories();

        assertFalse(Files.exists(targetRoot.resolve("stale.txt")));
        assertFalse(Files.exists(targetRoot.resolve("selected-skills-stale")));
        try (var paths = Files.list(targetRoot)) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().startsWith("selected-skills-")));
        }
    }

    @Test
    void shouldCopyDevelopmentResourcesIntoTheSamePlatformDirectory() throws Exception {
        var projectRoot = tempDirectory.resolve("project");
        var sourceRoot = projectRoot.resolve(Path.of("src", "main", "resources", "copilot", "dev-skills"));
        writeSkill(sourceRoot.resolve("local-skill"), "local-skill");
        var properties = propertiesWithCopilotHome(tempDirectory.resolve("platform-copilot"));
        properties.setWorkingDirectory(projectRoot.toString());
        properties.setSkillResourceRoot("copilot/dev-skills");
        var loader = new CopilotSkillRuntimeLoader(properties);

        var directories = loader.platformSkillDirectories();

        assertEquals(List.of(properties.resolvedSkillDirectory().toString()), directories);
        assertTrue(Files.isRegularFile(
                properties.resolvedSkillDirectory().resolve("local-skill").resolve("SKILL.md")
        ));
        assertFalse(directories.contains(sourceRoot.toString()));
    }

    @Test
    void shouldFailWhenPackagedSkillRootDoesNotExist() {
        var properties = propertiesWithCopilotHome(tempDirectory.resolve("copilot"));
        properties.setWorkingDirectory(tempDirectory.resolve("project").toString());
        properties.setSkillResourceRoot("copilot/missing-skills");
        var loader = new CopilotSkillRuntimeLoader(properties);

        var exception = assertThrows(IllegalStateException.class, loader::platformSkillDirectories);

        assertTrue(exception.getMessage().contains("No Copilot skills were found"));
    }

    @Test
    void shouldFailWhenSkillDirectoryHasNoSkillDefinition() throws Exception {
        var projectRoot = tempDirectory.resolve("project");
        var sourceRoot = projectRoot.resolve(Path.of("src", "main", "resources", "copilot", "broken-skills"));
        Files.createDirectories(sourceRoot.resolve("broken-skill"));
        Files.writeString(sourceRoot.resolve("broken-skill").resolve("README.md"), "broken");
        var properties = propertiesWithCopilotHome(tempDirectory.resolve("copilot"));
        properties.setWorkingDirectory(projectRoot.toString());
        properties.setSkillResourceRoot("copilot/broken-skills");
        var loader = new CopilotSkillRuntimeLoader(properties);

        var exception = assertThrows(IllegalStateException.class, loader::platformSkillDirectories);

        assertTrue(exception.getMessage().contains("Missing SKILL.md"));
    }

    @Test
    void shouldFailWhenFrontmatterNameDoesNotMatchDirectory() throws Exception {
        var projectRoot = tempDirectory.resolve("project");
        var sourceRoot = projectRoot.resolve(Path.of("src", "main", "resources", "copilot", "invalid-skills"));
        writeSkill(sourceRoot.resolve("alpha-skill"), "beta-skill");
        var properties = propertiesWithCopilotHome(tempDirectory.resolve("copilot"));
        properties.setWorkingDirectory(projectRoot.toString());
        properties.setSkillResourceRoot("copilot/invalid-skills");
        var loader = new CopilotSkillRuntimeLoader(properties);

        var exception = assertThrows(IllegalStateException.class, loader::platformSkillDirectories);

        assertTrue(exception.getMessage().contains("must match directory 'alpha-skill'"));
    }

    private CopilotSdkProperties propertiesWithCopilotHome(Path copilotHome) {
        var properties = new CopilotSdkProperties();
        properties.setCopilotHome(copilotHome.toString());
        return properties;
    }

    private void writeSkill(Path skillDirectory, String skillName) throws Exception {
        Files.createDirectories(skillDirectory);
        Files.writeString(
                skillDirectory.resolve("SKILL.md"),
                """
                ---
                name: %s
                description: Test skill.
                ---

                # Test Skill
                """.formatted(skillName)
        );
    }
}
