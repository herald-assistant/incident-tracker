package pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api;

import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContext;

public record RuntimeConfigurationWorkbenchDeepResponse(
        String previewId,
        boolean requested,
        RuntimeConfigurationDeepContext context
) {
}
