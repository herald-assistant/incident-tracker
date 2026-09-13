package pl.mkn.tdw.features.operationalcontextassistance.api;

public record OperationalContextAssistanceSourceRevision(
        String project,
        String requestedRef,
        String commitId
) {
}
