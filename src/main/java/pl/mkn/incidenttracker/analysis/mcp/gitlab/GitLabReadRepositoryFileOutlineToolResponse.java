package pl.mkn.incidenttracker.analysis.mcp.gitlab;

import java.util.List;

public record GitLabReadRepositoryFileOutlineToolResponse(
        String group,
        String projectName,
        String branch,
        String filePath,
        String packageName,
        List<String> imports,
        List<String> classes,
        List<String> annotations,
        List<String> methodSignatures,
        String inferredRole,
        boolean truncated
) {
}
