package pl.mkn.tdw.features.configdriftviewer.deterministic.parse;

public record ParsedConfigurationIssue(
        String code,
        String path,
        Integer line
) {
}
