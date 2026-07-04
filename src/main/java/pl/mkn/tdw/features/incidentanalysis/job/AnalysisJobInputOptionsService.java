package pl.mkn.tdw.features.incidentanalysis.job;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.incidentanalysis.job.api.AnalysisJobInputOptionsResponse;
import pl.mkn.tdw.features.incidentanalysis.job.api.AnalysisJobLogSource;
import pl.mkn.tdw.integrations.elasticsearch.ElasticConnectionAvailabilityService;
import pl.mkn.tdw.integrations.elasticsearch.ElasticProperties;

@Service
@RequiredArgsConstructor
public class AnalysisJobInputOptionsService {

    private final ElasticConnectionAvailabilityService elasticAvailabilityService;

    public AnalysisJobInputOptionsResponse currentOptions() {
        var elasticAvailability = elasticAvailabilityService.currentAvailability();
        return new AnalysisJobInputOptionsResponse(
                new AnalysisJobInputOptionsResponse.LogSourceOption(
                        AnalysisJobLogSource.ELASTICSEARCH.name(),
                        elasticAvailability.configured(),
                        elasticAvailability.configured()
                                ? null
                                : elasticAvailability.disabledReason()
                ),
                new AnalysisJobInputOptionsResponse.LogSourceOption(
                        AnalysisJobLogSource.CSV_UPLOAD.name(),
                        true,
                        null
                )
        );
    }

    public boolean elasticsearchStartEnabled() {
        return currentOptions().elasticsearch().enabled();
    }

    static AnalysisJobInputOptionsService elasticsearchAvailableForTests() {
        var properties = new ElasticProperties();
        properties.setBaseUrl("https://kibana.example.internal");
        properties.setKibanaSpaceId("default");
        properties.setIndexPattern("logs-*");
        properties.setAuthorizationHeader("ApiKey test");
        return new AnalysisJobInputOptionsService(new ElasticConnectionAvailabilityService(properties));
    }
}
