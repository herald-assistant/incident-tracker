package pl.mkn.tdw.integrations.operationalcontext;

import java.util.List;

public record OperationalContextDeleteImpact(
        String type,
        String id,
        String sourceFile,
        boolean allowed,
        List<InboundReference> inboundReferences
) {

    public OperationalContextDeleteImpact {
        inboundReferences = inboundReferences == null ? List.of() : List.copyOf(inboundReferences);
    }

    public record InboundReference(
            String sourceType,
            String sourceId,
            String relationType,
            String sourceFile,
            String fieldPath
    ) {
    }
}
