package pl.mkn.tdw.aiplatform.copilot.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRuntimeSkillState.CUSTOM;
import static pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRuntimeSkillState.DEFAULT;

class CopilotSkillRuntimeLoaderTest {

    @TempDir
    Path tempDirectory;

    @Test
    void shouldResolvePlatformSkillDirectoryUnderCopilotHome() {
        var properties = new CopilotSdkProperties();

        assertTrue(properties.resolvedSkillDirectory().endsWith(Path.of("tdw-data", "copilot", "skills")));
    }

    @Test
    void shouldSeedAllClasspathSkillsAsDefault() throws Exception {
        var properties = propertiesWithCopilotHome(tempDirectory.resolve("copilot"));
        var loader = new CopilotSkillRuntimeLoader(properties);

        var directories = loader.platformSkillDirectories();

        assertEquals(List.of(properties.resolvedSkillDirectory().toString()), directories);
        var skillFile = skillFile(properties, "incident-analysis-orchestrator");
        assertTrue(Files.isRegularFile(skillFile));
        assertTrue(Files.readString(skillFile).contains("name: incident-analysis-orchestrator"));
        assertTrue(loader.availableSkillNames().containsAll(List.of(
                "incident-analysis-orchestrator",
                "flow-explorer-orchestrator",
                "ui-explorer-orchestrator",
                "change-verification-orchestrator",
                "config-drift-viewer-deep-review",
                "delivery-complexity-assessment-evaluator"
        )));
        var skill = skill(loader, "incident-analysis-orchestrator");
        assertTrue(skill.description().startsWith("Glowny starter analizy incydentu"));
        assertTrue(skill.markdown().contains("# Skill Orkiestratora Analizy Incydentu"));
        assertFalse(skill.markdown().contains("name: incident-analysis-orchestrator"));
        assertTrue(skill.rawMarkdown().startsWith("---\n"));
        assertEquals(Math.toIntExact(skill.rawMarkdown().lines().count()), skill.lineCount());
        assertEquals(DEFAULT, skill.state());
        assertTrue(skill.restoreAvailable());
    }

    @Test
    void shouldPreserveExistingEffectiveSkillAcrossRestart() throws Exception {
        var properties = propertiesWithCopilotHome(tempDirectory.resolve("copilot"));
        var firstLoader = new CopilotSkillRuntimeLoader(properties);
        firstLoader.platformSkillDirectories();
        var custom = skillContent("incident-analysis-orchestrator", "Custom description.", "# Custom instructions");
        Files.writeString(skillFile(properties, "incident-analysis-orchestrator"), custom);

        var restartedLoader = new CopilotSkillRuntimeLoader(properties);

        var skill = skill(restartedLoader, "incident-analysis-orchestrator");
        assertThat(skill.rawMarkdown()).isEqualTo(custom);
        assertThat(skill.state()).isEqualTo(CUSTOM);
        assertThat(skill.description()).isEqualTo("Custom description.");
    }

    @Test
    void shouldAddOnlyMissingPackagedFilesOnRestart() throws Exception {
        var properties = propertiesWithCopilotHome(tempDirectory.resolve("copilot"));
        var firstLoader = new CopilotSkillRuntimeLoader(properties);
        firstLoader.platformSkillDirectories();
        var preservedFile = skillFile(properties, "incident-analysis-orchestrator");
        var custom = skillContent("incident-analysis-orchestrator", "Custom description.", "# Custom instructions");
        Files.writeString(preservedFile, custom);
        var missingFile = skillFile(properties, "flow-explorer-orchestrator");
        Files.delete(missingFile);

        var restartedLoader = new CopilotSkillRuntimeLoader(properties);
        restartedLoader.platformSkillDirectories();

        assertThat(Files.readString(preservedFile)).isEqualTo(custom);
        assertThat(Files.readString(missingFile)).contains("name: flow-explorer-orchestrator");
        assertThat(skill(restartedLoader, "incident-analysis-orchestrator").state()).isEqualTo(CUSTOM);
        assertThat(skill(restartedLoader, "flow-explorer-orchestrator").state()).isEqualTo(DEFAULT);
    }

    @Test
    void shouldUpdateOneSkillAtomicallyAndRestorePackagedDefault() throws Exception {
        var properties = propertiesWithCopilotHome(tempDirectory.resolve("copilot"));
        var loader = new CopilotSkillRuntimeLoader(properties);
        loader.platformSkillDirectories();
        var originalOtherSkill = Files.readString(skillFile(properties, "flow-explorer-orchestrator"));
        var custom = skillContent("incident-analysis-orchestrator", "Custom description.", "# Custom instructions");

        var updated = loader.updateSkill("incident-analysis-orchestrator", custom.replace("\n", "\r\n"));

        assertThat(updated.state()).isEqualTo(CUSTOM);
        assertThat(updated.rawMarkdown()).isEqualTo(custom);
        assertThat(Files.readString(skillFile(properties, "incident-analysis-orchestrator"))).isEqualTo(custom);
        assertThat(Files.readString(skillFile(properties, "flow-explorer-orchestrator")))
                .isEqualTo(originalOtherSkill);
        assertThat(skill(loader, "incident-analysis-orchestrator").state()).isEqualTo(CUSTOM);

        var latest = skillContent(
                "incident-analysis-orchestrator",
                "Latest description.",
                "# Latest instructions"
        );
        loader.updateSkill("incident-analysis-orchestrator", latest);
        assertThat(Files.readString(skillFile(properties, "incident-analysis-orchestrator")))
                .isEqualTo(latest);
        assertThat(skill(loader, "incident-analysis-orchestrator").rawMarkdown()).isEqualTo(latest);

        var restored = loader.restoreDefault("incident-analysis-orchestrator");

        assertThat(restored.state()).isEqualTo(DEFAULT);
        assertThat(Files.readString(skillFile(properties, "incident-analysis-orchestrator")))
                .isEqualTo(restored.rawMarkdown());
    }

    @Test
    void shouldRejectInvalidCandidateWithoutChangingEffectiveFileOrSnapshot() throws Exception {
        var properties = propertiesWithCopilotHome(tempDirectory.resolve("copilot"));
        var loader = new CopilotSkillRuntimeLoader(properties);
        var before = skill(loader, "incident-analysis-orchestrator");
        var file = skillFile(properties, before.name());
        var fileBefore = Files.readString(file);

        assertThatThrownBy(() -> loader.updateSkill(before.name(), "# Missing frontmatter"))
                .isInstanceOf(CopilotSkillCatalogException.class)
                .extracting(exception -> ((CopilotSkillCatalogException) exception).code())
                .isEqualTo(CopilotSkillCatalogException.Code.INVALID_CONTENT);
        assertThat(Files.readString(file)).isEqualTo(fileBefore);
        assertThat(skill(loader, before.name())).isEqualTo(before);
    }

    @Test
    void shouldRejectFrontmatterNameMismatch() {
        var properties = propertiesWithCopilotHome(tempDirectory.resolve("copilot"));
        var loader = new CopilotSkillRuntimeLoader(properties);
        loader.platformSkillDirectories();

        assertThatThrownBy(() -> loader.updateSkill(
                "incident-analysis-orchestrator",
                skillContent("other-skill", "Description.", "# Other")
        ))
                .isInstanceOf(CopilotSkillCatalogException.class)
                .extracting(exception -> ((CopilotSkillCatalogException) exception).code())
                .isEqualTo(CopilotSkillCatalogException.Code.NAME_MISMATCH);
    }

    @Test
    void shouldExposeEffectiveOnlySkillAsCustomWithoutRestore() throws Exception {
        var properties = propertiesWithCopilotHome(tempDirectory.resolve("copilot"));
        var firstLoader = new CopilotSkillRuntimeLoader(properties);
        firstLoader.platformSkillDirectories();
        writeSkill(properties.resolvedSkillDirectory().resolve("local-only-skill"), "local-only-skill");

        var restartedLoader = new CopilotSkillRuntimeLoader(properties);
        var localOnly = skill(restartedLoader, "local-only-skill");

        assertThat(localOnly.state()).isEqualTo(CUSTOM);
        assertThat(localOnly.restoreAvailable()).isFalse();
        assertThatThrownBy(() -> restartedLoader.restoreDefault("local-only-skill"))
                .isInstanceOf(CopilotSkillCatalogException.class)
                .extracting(exception -> ((CopilotSkillCatalogException) exception).code())
                .isEqualTo(CopilotSkillCatalogException.Code.DEFAULT_UNAVAILABLE);
    }

    @Test
    void shouldKeepPreviousFileWhenAtomicReplaceFails() throws Exception {
        var properties = propertiesWithCopilotHome(tempDirectory.resolve("copilot"));
        var loader = new FailingReplaceLoader(properties);
        var before = skill(loader, "incident-analysis-orchestrator");
        var file = skillFile(properties, before.name());

        assertThatThrownBy(() -> loader.updateSkill(
                before.name(),
                skillContent(before.name(), "Custom.", "# Custom")
        ))
                .isInstanceOf(CopilotSkillCatalogException.class)
                .extracting(exception -> ((CopilotSkillCatalogException) exception).code())
                .isEqualTo(CopilotSkillCatalogException.Code.STORAGE_UNAVAILABLE);
        assertThat(Files.readString(file)).isEqualTo(before.rawMarkdown());
        assertThat(skill(loader, before.name())).isEqualTo(before);
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
    void shouldFailWhenEffectiveSkillDirectoryHasNoSkillDefinition() throws Exception {
        var properties = propertiesWithCopilotHome(tempDirectory.resolve("copilot"));
        var firstLoader = new CopilotSkillRuntimeLoader(properties);
        firstLoader.platformSkillDirectories();
        Files.createDirectories(properties.resolvedSkillDirectory().resolve("broken-skill"));
        Files.writeString(properties.resolvedSkillDirectory().resolve("broken-skill").resolve("README.md"), "broken");
        var restartedLoader = new CopilotSkillRuntimeLoader(properties);

        var exception = assertThrows(IllegalStateException.class, restartedLoader::platformSkillDirectories);

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

    private CopilotRuntimeSkill skill(CopilotSkillRuntimeLoader loader, String skillName) {
        return loader.availableSkills().stream()
                .filter(candidate -> candidate.name().equals(skillName))
                .findFirst()
                .orElseThrow();
    }

    private Path skillFile(CopilotSdkProperties properties, String skillName) {
        return properties.resolvedSkillDirectory().resolve(skillName).resolve("SKILL.md");
    }

    private CopilotSdkProperties propertiesWithCopilotHome(Path copilotHome) {
        var properties = new CopilotSdkProperties();
        properties.setCopilotHome(copilotHome.toString());
        return properties;
    }

    private void writeSkill(Path skillDirectory, String skillName) throws Exception {
        Files.createDirectories(skillDirectory);
        Files.writeString(skillDirectory.resolve("SKILL.md"), skillContent(skillName, "Test skill.", "# Test Skill"));
    }

    private String skillContent(String skillName, String description, String body) {
        return """
                ---
                name: %s
                description: %s
                ---

                %s
                """.formatted(skillName, description, body);
    }

    private static final class FailingReplaceLoader extends CopilotSkillRuntimeLoader {

        private FailingReplaceLoader(CopilotSdkProperties properties) {
            super(properties);
        }

        @Override
        protected void replaceFile(Path source, Path target) throws IOException {
            throw new IOException("Synthetic replace failure");
        }
    }
}
