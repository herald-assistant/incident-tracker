package pl.mkn.tdw.aiplatform.copilot.runtime;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRuntimeSkillState.CUSTOM;
import static pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRuntimeSkillState.DEFAULT;

@Slf4j
@Component
@RequiredArgsConstructor
public class CopilotSkillRuntimeLoader {

    static final int MAX_SKILL_CONTENT_BYTES = 256 * 1024;

    private static final ResourcePatternResolver RESOURCE_RESOLVER = new PathMatchingResourcePatternResolver();

    private final CopilotSdkProperties properties;

    private volatile SkillCatalogState state;

    @PostConstruct
    void initializePlatformSkillCatalog() {
        ensureInitialized();
    }

    public List<String> platformSkillDirectories() {
        return List.of(currentState().root().toString());
    }

    public List<String> availableSkillNames() {
        return currentState().effectiveSkills().stream()
                .map(CopilotRuntimeSkill::name)
                .toList();
    }

    public List<CopilotRuntimeSkill> availableSkills() {
        return currentState().effectiveSkills();
    }

    public synchronized CopilotRuntimeSkill updateSkill(String skillName, String rawMarkdown) {
        var current = currentState();
        requireEffectiveSkill(current, skillName);
        var candidate = parseMutationCandidate(skillName, rawMarkdown);
        writeEffectiveSkill(current.root(), candidate);
        return publishChangedSkill(current, candidate);
    }

    public synchronized CopilotRuntimeSkill restoreDefault(String skillName) {
        var current = currentState();
        requireEffectiveSkill(current, skillName);
        var packaged = definitionsByName(current.packagedSkills()).get(skillName);
        if (packaged == null) {
            throw new CopilotSkillCatalogException(
                    CopilotSkillCatalogException.Code.DEFAULT_UNAVAILABLE,
                    "Packaged default is unavailable for AI skill: " + skillName
            );
        }
        writeEffectiveSkill(current.root(), packaged);
        return publishChangedSkill(current, packaged);
    }

    private SkillCatalogState currentState() {
        ensureInitialized();
        return state;
    }

    private void ensureInitialized() {
        if (state != null) {
            return;
        }

        synchronized (this) {
            if (state != null) {
                return;
            }

            var catalog = initializePersistentCatalog();
            state = catalog;
            log.info(
                    "Copilot platform skill catalog initialized directory={} resourceRoot={} skillCount={} defaultSkillCount={} customSkillCount={} skills={}",
                    catalog.root(),
                    properties.getSkillResourceRoot(),
                    catalog.effectiveSkills().size(),
                    catalog.effectiveSkills().stream().filter(skill -> skill.state() == DEFAULT).count(),
                    catalog.effectiveSkills().stream().filter(skill -> skill.state() == CUSTOM).count(),
                    catalog.effectiveSkills().stream().map(CopilotRuntimeSkill::name).toList()
            );
        }
    }

    private SkillCatalogState initializePersistentCatalog() {
        var targetRoot = properties.resolvedSkillDirectory();
        var parent = targetRoot.getParent();
        var stagingRoot = parent.resolve(".skills-seed-" + UUID.randomUUID()).normalize();

        try {
            Files.createDirectories(parent);
            Files.createDirectory(stagingRoot);
            copyPackagedSkills(stagingRoot);
            var packagedSkills = validateSkillCatalog(stagingRoot);
            seedMissingEffectiveFiles(stagingRoot, targetRoot);
            var effectiveDefinitions = validateSkillCatalog(targetRoot);
            var effectiveSkills = projectEffectiveSkills(effectiveDefinitions, packagedSkills);
            return new SkillCatalogState(targetRoot, packagedSkills, effectiveSkills);
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException illegalStateException) {
                throw illegalStateException;
            }
            throw new IllegalStateException(
                    "Failed to initialize Copilot platform skill catalog: " + targetRoot,
                    exception
            );
        } finally {
            cleanupDirectory(stagingRoot);
        }
    }

    private void copyPackagedSkills(Path targetRoot) throws IOException {
        var resourceRoot = normalizeResourceRoot(properties.getSkillResourceRoot());
        var resources = RESOURCE_RESOLVER.getResources(
                ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + resourceRoot + "/**/*"
        );
        var copiedAnyFile = false;

        for (var resource : resources) {
            if (!resource.exists() || !resource.isReadable()) {
                continue;
            }

            var relativePath = relativePath(resource.getURL().toString(), resourceRoot);
            if (relativePath == null || relativePath.isBlank()) {
                continue;
            }

            var targetFile = resolveWithin(targetRoot, relativePath);
            Files.createDirectories(targetFile.getParent());
            try (var inputStream = resource.getInputStream()) {
                Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
                copiedAnyFile = true;
            }
        }

        if (!copiedAnyFile) {
            var developmentRoot = developmentResourceRoot(resourceRoot);
            if (Files.isDirectory(developmentRoot)) {
                copyDirectoryContents(developmentRoot, targetRoot);
                copiedAnyFile = containsRegularFile(targetRoot);
                if (copiedAnyFile) {
                    log.info("Loaded Copilot skills from development resource root '{}'.", developmentRoot);
                }
            }
        }

        if (!copiedAnyFile) {
            throw new IllegalStateException(
                    "No Copilot skills were found under packaged resource root: " + resourceRoot
            );
        }
    }

    private void seedMissingEffectiveFiles(Path seedRoot, Path targetRoot) throws IOException {
        if (Files.exists(targetRoot) && !Files.isDirectory(targetRoot)) {
            throw new IllegalStateException("Copilot platform skill catalog is not a directory: " + targetRoot);
        }
        Files.createDirectories(targetRoot);

        try (var paths = Files.walk(seedRoot)) {
            for (var seedPath : paths.sorted().toList()) {
                var relative = seedRoot.relativize(seedPath);
                if (relative.toString().isBlank()) {
                    continue;
                }
                var targetPath = targetRoot.resolve(relative).normalize();
                if (!targetPath.startsWith(targetRoot)) {
                    throw new IllegalStateException("Copilot skill seed escaped effective directory: " + relative);
                }
                if (Files.isDirectory(seedPath)) {
                    Files.createDirectories(targetPath);
                    continue;
                }
                if (!Files.isRegularFile(seedPath) || Files.exists(targetPath)) {
                    continue;
                }
                Files.createDirectories(targetPath.getParent());
                try {
                    Files.copy(seedPath, targetPath);
                    log.info("Added missing packaged Copilot skill file relativePath={}", relative);
                } catch (FileAlreadyExistsException ignored) {
                    // Another local initializer supplied the same missing file.
                }
            }
        }
    }

    private List<SkillDefinition> validateSkillCatalog(Path root) throws IOException {
        var skillNames = new LinkedHashSet<String>();
        var skills = new ArrayList<SkillDefinition>();
        List<Path> skillDirectories;
        try (var paths = Files.list(root)) {
            skillDirectories = paths
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }

        if (skillDirectories.isEmpty()) {
            throw new IllegalStateException("Copilot platform skill catalog contains no skill directories: " + root);
        }

        for (var skillDirectory : skillDirectories) {
            var skillFile = skillDirectory.resolve("SKILL.md");
            if (!Files.isRegularFile(skillFile)) {
                throw new IllegalStateException("Missing SKILL.md in Copilot skill directory: " + skillDirectory);
            }

            var parsedSkill = parseSkillFile(skillFile);
            var expectedName = skillDirectory.getFileName().toString();
            if (!expectedName.equals(parsedSkill.name())) {
                throw new IllegalStateException(
                        "Copilot skill frontmatter name '%s' must match directory '%s'"
                                .formatted(parsedSkill.name(), expectedName)
                );
            }
            if (!skillNames.add(parsedSkill.name())) {
                throw new IllegalStateException("Duplicate Copilot skill name: " + parsedSkill.name());
            }
            skills.add(parsedSkill);
        }

        return List.copyOf(skills);
    }

    private SkillDefinition parseSkillFile(Path skillFile) throws IOException {
        var content = Files.readString(skillFile, StandardCharsets.UTF_8);
        return parseSkillContent(content, skillFile.toString());
    }

    private SkillDefinition parseMutationCandidate(String expectedName, String rawMarkdown) {
        if (rawMarkdown == null || rawMarkdown.isBlank()) {
            throw invalidContent("AI skill content must not be blank.", null);
        }
        var normalized = normalizeContent(rawMarkdown);
        if (normalized.getBytes(StandardCharsets.UTF_8).length > MAX_SKILL_CONTENT_BYTES) {
            throw invalidContent(
                    "AI skill content exceeds the maximum size of " + MAX_SKILL_CONTENT_BYTES + " bytes.",
                    null
            );
        }

        final SkillDefinition candidate;
        try {
            candidate = parseSkillContent(normalized, "AI skill " + expectedName);
        } catch (IllegalStateException exception) {
            throw invalidContent("AI skill content failed validation.", exception);
        }
        if (!expectedName.equals(candidate.name())) {
            throw new CopilotSkillCatalogException(
                    CopilotSkillCatalogException.Code.NAME_MISMATCH,
                    "AI skill frontmatter name must match path skill name: " + expectedName
            );
        }
        return candidate;
    }

    private SkillDefinition parseSkillContent(String rawContent, String sourceLabel) {
        var content = normalizeContent(rawContent);
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_SKILL_CONTENT_BYTES) {
            throw new IllegalStateException("Copilot skill exceeds maximum content size: " + sourceLabel);
        }
        if (!content.startsWith("---\n")) {
            throw new IllegalStateException("Missing YAML frontmatter in Copilot skill: " + sourceLabel);
        }
        var endMarker = content.indexOf("\n---\n", 4);
        if (endMarker < 0) {
            throw new IllegalStateException("Unclosed YAML frontmatter in Copilot skill: " + sourceLabel);
        }

        try {
            var parsed = new Yaml().load(content.substring(4, endMarker));
            if (!(parsed instanceof Map<?, ?> metadata)) {
                throw new IllegalStateException("Copilot skill frontmatter must be a YAML map: " + sourceLabel);
            }
            var name = requiredString(metadata, "name", sourceLabel);
            var description = requiredString(metadata, "description", sourceLabel);
            var markdown = content.substring(endMarker + "\n---\n".length());
            if (markdown.isBlank()) {
                throw new IllegalStateException("Copilot skill Markdown body must not be blank: " + sourceLabel);
            }
            return new SkillDefinition(
                    name,
                    description,
                    Math.toIntExact(content.lines().count()),
                    markdown,
                    content
            );
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid YAML frontmatter in Copilot skill: " + sourceLabel, exception);
        }
    }

    private String requiredString(Map<?, ?> metadata, String key, String sourceLabel) {
        var value = metadata.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        throw new IllegalStateException(
                "Copilot skill frontmatter field '%s' must be a non-blank string: %s"
                        .formatted(key, sourceLabel)
        );
    }

    private List<CopilotRuntimeSkill> projectEffectiveSkills(
            List<SkillDefinition> effectiveDefinitions,
            List<SkillDefinition> packagedDefinitions
    ) {
        var packagedByName = definitionsByName(packagedDefinitions);
        return effectiveDefinitions.stream()
                .map(definition -> toRuntimeSkill(definition, packagedByName.get(definition.name())))
                .toList();
    }

    private CopilotRuntimeSkill toRuntimeSkill(SkillDefinition effective, SkillDefinition packaged) {
        var skillState = packaged != null && packaged.rawMarkdown().equals(effective.rawMarkdown())
                ? DEFAULT
                : CUSTOM;
        return new CopilotRuntimeSkill(
                effective.name(),
                effective.description(),
                effective.lineCount(),
                effective.markdown(),
                effective.rawMarkdown(),
                skillState,
                packaged != null
        );
    }

    private Map<String, SkillDefinition> definitionsByName(List<SkillDefinition> definitions) {
        var result = new LinkedHashMap<String, SkillDefinition>();
        definitions.forEach(definition -> result.put(definition.name(), definition));
        return Map.copyOf(result);
    }

    private CopilotRuntimeSkill requireEffectiveSkill(SkillCatalogState current, String skillName) {
        return current.effectiveSkills().stream()
                .filter(skill -> skill.name().equals(skillName))
                .findFirst()
                .orElseThrow(() -> new CopilotSkillCatalogException(
                        CopilotSkillCatalogException.Code.SKILL_NOT_FOUND,
                        "AI skill not found: " + skillName
                ));
    }

    private CopilotRuntimeSkill publishChangedSkill(SkillCatalogState current, SkillDefinition changedDefinition) {
        var packaged = definitionsByName(current.packagedSkills()).get(changedDefinition.name());
        var changedSkill = toRuntimeSkill(changedDefinition, packaged);
        var effectiveSkills = current.effectiveSkills().stream()
                .map(skill -> skill.name().equals(changedSkill.name()) ? changedSkill : skill)
                .toList();
        state = new SkillCatalogState(current.root(), current.packagedSkills(), effectiveSkills);
        return changedSkill;
    }

    private void writeEffectiveSkill(Path root, SkillDefinition candidate) {
        var skillDirectory = root.resolve(candidate.name()).normalize();
        var target = skillDirectory.resolve("SKILL.md").normalize();
        var temporary = skillDirectory.resolve(".SKILL.md." + UUID.randomUUID() + ".tmp").normalize();
        if (!skillDirectory.getParent().equals(root)
                || !target.getParent().equals(skillDirectory)
                || !temporary.getParent().equals(skillDirectory)) {
            throw new CopilotSkillCatalogException(
                    CopilotSkillCatalogException.Code.STORAGE_UNAVAILABLE,
                    "AI skill storage target is invalid."
            );
        }

        try {
            Files.writeString(
                    temporary,
                    candidate.rawMarkdown(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE
            );
            replaceFile(temporary, target);
            log.info("Copilot runtime skill updated skillName={}", candidate.name());
        } catch (IOException exception) {
            cleanupFile(temporary);
            throw new CopilotSkillCatalogException(
                    CopilotSkillCatalogException.Code.STORAGE_UNAVAILABLE,
                    "AI skill could not be written.",
                    exception
            );
        } finally {
            cleanupFile(temporary);
        }
    }

    protected void replaceFile(Path source, Path target) throws IOException {
        Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    private CopilotSkillCatalogException invalidContent(String message, Throwable cause) {
        return cause == null
                ? new CopilotSkillCatalogException(CopilotSkillCatalogException.Code.INVALID_CONTENT, message)
                : new CopilotSkillCatalogException(
                        CopilotSkillCatalogException.Code.INVALID_CONTENT,
                        message,
                        cause
                );
    }

    private String normalizeContent(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n');
    }

    private void copyDirectoryContents(Path sourceRoot, Path targetRoot) throws IOException {
        try (var paths = Files.walk(sourceRoot)) {
            for (var sourceFile : paths.filter(Files::isRegularFile).sorted().toList()) {
                var targetFile = targetRoot.resolve(sourceRoot.relativize(sourceFile)).normalize();
                if (!targetFile.startsWith(targetRoot)) {
                    throw new IllegalStateException("Copilot skill resource escaped target directory: " + sourceFile);
                }
                Files.createDirectories(targetFile.getParent());
                Files.copy(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private boolean containsRegularFile(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            return paths.anyMatch(Files::isRegularFile);
        }
    }

    private Path developmentResourceRoot(String resourceRoot) {
        var workingDirectory = properties.getWorkingDirectory();
        if (workingDirectory == null || workingDirectory.isBlank()) {
            workingDirectory = System.getProperty("user.dir");
        }
        return Path.of(workingDirectory)
                .resolve(Path.of("src", "main", "resources"))
                .resolve(resourceRoot.replace('/', java.io.File.separatorChar));
    }

    private Path resolveWithin(Path root, String relativePath) {
        var target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalStateException("Copilot skill resource escaped platform directory: " + relativePath);
        }
        return target;
    }

    private String normalizeResourceRoot(String resourceRoot) {
        if (resourceRoot == null || resourceRoot.isBlank()) {
            throw new IllegalStateException("analysis.ai.copilot.skill-resource-root must not be blank");
        }
        var normalized = resourceRoot.replace('\\', '/').trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isBlank() || normalized.contains("..")) {
            throw new IllegalStateException("Invalid Copilot skill resource root: " + resourceRoot);
        }
        return normalized;
    }

    private String relativePath(String resourceUrl, String resourceRoot) {
        var normalizedUrl = resourceUrl.replace('\\', '/');
        var rootMarker = resourceRoot + "/";
        var markerIndex = normalizedUrl.lastIndexOf(rootMarker);
        if (markerIndex < 0) {
            return null;
        }
        return URLDecoder.decode(
                normalizedUrl.substring(markerIndex + rootMarker.length()),
                StandardCharsets.UTF_8
        );
    }

    private void cleanupFile(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException exception) {
            log.warn("Failed to clean generated Copilot skill temporary file '{}'.", file, exception);
        }
    }

    private void cleanupDirectory(Path directory) {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        try (var paths = Files.walk(directory)) {
            for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            log.warn("Failed to clean generated Copilot skill directory '{}'.", directory, exception);
        }
    }

    private record SkillDefinition(
            String name,
            String description,
            int lineCount,
            String markdown,
            String rawMarkdown
    ) {
    }

    private record SkillCatalogState(
            Path root,
            List<SkillDefinition> packagedSkills,
            List<CopilotRuntimeSkill> effectiveSkills
    ) {
    }
}
