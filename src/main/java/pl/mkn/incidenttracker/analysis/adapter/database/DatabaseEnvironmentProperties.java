package pl.mkn.incidenttracker.analysis.adapter.database;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
public class DatabaseEnvironmentProperties {

    private String jdbcUrl;
    private String driverClassName;
    private String username;
    private String password;
    private String databaseAlias = "oracle";
    private String description;
    private List<String> allowedSchemas = new ArrayList<>();
    private Map<String, DatabaseApplicationProperties> applications = new LinkedHashMap<>();
}
