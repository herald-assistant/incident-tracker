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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CopilotSkillRuntimeLoader {

    private static final ResourcePatternResolver RESOURCE_RESOLVER = new PathMatchingResourcePatternResolver();

    private final CopilotSdkProperties properties;

    private volatile List<String> platformSkillDirectories;
    private volatile List<String> platformSkillNames;
    private volatile List<CopilotRuntimeSkill> platformSkills;

    @PostConstruct
    void initializePlatformSkillCatalog() {
        ensureInitialized();
    }

    public List<String> platformSkillDirectories() {
        ensureInitialized();
        return platformSkillDirectories;
    }

    public List<String> availableSkillNames() {
        ensureInitialized();
        return platformSkillNames;
    }

    public List<CopilotRuntimeSkill> availableSkills() {
        ensureInitialized();
        return platformSkills;
    }

    private void ensureInitialized() {
        if (platformSkillDirectories != null) {
            return;
        }

        synchronized (this) {
            if (platformSkillDirectories != null) {
                return;
            }

            var catalog = materializePlatformSkillCatalog();
            platformSkills = catalog.skills();
            platformSkillNames = catalog.skills().stream().map(CopilotRuntimeSkill::name).toList();
            platformSkillDirectories = List.of(catalog.root().toString());
            log.info(
                    "Copilot platform skill catalog initialized directory={} resourceRoot={} skillCount={} skills={}",
                    catalog.root(),
                    properties.getSkillResourceRoot(),
                    catalog.skills().size(),
                    platformSkillNames
            );
        }
    }

    private SkillCatalog materializePlatformSkillCatalog() {
        var targetRoot = properties.resolvedSkillDirectory();
        var parent = targetRoot.getParent();
        var stagingRoot = parent.resolve(".skills-staging-" + UUID.randomUUID()).normalize();

        try {
            Files.createDirectories(parent);
            Files.createDirectory(stagingRoot);
            copyPackagedSkills(stagingRoot);
            var skills = validateSkillCatalog(stagingRoot);
            replaceDirectory(stagingRoot, targetRoot);
            return new SkillCatalog(targetRoot, skills);
        } catch (Exception exception) {
            cleanupDirectory(stagingRoot);
            if (exception instanceof IllegalStateException illegalStateException) {
                throw illegalStateException;
            }
            throw new IllegalStateException(
                    "Failed to initialize Copilot platform skill catalog: " + targetRoot,
                    exception
            );
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

    private List<CopilotRuntimeSkill> validateSkillCatalog(Path root) throws IOException {
        var skillNames = new LinkedHashSet<String>();
        var skills = new ArrayList<CopilotRuntimeSkill>();
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

            var parsedSkill = parseSkill(skillFile);
            var metadata = parsedSkill.metadata();
            var expectedName = skillDirectory.getFileName().toString();
            var name = requiredString(metadata, "name", skillFile);
            var description = requiredString(metadata, "description", skillFile);
            if (!expectedName.equals(name)) {
                throw new IllegalStateException(
                        "Copilot skill frontmatter name '%s' must match directory '%s'"
                                .formatted(name, expectedName)
                );
            }
            if (!skillNames.add(name)) {
                throw new IllegalStateException("Duplicate Copilot skill name: " + name);
            }
            skills.add(new CopilotRuntimeSkill(
                    name,
                    description,
                    parsedSkill.lineCount(),
                    parsedSkill.markdown(),
                    parsedSkill.rawMarkdown()
            ));
        }

        return List.copyOf(skills);
    }

    private ParsedSkill parseSkill(Path skillFile) throws IOException {
        var content = Files.readString(skillFile, StandardCharsets.UTF_8).replace("\r\n", "\n");
        if (!content.startsWith("---\n")) {
            throw new IllegalStateException("Missing YAML frontmatter in Copilot skill: " + skillFile);
        }
        var endMarker = content.indexOf("\n---", 4);
        if (endMarker < 0) {
            throw new IllegalStateException("Unclosed YAML frontmatter in Copilot skill: " + skillFile);
        }

        try {
            var parsed = new Yaml().load(content.substring(4, endMarker));
            if (parsed instanceof Map<?, ?> metadata) {
                var bodyStart = endMarker + "\n---".length();
                if (bodyStart < content.length() && content.charAt(bodyStart) == '\n') {
                    bodyStart++;
                }
                return new ParsedSkill(
                        metadata,
                        content.substring(bodyStart),
                        content,
                        Math.toIntExact(content.lines().count())
                );
            }
            throw new IllegalStateException("Copilot skill frontmatter must be a YAML map: " + skillFile);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid YAML frontmatter in Copilot skill: " + skillFile, exception);
        }
    }

    private String requiredString(Map<?, ?> metadata, String key, Path skillFile) {
        var value = metadata.get(key);
        if (value instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        throw new IllegalStateException(
                "Copilot skill frontmatter field '%s' must be a non-blank string: %s"
                        .formatted(key, skillFile)
        );
    }

    private void replaceDirectory(Path stagingRoot, Path targetRoot) throws IOException {
        var backupRoot = targetRoot.getParent().resolve(".skills-backup-" + UUID.randomUUID()).normalize();
        var previousCatalogMoved = false;
        try {
            if (Files.exists(targetRoot)) {
                moveDirectory(targetRoot, backupRoot);
                previousCatalogMoved = true;
            }
            moveDirectory(stagingRoot, targetRoot);
            cleanupDirectory(backupRoot);
        } catch (IOException exception) {
            if (previousCatalogMoved && !Files.exists(targetRoot) && Files.exists(backupRoot)) {
                try {
                    moveDirectory(backupRoot, targetRoot);
                } catch (IOException restoreException) {
                    exception.addSuppressed(restoreException);
                }
            }
            throw exception;
        } finally {
            cleanupDirectory(stagingRoot);
            if (Files.exists(targetRoot)) {
                cleanupDirectory(backupRoot);
            }
        }
    }

    private void moveDirectory(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target);
        }
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

    private record ParsedSkill(
            Map<?, ?> metadata,
            String markdown,
            String rawMarkdown,
            int lineCount
    ) {
    }

    private record SkillCatalog(Path root, List<CopilotRuntimeSkill> skills) {
    }
}
