package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.parse;

public record ParsedConfigurationIssue(
        String code,
        String path,
        Integer line
) {
}
