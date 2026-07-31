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
            String originalName,
            String originalPath,
            String sanitizedName,
            String sanitizedPath,
            RuntimeConfigurationValueType sourceType,
            RuntimeConfigurationValueType targetType,
            RuntimeConfigurationChangeKind changeKind,
            RuntimeConfigurationSensitivity sensitivity,
            String sourceValueToken,
            String targetValueToken,
            List<String> differenceIds
    ) {

        public Item {
            differenceIds = differenceIds != null ? List.copyOf(differenceIds) : List.of();
        }
    }
}
