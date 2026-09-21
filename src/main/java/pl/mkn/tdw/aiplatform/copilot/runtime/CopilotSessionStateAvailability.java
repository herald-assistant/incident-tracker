package pl.mkn.tdw.aiplatform.copilot.runtime;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class CopilotSessionStateAvailability {
    private static final Pattern SAFE_SEGMENT = Pattern.compile("[A-Za-z0-9._-]+");
    private final CopilotSdkProperties properties;

    public boolean exists(String sessionId) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(properties.getCopilotHome())) return false;
        var normalized = sessionId.trim();
        if (!SAFE_SEGMENT.matcher(normalized).matches()) return false;
        var root = Path.of(properties.getCopilotHome().trim()).toAbsolutePath().normalize().resolve("session-state").normalize();
        var directory = root.resolve(normalized).normalize();
        return directory.startsWith(root) && Files.isDirectory(directory);
    }
}
