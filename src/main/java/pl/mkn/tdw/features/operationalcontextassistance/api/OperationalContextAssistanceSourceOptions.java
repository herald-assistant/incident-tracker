package pl.mkn.tdw.features.operationalcontextassistance.api;

import java.util.List;

public record OperationalContextAssistanceSourceOptions(
        String configuredBaseUrl,
        String configuredGroup,
        List<Project> projects
) {
    public OperationalContextAssistanceSourceOptions {
        projects = List.copyOf(projects);
    }

    public record Project(String project, String projectPath) {
    }
}
