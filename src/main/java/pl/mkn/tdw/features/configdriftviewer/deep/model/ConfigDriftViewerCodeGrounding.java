package pl.mkn.tdw.features.configdriftviewer.deep.model;

public record ConfigDriftViewerCodeGrounding(
        String groundingId,
        String scopeId,
        String repositoryId,
        String projectPath,
        String usedRef,
        String filePath,
        Integer lineNumber,
        String symbol,
        String matchedPropertyPath,
        String differenceId,
        ConfigDriftViewerCodeUsageKind usageKind,
        ConfigDriftViewerGroundingConfidence confidence
) {
}
