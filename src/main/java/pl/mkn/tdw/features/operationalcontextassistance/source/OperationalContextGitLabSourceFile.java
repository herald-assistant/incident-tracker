package pl.mkn.tdw.features.operationalcontextassistance.source;

public record OperationalContextGitLabSourceFile(
        String path,
        String content,
        String sourceRef
) {
}
