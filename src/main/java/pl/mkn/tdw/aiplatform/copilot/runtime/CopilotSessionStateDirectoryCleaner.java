package pl.mkn.tdw.aiplatform.copilot.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.regex.Pattern;

@Component
@Slf4j
@RequiredArgsConstructor
class CopilotSessionStateDirectoryCleaner {

    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9._-]+");

    private final CopilotSdkProperties properties;

    void deleteSessionStateDirectory(String sessionId) {
        sessionStateDirectory(sessionId).ifPresent(this::deleteDirectory);
    }

    private java.util.Optional<Path> sessionStateDirectory(String sessionId) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(properties.getCopilotHome())) {
            return java.util.Optional.empty();
        }
        var normalizedSessionId = sessionId.trim();
        if (!SAFE_SEGMENT.matcher(normalizedSessionId).matches()) {
            log.warn("Skipping Copilot session-state cleanup for unsafe sessionId={}", normalizedSessionId);
            return java.util.Optional.empty();
        }

        var sessionStateRoot = Path.of(properties.getCopilotHome().trim())
                .toAbsolutePath()
                .normalize()
                .resolve("session-state")
                .normalize();
        var directory = sessionStateRoot.resolve(normalizedSessionId).normalize();
        if (!directory.startsWith(sessionStateRoot)) {
            log.warn("Skipping Copilot session-state cleanup outside configured root path={}", directory);
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(directory);
    }

    private void deleteDirectory(Path directory) {
        if (!Files.exists(directory)) {
            return;
        }

        try (var files = Files.walk(directory)) {
            var pathsToDelete = files
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (var path : pathsToDelete) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            log.warn("Failed to delete Copilot session-state directory path={}", directory, exception);
            throw new IllegalStateException("Failed to delete Copilot session-state directory: " + directory, exception);
        }
    }
}
