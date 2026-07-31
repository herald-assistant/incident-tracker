package pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api;

public record RuntimeConfigurationWorkbenchAiInputResponse(
        String previewId,
        boolean generated,
        int characterCount,
        String prompt
) {
}
