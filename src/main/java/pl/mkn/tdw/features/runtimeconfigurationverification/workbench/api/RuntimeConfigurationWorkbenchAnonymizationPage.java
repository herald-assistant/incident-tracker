package pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api;

import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationChangeKind;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationSensitivity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationValueType;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationFileRole;

import java.util.List;

public record RuntimeConfigurationWorkbenchAnonymizationPage(
        String previewId,
        int offset,
        int limit,
        int totalItems,
        List<Item> items
) {

    public RuntimeConfigurationWorkbenchAnonymizationPage {
        items = items != null ? List.copyOf(items) : List.of();
    }

    public record Item(
            RuntimeConfigurationFileRole role,
            int documentIndex,
            String path,
            RuntimeConfigurationChangeKind relation,
            RuntimeConfigurationSensitivity sensitivity,
            RuntimeConfigurationValueType sourceType,
            RuntimeConfigurationValueType targetType,
            ValueRepresentation sourceRepresentation,
            ValueRepresentation targetRepresentation,
            String sourceValueToken,
            String targetValueToken
    ) {
    }

    public enum ValueRepresentation {
        PSEUDONYMIZED,
        SUPPRESSED,
        STRUCTURE_ONLY,
        NOT_PRESENT
    }
}
