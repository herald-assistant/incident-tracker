package pl.mkn.tdw.integrations.operationalcontext.contract;

import java.util.Map;

public record OperationalContextEditableEntity(
        String type,
        String id,
        String sourceFile,
        Map<String, Object> payload
) {

    public OperationalContextEditableEntity {
        payload = OperationalContextImmutableValues.copyMap(payload);
    }
}
