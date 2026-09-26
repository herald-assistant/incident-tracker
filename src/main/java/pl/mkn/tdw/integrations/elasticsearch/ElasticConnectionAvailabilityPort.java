package pl.mkn.tdw.integrations.elasticsearch;

import pl.mkn.tdw.integrations.elasticsearch.contract.ElasticConnectionAvailability;

public interface ElasticConnectionAvailabilityPort {

    ElasticConnectionAvailability currentAvailability();
}
