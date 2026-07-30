package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.parse;

import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationFileRole;

import java.util.List;

public final class ParsedConfigurationFile {

    private final RuntimeConfigurationFileRole role;
    private final String path;
    private final List<ParsedConfigurationDocument> documents;
    private final List<ParsedConfigurationIssue> issues;

    public ParsedConfigurationFile(
            RuntimeConfigurationFileRole role,
            String path,
            List<ParsedConfigurationDocument> documents,
            List<ParsedConfigurationIssue> issues
    ) {
        this.role = role;
        this.path = path;
        this.documents = documents != null ? List.copyOf(documents) : List.of();
        this.issues = issues != null ? List.copyOf(issues) : List.of();
    }

    public RuntimeConfigurationFileRole role() {
        return role;
    }

    public String path() {
        return path;
    }

    public List<ParsedConfigurationDocument> documents() {
        return documents;
    }

    public List<ParsedConfigurationIssue> issues() {
        return issues;
    }

    public boolean parsed() {
        return issues.stream().noneMatch(issue -> issue.code().endsWith("_PARSE_ERROR"));
    }

    @Override
    public String toString() {
        return "ParsedConfigurationFile[role=" + role
                + ", path=" + path
                + ", documents=<redacted:" + documents.size() + ">"
                + ", issues=" + issues
                + "]";
    }
}
