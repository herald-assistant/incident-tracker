package pl.mkn.tdw.integrations.operationalcontext.contract;

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
