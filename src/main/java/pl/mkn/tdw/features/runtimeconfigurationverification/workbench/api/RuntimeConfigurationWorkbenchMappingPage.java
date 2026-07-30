package pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api;

import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationChangeKind;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationSensitivity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationValueType;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationFileRole;

import java.util.List;

public record RuntimeConfigurationWorkbenchMappingPage(
        String previewId,
        int offset,
        int limit,
        int totalItems,
        int totalNodes,
        boolean changedOnly,
        List<Item> items
) {

    public RuntimeConfigurationWorkbenchMappingPage {
        items = items != null ? List.copyOf(items) : List.of();
    }

    public record Item(
            RuntimeConfigurationFileRole role,
            int documentIndex,
            int depth,
            String name,
            String path,
            RuntimeConfigurationValueType sourceType,
            RuntimeConfigurationValueType targetType,
            RuntimeConfigurationChangeKind relation,
            RuntimeConfigurationSensitivity sensitivity
    ) {
    }
}
