package pl.mkn.tdw.integrations.elasticsearch;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ElasticConnectionAvailabilityService {

    private static final String BASE_URL_PROPERTY = "analysis.elasticsearch.base-url";
    private static final String KIBANA_SPACE_ID_PROPERTY = "analysis.elasticsearch.kibana-space-id";
    private static final String INDEX_PATTERN_PROPERTY = "analysis.elasticsearch.index-pattern";
    private static final String AUTHORIZATION_HEADER_PROPERTY = "analysis.elasticsearch.authorization-header";

    private final ElasticProperties properties;

    public ElasticConnectionAvailability currentAvailability() {
        var missing = missingPropertyKeys();
        if (missing.isEmpty()) {
            return new ElasticConnectionAvailability(true, missing, null);
        }

        return new ElasticConnectionAvailability(
                false,
                missing,
                "Elasticsearch/Kibana nie jest skonfigurowany. Brakujace ustawienia: "
                        + String.join(", ", missing) + "."
        );
    }

    private List<String> missingPropertyKeys() {
        var missing = new ArrayList<String>();
        addMissing(missing, BASE_URL_PROPERTY, properties.getBaseUrl());
        addMissing(missing, KIBANA_SPACE_ID_PROPERTY, properties.getKibanaSpaceId());
        addMissing(missing, INDEX_PATTERN_PROPERTY, properties.getIndexPattern());
        addMissing(missing, AUTHORIZATION_HEADER_PROPERTY, properties.getAuthorizationHeader());
        return List.copyOf(missing);
    }

    private void addMissing(List<String> missing, String propertyKey, String value) {
        if (!StringUtils.hasText(value)) {
            missing.add(propertyKey);
        }
    }
}
