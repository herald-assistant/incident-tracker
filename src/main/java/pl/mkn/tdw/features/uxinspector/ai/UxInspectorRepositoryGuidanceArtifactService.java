package pl.mkn.tdw.features.uxinspector.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorContextException;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetContext;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.GitLabVerifiedRepositoryFileReader;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Builds pinned repository guidance without installing remote project skills in the TDW runtime. */
@Service
@RequiredArgsConstructor
public class UxInspectorRepositoryGuidanceArtifactService {

    static final String SCHEMA = "tdw.ux-inspector-repository-guidance";
    static final int VERSION = 1;
    static final String COPILOT_INSTRUCTIONS_PATH = ".github/copilot-instructions.md";
    static final int MAX_COPILOT_INSTRUCTIONS_BYTES = 64 * 1024;
    static final int MAX_PROJECT_SKILLS = 100;
    private static final int MAX_SKILL_BYTES = GitLabVerifiedRepositoryFileReader.MAX_FILE_BYTES;
    private static final Pattern SKILL_PATH = Pattern.compile(
            "^(?:\\.github|\\.claude|\\.agents)/skills/[^/]+/SKILL\\.md$"
    );
    private static final Pattern SKILL_NAME = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");

    private final GitLabRepositoryPort repositoryPort;
    private final ObjectMapper objectMapper;

    public String render(UxInspectorTargetContext context, List<String> repositoryFilePaths) {
        var scope = requireScope(context);
        var paths = repositoryFilePaths == null ? List.<String>of() : repositoryFilePaths.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .sorted()
                .toList();

        var instructions = paths.contains(COPILOT_INSTRUCTIONS_PATH)
                ? presentInstructions(scope)
                : new CopilotInstructions(COPILOT_INSTRUCTIONS_PATH, false, null);
        var skillPaths = paths.stream().filter(path -> SKILL_PATH.matcher(path).matches()).toList();
        if (skillPaths.size() > MAX_PROJECT_SKILLS) {
            throw invalid("The selected repository contains more than " + MAX_PROJECT_SKILLS
                    + " project skills in supported Copilot locations.");
        }

        var names = new LinkedHashSet<String>();
        var skills = skillPaths.stream().map(path -> skillHeader(scope, path, names)).toList();
        var artifact = new RepositoryGuidance(
                SCHEMA,
                VERSION,
                scope.group() + "/" + scope.project(),
                scope.commit(),
                instructions,
                skills
        );
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(artifact);
        } catch (JsonProcessingException exception) {
            throw unavailable("Repository guidance could not be rendered.", exception);
        }
    }

    private CopilotInstructions presentInstructions(RepositoryScope scope) {
        var file = read(scope, COPILOT_INSTRUCTIONS_PATH, MAX_COPILOT_INSTRUCTIONS_BYTES);
        return new CopilotInstructions(file.path(), true, file.content());
    }

    private ProjectSkillHeader skillHeader(RepositoryScope scope, String path, LinkedHashSet<String> names) {
        var file = read(scope, path, MAX_SKILL_BYTES);
        var metadata = parseSkillFrontmatter(file.content(), path);
        var name = requiredString(metadata, "name", path);
        var description = requiredString(metadata, "description", path);
        if (!SKILL_NAME.matcher(name).matches()) {
            throw invalid("Project skill has an invalid lowercase hyphenated name: " + path);
        }
        if (!names.add(name)) {
            throw invalid("Project skill name is not unique: " + name);
        }
        return new ProjectSkillHeader(path, name, description);
    }

    private Map<?, ?> parseSkillFrontmatter(String rawContent, String path) {
        var content = rawContent != null ? rawContent.replace("\r\n", "\n").replace('\r', '\n') : "";
        if (!content.startsWith("---\n")) {
            throw invalid("Project skill is missing YAML frontmatter: " + path);
        }
        var endMarker = content.indexOf("\n---\n", 4);
        if (endMarker < 0 || content.substring(endMarker + 5).isBlank()) {
            throw invalid("Project skill must contain closed YAML frontmatter and a Markdown body: " + path);
        }
        try {
            var options = new LoaderOptions();
            options.setCodePointLimit(MAX_SKILL_BYTES);
            options.setMaxAliasesForCollections(10);
            options.setNestingDepthLimit(20);
            var parsed = new Yaml(new SafeConstructor(options)).load(content.substring(4, endMarker));
            if (parsed instanceof Map<?, ?> metadata) return metadata;
            throw invalid("Project skill frontmatter must be a YAML map: " + path);
        } catch (UxInspectorContextException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalid("Project skill has invalid YAML frontmatter: " + path, exception);
        }
    }

    private String requiredString(Map<?, ?> metadata, String field, String path) {
        var value = metadata.get(field);
        if (value instanceof String text && StringUtils.hasText(text)) return text.trim();
        throw invalid("Project skill frontmatter field '" + field + "' must be a non-blank string: " + path);
    }

    private GitLabVerifiedRepositoryFileReader.VerifiedFile read(RepositoryScope scope, String path, int maxBytes) {
        try {
            return GitLabVerifiedRepositoryFileReader.read(
                    repositoryPort, scope.group(), scope.project(), scope.commit(), path, maxBytes);
        } catch (RuntimeException exception) {
            throw unavailable("Repository guidance file could not be read from the pinned revision: " + path, exception);
        }
    }

    private RepositoryScope requireScope(UxInspectorTargetContext context) {
        if (context == null || context.sourceScope() == null || context.sourceRevision() == null
                || !StringUtils.hasText(context.sourceScope().group())
                || !StringUtils.hasText(context.sourceScope().projectName())
                || !StringUtils.hasText(context.sourceRevision().revision())) {
            throw unavailable("Pinned repository scope is unavailable.", null);
        }
        return new RepositoryScope(
                context.sourceScope().group().trim(),
                context.sourceScope().projectName().trim(),
                context.sourceRevision().revision().trim()
        );
    }

    private UxInspectorContextException invalid(String message) {
        return new UxInspectorContextException(
                "UX_INSPECTOR_REPOSITORY_GUIDANCE_INVALID", UserFacingErrorType.UNPROCESSABLE_ENTITY, message);
    }

    private UxInspectorContextException invalid(String message, RuntimeException cause) {
        var exception = invalid(message);
        exception.initCause(cause);
        return exception;
    }

    private UxInspectorContextException unavailable(String message, Throwable cause) {
        var exception = new UxInspectorContextException(
                "UX_INSPECTOR_REPOSITORY_GUIDANCE_UNAVAILABLE", UserFacingErrorType.SERVICE_UNAVAILABLE, message);
        if (cause != null) exception.initCause(cause);
        return exception;
    }

    private record RepositoryScope(String group, String project, String commit) {
    }

    private record RepositoryGuidance(
            String schema,
            int version,
            String repository,
            String commit,
            CopilotInstructions copilotInstructions,
            List<ProjectSkillHeader> projectSkills
    ) {
    }

    private record CopilotInstructions(String path, boolean present, String content) {
    }

    private record ProjectSkillHeader(String path, String name, String description) {
    }
}
