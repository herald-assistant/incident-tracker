package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

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
    private String model;
    private String reasoningEffort;
    private String clientName = "incidenttracker";
    private Duration sendAndWaitTimeout = Duration.ofMinutes(5);
    private Duration modelOptionsTimeout = Duration.ofSeconds(20);
    private Duration modelOptionsCacheTtl = Duration.ofMinutes(10);
    private String githubToken;
    private PermissionMode permissionMode = PermissionMode.APPROVE_ALL;
    private List<String> skillResourceRoots = List.of("copilot/skills");
    private String skillRuntimeDirectory = defaultSkillRuntimeDirectory();
    private List<String> skillDirectories = List.of();
    private List<String> disabledSkills = List.of();

    private static String defaultSkillRuntimeDirectory() {
        return System.getProperty("java.io.tmpdir") + java.io.File.separator
                + "incidenttracker" + java.io.File.separator
                + "copilot-skills";
    }
}
