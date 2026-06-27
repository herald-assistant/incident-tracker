package pl.mkn.tdw.aiplatform.copilot.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CopilotNamedSkillDirectoryResolver {

    private final CopilotSkillRuntimeLoader skillRuntimeLoader;

    public List<String> resolveSkillDirectories(List<String> skillNames) {
        var normalizedSkillNames = normalizeSkillNames(skillNames);
        if (normalizedSkillNames.isEmpty()) {
            return List.of();
        }

        var resolvedRoots = skillRuntimeLoader.resolveSkillDirectories();
        return normalizedSkillNames.stream()
                .map(skillName -> resolveRequiredSkillDirectory(skillName, resolvedRoots))
                .map(Path::toString)
                .toList();
    }

    private Path resolveRequiredSkillDirectory(String skillName, List<String> resolvedRoots) {
        for (var rootValue : safeList(resolvedRoots)) {
            if (!StringUtils.hasText(rootValue)) {
                continue;
            }

            var root = Path.of(rootValue);
            var nestedSkillDirectory = root.resolve(skillName);
            if (hasSkillDefinition(nestedSkillDirectory)) {
                return nestedSkillDirectory;
            }
            if (skillName.equals(root.getFileName() != null ? root.getFileName().toString() : null)
                    && hasSkillDefinition(root)) {
                return root;
            }
        }

        throw new IllegalStateException(
                "Missing Copilot runtime skill directory '%s' under resolved skill roots: %s"
                        .formatted(skillName, safeList(resolvedRoots))
        );
    }

    private static boolean hasSkillDefinition(Path directory) {
        return Files.isDirectory(directory) && Files.isRegularFile(directory.resolve("SKILL.md"));
    }

    private static List<String> normalizeSkillNames(List<String> skillNames) {
        if (skillNames == null || skillNames.isEmpty()) {
            return List.of();
        }

        var normalized = new LinkedHashSet<String>();
        for (var skillName : skillNames) {
            if (StringUtils.hasText(skillName)) {
                normalized.add(skillName.trim());
            }
        }
        return List.copyOf(normalized);
    }

    private static <T> List<T> safeList(List<T> values) {
        return values != null ? values : List.of();
    }
}
