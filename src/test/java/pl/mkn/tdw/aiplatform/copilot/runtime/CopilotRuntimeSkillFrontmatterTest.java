package pl.mkn.tdw.aiplatform.copilot.runtime;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CopilotRuntimeSkillFrontmatterTest {

    private static final Path SKILLS_ROOT = Path.of("src", "main", "resources", "copilot", "skills");

    @Test
    void shouldParseAllRuntimeSkillFrontmatter() throws Exception {
        var names = new HashSet<String>();

        try (var paths = Files.list(SKILLS_ROOT)) {
            for (var skillDirectory : paths
                    .filter(Files::isDirectory)
                    .sorted()
                    .toList()) {
                var skillFile = skillDirectory.resolve("SKILL.md");
                assertTrue(Files.isRegularFile(skillFile), () -> "Missing SKILL.md in " + skillDirectory);

                var metadata = parseFrontmatter(skillFile);
                var expectedName = skillDirectory.getFileName().toString();

                assertEquals(expectedName, metadata.get("name"), () -> "Skill name must match directory: " + skillFile);
                assertTrue(names.add(expectedName), () -> "Duplicate runtime skill name: " + expectedName);
                assertStringMetadata(metadata, "description", skillFile);
            }
        }
    }

    private static Map<?, ?> parseFrontmatter(Path skillFile) throws Exception {
        var content = Files.readString(skillFile).replace("\r\n", "\n");
        assertTrue(content.startsWith("---\n"), () -> "Missing YAML frontmatter: " + skillFile);
        var endMarker = content.indexOf("\n---", 4);
        assertTrue(endMarker > 0, () -> "Unclosed YAML frontmatter: " + skillFile);

        var parsed = new Yaml().load(content.substring(4, endMarker));
        assertInstanceOf(Map.class, parsed, () -> "Frontmatter must parse as YAML map: " + skillFile);
        return (Map<?, ?>) parsed;
    }

    private static void assertStringMetadata(Map<?, ?> metadata, String key, Path skillFile) {
        var value = metadata.get(key);
        assertInstanceOf(String.class, value, () -> "Frontmatter key must be a string: " + key + " in " + skillFile);
        assertFalse(((String) value).isBlank(), () -> "Frontmatter key must not be blank: " + key + " in " + skillFile);
    }
}
