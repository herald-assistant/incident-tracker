package pl.mkn.incidenttracker.analysis.adapter.database;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "analysis.database")
public class DatabaseToolProperties {

    private boolean enabled = false;
    private Map<String, DatabaseEnvironmentProperties> environments = new LinkedHashMap<>();

    private int maxRows = 50;
    private int maxColumns = 40;
    private int maxTablesPerSearch = 30;
    private int maxColumnsPerSearch = 50;
    private int maxTablesToDescribe = 5;
    private int maxResultCharacters = 64_000;
    private Duration queryTimeout = Duration.ofSeconds(5);
    private Duration connectionTimeout = Duration.ofSeconds(5);
    private boolean rawSqlEnabled = false;
    private boolean allowAllSchemas = false;
}
