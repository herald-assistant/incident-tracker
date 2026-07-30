package pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api;

public record RuntimeConfigurationWorkbenchAiInputResponse(
        String previewId,
        int characterCount,
        String prompt
) {
}
