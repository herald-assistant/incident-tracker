package pl.mkn.tdw.features.configdriftviewer.ai.model;

import java.util.List;

public record ConfigDriftViewerAgreement(
        ConfigDriftViewerAgreementStatus status,
        String explanation,
        List<String> alignedFindingIds,
        List<String> disputedFindingIds
) {

    public ConfigDriftViewerAgreement {
        alignedFindingIds = alignedFindingIds != null ? List.copyOf(alignedFindingIds) : List.of();
        disputedFindingIds = disputedFindingIds != null ? List.copyOf(disputedFindingIds) : List.of();
    }
}
