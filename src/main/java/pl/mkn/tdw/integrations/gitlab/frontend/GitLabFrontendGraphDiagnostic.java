package pl.mkn.tdw.integrations.gitlab.frontend;

import org.springframework.util.StringUtils;

import java.util.Objects;

public record GitLabFrontendGraphDiagnostic(
        GitLabFrontendDiagnosticSeverity severity,
        GitLabFrontendGraphDiagnosticCode code,
        String message,
        String nodeId,
        String edgeId,
        GitLabFrontendSourceReference source
) {

    public GitLabFrontendGraphDiagnostic {
        severity = Objects.requireNonNull(severity, "severity must not be null");
        code = Objects.requireNonNull(code, "code must not be null");
        if (!StringUtils.hasText(message)) {
            throw new IllegalArgumentException("message must not be blank");
        }
        message = message.trim();
        nodeId = normalize(nodeId);
        edgeId = normalize(edgeId);
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
