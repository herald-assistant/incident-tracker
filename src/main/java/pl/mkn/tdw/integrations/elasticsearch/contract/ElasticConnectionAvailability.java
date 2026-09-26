package pl.mkn.tdw.integrations.elasticsearch.contract;

import java.util.List;

public record ElasticConnectionAvailability(
        boolean configured,
        List<String> missingPropertyKeys,
        String disabledReason
) {

    public ElasticConnectionAvailability {
        missingPropertyKeys = List.copyOf(missingPropertyKeys);
    }
}
