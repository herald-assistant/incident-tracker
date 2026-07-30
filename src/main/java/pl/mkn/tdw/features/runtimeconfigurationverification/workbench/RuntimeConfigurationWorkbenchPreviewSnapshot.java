package pl.mkn.tdw.features.runtimeconfigurationverification.workbench;

import pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation.RuntimeConfigurationPromptPreparation;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api
        .RuntimeConfigurationWorkbenchAnonymizationPage;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api
        .RuntimeConfigurationWorkbenchMappingPage;

import java.util.List;

record RuntimeConfigurationWorkbenchPreviewSnapshot(
        RuntimeConfigurationVerificationMode mode,
        RuntimeConfigurationDeterministicContext deterministic,
        RuntimeConfigurationDeepContext deepContext,
        RuntimeConfigurationPromptPreparation preparation,
        List<RuntimeConfigurationWorkbenchMappingPage.Item> mappingItems,
        List<RuntimeConfigurationWorkbenchAnonymizationPage.Item> anonymizationItems
) {

    RuntimeConfigurationWorkbenchPreviewSnapshot {
        mappingItems = mappingItems != null ? List.copyOf(mappingItems) : List.of();
        anonymizationItems = anonymizationItems != null
                ? List.copyOf(anonymizationItems)
                : List.of();
    }
}
