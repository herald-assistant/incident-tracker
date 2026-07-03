package pl.mkn.tdw.aiplatform.copilot.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CopilotSkillRuntimeLoader {

    private static final ResourcePatternResolver RESOURCE_RESOLVER = new PathMatchingResourcePatternResolver();

    private final CopilotSdkProperties properties;

    private volatile List<String> cachedSkillDirectories;

    @EventListener(ApplicationReadyEvent.class)
    public void logAvailableSkillsOnStartup() {
        var resolvedDirectories = resolveSkillDirectories();
        var availableSkillNames = availableSkillNames(resolvedDirectories);

        log.info(
                "Copilot runtime skills resolved runtimeDirectory={} resourceRoots={} externalDirectories={} resolvedRoots={} skillCount={} skills={}",
                properties.getSkillRuntimeDirectory(),
                safeList(properties.getSkillResourceRoots()),
                safeList(properties.getSkillDirectories()),
                resolvedDirectories,
                availableSkillNames.size(),
                availableSkillNames
        );
    }

    public List<String> resolveSkillDirectories() {
        var directories = cachedSkillDirectories;
        if (directories != null) {
            return directories;
        }

        synchronized (this) {
            if (cachedSkillDirectories == null) {
                cachedSkillDirectories = List.copyOf(loadSkillDirectories());
            }

            return cachedSkillDirectories;
        }
    }

    public List<String> resolveSelectedSkillRootDirectories(List<String> skillNames) {
        var normalizedSkillNames = normalizeSkillNames(skillNames);
        if (normalizedSkillNames.isEmpty()) {
            return List.of();
        }

        var resolvedRoots = resolveSkillDirectories();
        var selectedSkillDirectories = normalizedSkillNames.stream()
                .map(skillName -> resolveRequiredSkillDirectory(skillName, resolvedRoots))
                .toList();
        var selectedRoot = selectedSkillRoot(normalizedSkillNames, selectedSkillDirectories);

        synchronized (this) {
            if (!containsAllSelectedSkills(selectedRoot, normalizedSkillNames)) {
                copySelectedSkills(selectedSkillDirectories, selectedRoot);
            }
        }

        log.info(
                "Copilot selected runtime skills resolved selectedRoot={} skillCount={} skills={}",
                selectedRoot,
                normalizedSkillNames.size(),
                normalizedSkillNames
        );

        return List.of(selectedRoot.toString());
    }

    List<String> availableSkillNames() {
        return availableSkillNames(resolveSkillDirectories());
    }

    private List<String> loadSkillDirectories() {
        var resolvedDirectories = new ArrayList<String>();

        for (var resourceRoot : safeList(properties.getSkillResourceRoots())) {
            var extractedDirectory = extractSkillResourceRoot(resourceRoot);
            if (extractedDirectory != null) {
                resolvedDirectories.add(extractedDirectory.toString());
            }
        }

        resolvedDirectories.addAll(safeList(properties.getSkillDirectories()));
        return resolvedDirectories;
    }

    private List<String> availableSkillNames(List<String> resolvedRoots) {
        var skillNames = new LinkedHashSet<String>();

        for (var rootValue : safeList(resolvedRoots)) {
            if (rootValue == null || rootValue.isBlank()) {
                continue;
            }

            var root = Path.of(rootValue);
            if (hasSkillDefinition(root) && root.getFileName() != null) {
                skillNames.add(root.getFileName().toString());
            }

            if (!Files.isDirectory(root)) {
                continue;
            }

            try (var paths = Files.list(root)) {
                paths
                        .filter(this::hasSkillDefinition)
                        .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                        .map(path -> path.getFileName().toString())
                        .forEach(skillNames::add);
            }
            catch (IOException exception) {
                throw new IllegalStateException("Failed to list Copilot runtime skills under root: " + root, exception);
            }
        }

        return List.copyOf(skillNames);
    }

    private Path resolveRequiredSkillDirectory(String skillName, List<String> resolvedRoots) {
        for (var rootValue : safeList(resolvedRoots)) {
            if (rootValue == null || rootValue.isBlank()) {
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

    private Path selectedSkillRoot(List<String> skillNames, List<Path> selectedSkillDirectories) {
        return Path.of(properties.getSkillRuntimeDirectory())
                .resolve("selected-skills-" + selectedSkillFingerprint(skillNames, selectedSkillDirectories));
    }

    private String selectedSkillFingerprint(List<String> skillNames, List<Path> selectedSkillDirectories) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (var skillName : skillNames) {
                updateDigest(digest, "skill:" + skillName);
            }
            for (var skillDirectory : selectedSkillDirectories) {
                updateDigest(digest, "directory:" + skillDirectory.toAbsolutePath().normalize());
                try (var paths = Files.walk(skillDirectory)) {
                    var files = paths
                            .filter(Files::isRegularFile)
                            .sorted(Comparator.comparing(path -> skillDirectory.relativize(path).toString()))
                            .toList();
                    for (var file : files) {
                        updateDigest(digest, skillDirectory.relativize(file).toString());
                        updateDigest(digest, Long.toString(Files.size(file)));
                        updateDigest(digest, Long.toString(Files.getLastModifiedTime(file).toMillis()));
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest()).substring(0, 16);
        }
        catch (IOException exception) {
            throw new IllegalStateException("Failed to fingerprint selected Copilot skill directories.", exception);
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is unavailable.", exception);
        }
    }

    private void updateDigest(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private boolean containsAllSelectedSkills(Path selectedRoot, List<String> skillNames) {
        if (!Files.isDirectory(selectedRoot)) {
            return false;
        }
        for (var skillName : skillNames) {
            if (!hasSkillDefinition(selectedRoot.resolve(skillName))) {
                return false;
            }
        }
        return true;
    }

    private void copySelectedSkills(List<Path> selectedSkillDirectories, Path selectedRoot) {
        try {
            Files.createDirectories(selectedRoot);
            for (var skillDirectory : selectedSkillDirectories) {
                copyDirectory(skillDirectory, selectedRoot.resolve(skillDirectory.getFileName().toString()));
            }
        }
        catch (IOException exception) {
            throw new IllegalStateException("Failed to prepare selected Copilot skill root: " + selectedRoot, exception);
        }
    }

    private void copyDirectory(Path sourceDirectory, Path targetDirectory) throws IOException {
        try (var paths = Files.walk(sourceDirectory)) {
            var files = paths
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(path -> sourceDirectory.relativize(path).toString()))
                    .toList();
            for (var sourceFile : files) {
                var targetFile = targetDirectory.resolve(sourceDirectory.relativize(sourceFile).toString());
                Files.createDirectories(targetFile.getParent());
                Files.copy(sourceFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private Path extractSkillResourceRoot(String resourceRoot) {
        var normalizedRoot = normalizeRoot(resourceRoot);
        var targetRoot = Path.of(properties.getSkillRuntimeDirectory(), normalizedRoot.replace('/', '_'));

        try {
            clearDirectory(targetRoot);
            var resources = RESOURCE_RESOLVER.getResources(
                    ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX + normalizedRoot + "/**/*"
            );

            var copiedAnyFile = false;

            for (var resource : resources) {
                if (!resource.exists() || !resource.isReadable()) {
                    continue;
                }

                var relativePath = relativePath(resource.getURL().toString(), normalizedRoot);
                if (relativePath == null || relativePath.isBlank()) {
                    continue;
                }

                var targetFile = targetRoot.resolve(relativePath);
                Files.createDirectories(targetFile.getParent());

                try (var inputStream = resource.getInputStream()) {
                    Files.copy(inputStream, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    copiedAnyFile = true;
                }
            }

            if (!copiedAnyFile) {
                var developmentResourceRoot = resolveDevelopmentResourceRoot(normalizedRoot);
                if (developmentResourceRoot != null) {
                    return developmentResourceRoot;
                }
            }

            if (!copiedAnyFile) {
                log.warn("No Copilot skills were found under classpath root '{}'.", normalizedRoot);
                return null;
            }

            return targetRoot;
        }
        catch (IOException exception) {
            throw new IllegalStateException("Failed to prepare Copilot skill runtime directory for root: " + normalizedRoot, exception);
        }
    }

    private Path resolveDevelopmentResourceRoot(String normalizedRoot) throws IOException {
        var sourceRoot = developmentResourceRoot(normalizedRoot);
        if (!Files.isDirectory(sourceRoot) || !containsRegularFile(sourceRoot)) {
            return null;
        }

        log.info("Loaded Copilot skills from development resource root '{}'.", sourceRoot);
        return sourceRoot;
    }

    private Path developmentResourceRoot(String normalizedRoot) {
        var workingDirectory = properties.getWorkingDirectory();
        if (workingDirectory == null || workingDirectory.isBlank()) {
            workingDirectory = System.getProperty("user.dir");
        }

        return Path.of(workingDirectory)
                .resolve(Path.of("src", "main", "resources"))
                .resolve(normalizedRoot.replace('/', java.io.File.separatorChar));
    }

    private boolean containsRegularFile(Path directory) throws IOException {
        try (var paths = Files.walk(directory)) {
            return paths.anyMatch(Files::isRegularFile);
        }
    }

    private boolean hasSkillDefinition(Path directory) {
        return Files.isDirectory(directory) && Files.isRegularFile(directory.resolve("SKILL.md"));
    }

    private void clearDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        try (var paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        }
                        catch (IOException exception) {
                            throw new IllegalStateException("Failed to clean Copilot skill runtime directory: " + directory, exception);
                        }
                    });
        }
    }

    private String normalizeRoot(String resourceRoot) {
        var normalized = resourceRoot.replace('\\', '/').trim();

        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }

        return normalized;
    }

    private List<String> normalizeSkillNames(List<String> skillNames) {
        if (skillNames == null || skillNames.isEmpty()) {
            return List.of();
        }

        var normalized = new LinkedHashSet<String>();
        for (var skillName : skillNames) {
            if (skillName != null && !skillName.isBlank()) {
                normalized.add(skillName.trim());
            }
        }
        return List.copyOf(normalized);
    }

    private String relativePath(String resourceUrl, String normalizedRoot) {
        var normalizedUrl = resourceUrl.replace('\\', '/');
        var rootMarker = normalizedRoot + "/";
        var markerIndex = normalizedUrl.indexOf(rootMarker);

        if (markerIndex < 0) {
            return null;
        }

        return URLDecoder.decode(
                normalizedUrl.substring(markerIndex + rootMarker.length()),
                StandardCharsets.UTF_8
        );
    }

    private List<String> safeList(List<String> values) {
        return values != null ? values : List.of();
    }

}
