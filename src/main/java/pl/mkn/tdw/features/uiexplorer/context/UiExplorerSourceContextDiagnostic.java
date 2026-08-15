package pl.mkn.tdw.features.uiexplorer.context;

public record UiExplorerSourceContextDiagnostic(
        String severity,
        String code,
        String message,
        String sourcePath
) {
}
