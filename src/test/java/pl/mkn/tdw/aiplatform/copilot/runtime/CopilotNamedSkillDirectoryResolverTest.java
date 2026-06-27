package pl.mkn.tdw.aiplatform.copilot.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

        assertSelectedSkillRoot(
                directories,
                List.of("flow-explorer-orchestrator", "flow-explorer-result-contract"),
                List.of("incident-analysis-orchestrator")
        );
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

        assertSelectedSkillRoot(directories, List.of("flow-explorer-orchestrator"), List.of());
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

    private static void assertSelectedSkillRoot(
            List<String> directories,
            List<String> expectedSkillNames,
            List<String> unexpectedSkillNames
    ) {
        assertEquals(1, directories.size());
        var selectedRoot = Path.of(directories.get(0));
        assertTrue(Files.isDirectory(selectedRoot));
        for (var expectedSkillName : expectedSkillNames) {
            assertTrue(
                    Files.isRegularFile(selectedRoot.resolve(expectedSkillName).resolve("SKILL.md")),
                    () -> "Missing selected skill in root: " + expectedSkillName
            );
        }
        for (var unexpectedSkillName : unexpectedSkillNames) {
            assertFalse(
                    Files.exists(selectedRoot.resolve(unexpectedSkillName)),
                    () -> "Unexpected skill in selected root: " + unexpectedSkillName
            );
        }
    }
}
