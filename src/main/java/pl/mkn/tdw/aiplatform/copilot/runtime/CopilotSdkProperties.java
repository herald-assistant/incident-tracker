package pl.mkn.tdw.aiplatform.copilot.runtime;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotAuthMode;

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
    private Duration modelOptionsTimeout = Duration.ofSeconds(20);
    private Duration modelOptionsCacheTtl = Duration.ofMinutes(10);
    /**
     * Legacy single-token property. Prefer analysis.ai.copilot.auth.local.github-token.
     */
    private String githubToken;
    private Auth auth = new Auth();
    private PermissionMode permissionMode = PermissionMode.APPROVE_ALL;
    private List<String> skillResourceRoots = List.of("copilot/skills");
    private String skillRuntimeDirectory = defaultSkillRuntimeDirectory();
    private List<String> skillDirectories = List.of();
    private List<String> disabledSkills = List.of();

    private static String defaultSkillRuntimeDirectory() {
        return System.getProperty("java.io.tmpdir") + java.io.File.separator
                + "incident-tracker" + java.io.File.separator
                + "copilot-runtime";
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
}
