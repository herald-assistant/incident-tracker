package pl.mkn.tdw.features.runtimeconfigurationverification.deep.model;

public record RuntimeConfigurationCodeGrounding(
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
        RuntimeConfigurationCodeUsageKind usageKind,
        RuntimeConfigurationGroundingConfidence confidence
) {
}
