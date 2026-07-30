package pl.mkn.tdw.features.runtimeconfigurationverification.ai.model;

import java.util.List;

public record RuntimeConfigurationAgreement(
        RuntimeConfigurationAgreementStatus status,
        String explanation,
        List<String> alignedFindingIds,
        List<String> disputedFindingIds
) {

    public RuntimeConfigurationAgreement {
        alignedFindingIds = alignedFindingIds != null ? List.copyOf(alignedFindingIds) : List.of();
        disputedFindingIds = disputedFindingIds != null ? List.copyOf(disputedFindingIds) : List.of();
    }
}
