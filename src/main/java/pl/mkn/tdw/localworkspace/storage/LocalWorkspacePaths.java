package pl.mkn.tdw.localworkspace.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;

import java.nio.file.Path;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class LocalWorkspacePaths {

    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9._-]+");
    private static final String DEFAULT_DIRECTORY = "tdw-data";

    private final LocalWorkspaceProperties properties;

    public Path root() {
        return Path.of(configuredDirectory()).toAbsolutePath().normalize();
    }

    public Path indexFile() {
        return root().resolve("index.json");
    }

    public Path tokensFile() {
        return root().resolve("tokens.json");
    }

    public Path runsDirectory() {
        return root().resolve("runs");
    }

    public Path runDirectory(String analysisId) {
        return runsDirectory().resolve(safeSegment(analysisId));
    }

    public Path runFile(String analysisId) {
        return runDirectory(analysisId).resolve("run.json");
    }

    public String runPath(String analysisId) {
        return root().relativize(runFile(analysisId)).toString().replace('\\', '/');
    }

    private String configuredDirectory() {
        var directory = properties.getDirectory();
        return directory == null || directory.isBlank() ? DEFAULT_DIRECTORY : directory.trim();
    }

    private String safeSegment(String value) {
        if (value == null || value.isBlank() || !SAFE_SEGMENT.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid local workspace path segment: " + value);
        }
        return value;
    }
}
