package pl.mkn.tdw.aiplatform.copilot.runtime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
@Slf4j
public class CopilotCliExecutableResolver {

    private static final String DEFAULT_COMMAND = "copilot";
    private final Set<String> loggedExecutablePaths = ConcurrentHashMap.newKeySet();

    public String resolve(String configuredCliPath, String workingDirectory) {
        return resolve(
                configuredCliPath,
                workingDirectory,
                System.getProperty("os.name", ""),
                System.getenv("PATH")
        );
    }

    String resolve(
            String configuredCliPath,
            String workingDirectory,
            String operatingSystem,
            String pathEnvironment
    ) {
        var command = StringUtils.hasText(configuredCliPath)
                ? withoutWrappingQuotes(configuredCliPath.trim())
                : DEFAULT_COMMAND;

        if (!isWindows(operatingSystem)) {
            return command;
        }

        var configuredPath = Path.of(command);
        if (configuredPath.isAbsolute()) {
            return validatedWindowsExecutable(configuredPath, configuredCliPath);
        }

        var executableName = command.toLowerCase(Locale.ROOT).endsWith(".exe")
                ? command
                : command + ".exe";

        for (var directory : searchDirectories(workingDirectory, pathEnvironment)) {
            var candidate = directory.resolve(executableName).toAbsolutePath().normalize();
            if (Files.isRegularFile(candidate)) {
                return resolvedPath(candidate);
            }
        }

        throw new IllegalStateException(
                "On Windows analysis.ai.copilot.cli-path must resolve to an absolute .exe file. "
                        + "Command '" + command + "' was not found as " + executableName
                        + " in the working directory or PATH."
        );
    }

    private String validatedWindowsExecutable(Path path, String configuredCliPath) {
        var normalized = path.toAbsolutePath().normalize();
        if (!normalized.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".exe")) {
            throw new IllegalStateException(
                    "On Windows analysis.ai.copilot.cli-path must point directly to copilot.exe, not a shell wrapper: "
                            + configuredCliPath
            );
        }
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalStateException(
                    "Configured Copilot CLI executable does not exist or is not a file: " + normalized
            );
        }
        return resolvedPath(normalized);
    }

    private LinkedHashSet<Path> searchDirectories(String workingDirectory, String pathEnvironment) {
        var directories = new LinkedHashSet<Path>();
        if (StringUtils.hasText(workingDirectory)) {
            directories.add(Path.of(workingDirectory.trim()).toAbsolutePath().normalize());
        }

        if (StringUtils.hasText(pathEnvironment)) {
            for (var entry : pathEnvironment.split(Pattern.quote(File.pathSeparator))) {
                var normalizedEntry = entry.trim();
                if (normalizedEntry.length() >= 2
                        && normalizedEntry.startsWith("\"")
                        && normalizedEntry.endsWith("\"")) {
                    normalizedEntry = normalizedEntry.substring(1, normalizedEntry.length() - 1);
                }
                if (StringUtils.hasText(normalizedEntry)) {
                    try {
                        directories.add(Path.of(normalizedEntry).toAbsolutePath().normalize());
                    } catch (InvalidPathException exception) {
                        log.debug("Ignoring invalid PATH entry while resolving Copilot CLI: {}", normalizedEntry);
                    }
                }
            }
        }
        return directories;
    }

    private boolean isWindows(String operatingSystem) {
        return operatingSystem != null
                && operatingSystem.trim().toLowerCase(Locale.ROOT).startsWith("windows");
    }

    private String resolvedPath(Path executable) {
        var value = executable.toString();
        if (loggedExecutablePaths.add(value)) {
            log.info("Resolved Copilot CLI executable path={}", executable);
        }
        return value;
    }

    private String withoutWrappingQuotes(String value) {
        return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
                ? value.substring(1, value.length() - 1)
                : value;
    }
}
