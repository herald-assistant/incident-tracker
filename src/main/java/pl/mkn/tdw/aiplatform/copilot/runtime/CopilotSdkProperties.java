package pl.mkn.tdw.aiplatform.copilot.runtime;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAuthMode;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "analysis.ai.copilot")
public class CopilotSdkProperties {

    public enum PermissionMode {
        APPROVE_ALL,
        DENY_ALL
    }

    private String cliPath = "copilot";
    private String workingDirectory;
    private String copilotHome = "tdw-data/copilot";
    private String model;
    private String reasoningEffort;
    private String clientName = "incidenttracker";
    private Duration sendAndWaitTimeout = Duration.ofMinutes(5);
    private Duration clientStopTimeout = Duration.ofSeconds(20);
    private Duration sessionDeleteTimeout = Duration.ofSeconds(20);
    private Duration modelOptionsTimeout = Duration.ofSeconds(20);
    private Duration modelOptionsCacheTtl = Duration.ofMinutes(10);
    /**
     * Legacy single-token property. Prefer analysis.ai.copilot.auth.local.github-token.
     */
    private String githubToken;
    private Auth auth = new Auth();
    private PermissionMode permissionMode = PermissionMode.APPROVE_ALL;
    private String skillResourceRoot = "copilot/skills";
    private List<String> disabledSkills = List.of();
    private ContextTierPolicy contextTier = new ContextTierPolicy();

    public Path resolvedCopilotHome() {
        if (copilotHome == null || copilotHome.isBlank()) {
            throw new IllegalStateException("analysis.ai.copilot.copilot-home must not be blank");
        }

        var path = Path.of(copilotHome.trim()).toAbsolutePath().normalize();
        if (path.getParent() == null) {
            throw new IllegalStateException("analysis.ai.copilot.copilot-home cannot be a filesystem root");
        }
        return path;
    }

    public Path resolvedSkillDirectory() {
        return resolvedCopilotHome().resolve("skills");
    }

    @Getter
    @Setter
    public static class Auth {

        private CopilotAuthMode mode = CopilotAuthMode.LOCAL_TOKEN;
        private Local local = new Local();
    }

    @Getter
    @Setter
    public static class Local {

        private String githubToken;
        private String displayName = "Local developer token";
    }

    @Getter
    @Setter
    public static class ContextTierPolicy {

        private boolean enabled = true;
        private double initialPromptThreshold = 0.70D;
        private double runtimeUsageThreshold = 0.70D;
        private double estimatedCharactersPerToken = 3.5D;
        private int reservedTokens = 16_000;
        private Duration verificationTimeout = Duration.ofSeconds(20);
    }
}
