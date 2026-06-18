package pl.mkn.incidenttracker.aiplatform.copilot.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CopilotNamedSkillDirectoryResolverTest {

    @TempDir
    Path tempDirectory;

    @Test
    void shouldResolveNamedSkillDirectoriesFromRuntimeRoot() throws Exception {
        createSkill(tempDirectory.resolve("skills").resolve("flow-explorer-orchestrator"));
        createSkill(tempDirectory.resolve("skills").resolve("flow-explorer-result-contract"));
        createSkill(tempDirectory.resolve("skills").resolve("incident-analysis-orchestrator"));
        var properties = new CopilotSdkProperties();
        properties.setSkillResourceRoots(List.of());
        properties.setSkillDirectories(List.of(tempDirectory.resolve("skills").toString()));
        var resolver = new CopilotNamedSkillDirectoryResolver(new CopilotSkillRuntimeLoader(properties));

        var directories = resolver.resolveSkillDirectories(List.of(
                "flow-explorer-orchestrator",
                "flow-explorer-result-contract"
        ));

        assertEquals(List.of(
                tempDirectory.resolve("skills").resolve("flow-explorer-orchestrator").toString(),
                tempDirectory.resolve("skills").resolve("flow-explorer-result-contract").toString()
        ), directories);
    }

    @Test
    void shouldResolveDirectSkillDirectory() throws Exception {
        var skillDirectory = tempDirectory.resolve("flow-explorer-orchestrator");
        createSkill(skillDirectory);
        var properties = new CopilotSdkProperties();
        properties.setSkillResourceRoots(List.of());
        properties.setSkillDirectories(List.of(skillDirectory.toString()));
        var resolver = new CopilotNamedSkillDirectoryResolver(new CopilotSkillRuntimeLoader(properties));

        var directories = resolver.resolveSkillDirectories(List.of("flow-explorer-orchestrator"));

        assertEquals(List.of(skillDirectory.toString()), directories);
    }

    @Test
    void shouldFailWhenNamedSkillIsMissing() {
        var properties = new CopilotSdkProperties();
        properties.setSkillResourceRoots(List.of());
        properties.setSkillDirectories(List.of(tempDirectory.resolve("skills").toString()));
        var resolver = new CopilotNamedSkillDirectoryResolver(new CopilotSkillRuntimeLoader(properties));

        assertThrows(
                IllegalStateException.class,
                () -> resolver.resolveSkillDirectories(List.of("flow-explorer-orchestrator"))
        );
    }

    private static void createSkill(Path directory) throws Exception {
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("SKILL.md"), "---\nname: " + directory.getFileName() + "\n---\n");
    }
}
