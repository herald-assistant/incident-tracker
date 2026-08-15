package pl.mkn.tdw.integrations.gitlab.frontend;

public record GitLabFrontendDiagnostic(
        GitLabFrontendDiagnosticSeverity severity,
        String code,
        String message,
        String sourcePath
) {
}

