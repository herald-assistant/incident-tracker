package pl.mkn.tdw.aiplatform.copilot.runtime;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAuthMode;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

@Getter
@Setter
@Slf4j
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
    private String skillResourceProjectDirectory = System.getProperty("user.dir");
    private List<String> disabledSkills = List.of();
    private ContextTierPolicy contextTier = new ContextTierPolicy();
    private InfiniteSessions infiniteSessions = new InfiniteSessions();
    private Telemetry telemetry = new Telemetry();

    @PostConstruct
    public void initializeRuntimeConfiguration() {
        validateContextManagementConfiguration();
        workingDirectory = prepareWorkingDirectory().toString();
        log.info("Copilot CLI working directory prepared path={}", workingDirectory);
        if (telemetry == null || !telemetry.isEnabled()) {
            log.info("Copilot OTLP export configured enabled=false");
            return;
        }

        log.info("Copilot OTLP export configured enabled=true endpoint={} sourceName={} captureContent={}",
                telemetry.validatedOtlpEndpoint(),
                telemetry.validatedSourceName(),
                telemetry.isCaptureContent());
    }

    Path prepareWorkingDirectory() {
        if (workingDirectory == null || workingDirectory.isBlank()) {
            throw new IllegalStateException("analysis.ai.copilot.working-directory must not be blank");
        }

        final Path directory;
        try {
            directory = Path.of(workingDirectory.trim()).toAbsolutePath().normalize();
        } catch (InvalidPathException exception) {
            throw new IllegalStateException("Invalid analysis.ai.copilot.working-directory: " + workingDirectory,
                    exception);
        }
        if (directory.getParent() == null) {
            throw new IllegalStateException("analysis.ai.copilot.working-directory cannot be a filesystem root");
        }
        try {
            Files.createDirectories(directory);
        } catch (IOException | SecurityException exception) {
            throw new IllegalStateException(
                    "Cannot create analysis.ai.copilot.working-directory: " + directory,
                    exception
            );
        }
        return directory;
    }

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

    public void validateContextManagementConfiguration() {
        if (contextTier == null) {
            throw new IllegalStateException("analysis.ai.copilot.context-tier must be configured");
        }
        if (contextTier.getInitialPromptThreshold() <= 0D || contextTier.getInitialPromptThreshold() > 1D) {
            throw new IllegalStateException(
                    "analysis.ai.copilot.context-tier.initial-prompt-threshold must be in (0, 1]"
            );
        }
        if (contextTier.getRuntimeUsageThreshold() <= 0D || contextTier.getRuntimeUsageThreshold() >= 1D) {
            throw new IllegalStateException(
                    "analysis.ai.copilot.context-tier.runtime-usage-threshold must be in (0, 1)"
            );
        }
        if (contextTier.getEstimatedCharactersPerToken() <= 0D) {
            throw new IllegalStateException(
                    "analysis.ai.copilot.context-tier.estimated-characters-per-token must be positive"
            );
        }
        if (contextTier.getReservedTokens() < 0) {
            throw new IllegalStateException(
                    "analysis.ai.copilot.context-tier.reserved-tokens must not be negative"
            );
        }
        if (contextTier.getVerificationTimeout() == null
                || contextTier.getVerificationTimeout().isZero()
                || contextTier.getVerificationTimeout().isNegative()) {
            throw new IllegalStateException(
                    "analysis.ai.copilot.context-tier.verification-timeout must be positive"
            );
        }
        if (infiniteSessions == null) {
            throw new IllegalStateException("analysis.ai.copilot.infinite-sessions must be configured");
        }
        if (infiniteSessions.getBackgroundCompactionThreshold() <= 0D
                || infiniteSessions.getBackgroundCompactionThreshold() >= 1D) {
            throw new IllegalStateException(
                    "analysis.ai.copilot.infinite-sessions.background-compaction-threshold must be in (0, 1)"
            );
        }
        if (infiniteSessions.getBufferExhaustionThreshold() <= 0D
                || infiniteSessions.getBufferExhaustionThreshold() >= 1D) {
            throw new IllegalStateException(
                    "analysis.ai.copilot.infinite-sessions.buffer-exhaustion-threshold must be in (0, 1)"
            );
        }
        if (contextTier.isEnabled()
                && infiniteSessions.isEnabled()
                && contextTier.getRuntimeUsageThreshold() >= infiniteSessions.getBackgroundCompactionThreshold()) {
            throw new IllegalStateException(
                    "analysis.ai.copilot.context-tier.runtime-usage-threshold must be lower than "
                            + "analysis.ai.copilot.infinite-sessions.background-compaction-threshold"
            );
        }
        if (infiniteSessions.getBackgroundCompactionThreshold()
                >= infiniteSessions.getBufferExhaustionThreshold()) {
            throw new IllegalStateException(
                    "analysis.ai.copilot.infinite-sessions.background-compaction-threshold must be lower than "
                            + "analysis.ai.copilot.infinite-sessions.buffer-exhaustion-threshold"
            );
        }
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
        private double runtimeUsageThreshold = 0.90D;
        private double estimatedCharactersPerToken = 3.5D;
        private int reservedTokens = 16_000;
        private Duration verificationTimeout = Duration.ofSeconds(20);
    }

    @Getter
    @Setter
    public static class InfiniteSessions {

        private boolean enabled = true;
        private double backgroundCompactionThreshold = 0.95D;
        private double bufferExhaustionThreshold = 0.98D;
    }

    @Getter
    @Setter
    public static class Telemetry {

        private boolean enabled;
        private String otlpEndpoint;
        private boolean captureContent;
        private String sourceName = "team-delivery-workspace";

        public String validatedOtlpEndpoint() {
            if (otlpEndpoint == null || otlpEndpoint.isBlank()) {
                throw new IllegalStateException(
                        "analysis.ai.copilot.telemetry.otlp-endpoint is required when telemetry is enabled."
                );
            }

            var endpoint = otlpEndpoint.trim();
            try {
                var uri = URI.create(endpoint);
                if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                        || uri.getHost() == null
                        || uri.getRawUserInfo() != null
                        || uri.getRawQuery() != null
                        || uri.getRawFragment() != null
                        || uri.getPath().matches("/v1/(traces|metrics|logs)/?")) {
                    throw new IllegalArgumentException("Unsupported OTLP endpoint URI");
                }
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException(
                        "analysis.ai.copilot.telemetry.otlp-endpoint must be an HTTP(S) base URL without credentials, query or fragment.",
                        exception
                );
            }
            return endpoint;
        }

        public String validatedSourceName() {
            if (sourceName == null || sourceName.isBlank()) {
                throw new IllegalStateException(
                        "analysis.ai.copilot.telemetry.source-name must not be blank when telemetry is enabled."
                );
            }
            return sourceName.trim();
        }
    }
}
