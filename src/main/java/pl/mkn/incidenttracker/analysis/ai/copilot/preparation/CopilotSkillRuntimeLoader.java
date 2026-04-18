package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CopilotSkillRuntimeLoader {

    private static final ResourcePatternResolver RESOURCE_RESOLVER = new PathMatchingResourcePatternResolver();

    private final CopilotSdkProperties properties;

    private volatile List<String> cachedSkillDirectories;

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
                log.warn("No Copilot skills were found under classpath root '{}'.", normalizedRoot);
                return null;
            }

            return targetRoot;
        }
        catch (IOException exception) {
            throw new IllegalStateException("Failed to prepare Copilot skill runtime directory for root: " + normalizedRoot, exception);
        }
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
