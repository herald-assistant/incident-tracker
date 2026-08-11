package pl.mkn.tdw.integrations.operationalcontext;

import java.util.Map;

public record OperationalContextCatalogMutationCommand(
        String type,
        String id,
        Map<String, Object> payload
) {

    public OperationalContextCatalogMutationCommand {
        payload = OperationalContextImmutableValues.copyMap(payload);
    }
}
