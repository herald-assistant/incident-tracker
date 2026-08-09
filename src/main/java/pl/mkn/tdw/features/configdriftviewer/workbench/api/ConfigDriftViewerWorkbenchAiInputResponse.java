package pl.mkn.tdw.features.configdriftviewer.workbench.api;

public record ConfigDriftViewerWorkbenchAiInputResponse(
        String previewId,
        boolean generated,
        int characterCount,
        String prompt
) {
}
