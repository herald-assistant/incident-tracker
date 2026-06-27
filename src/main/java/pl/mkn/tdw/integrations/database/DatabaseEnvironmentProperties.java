package pl.mkn.tdw.integrations.database;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
public class DatabaseEnvironmentProperties {

    @Setter
    private String connection;
    @Setter
    private String applicationUserSuffix;
    @Setter(AccessLevel.PACKAGE)
    private String jdbcUrl;
    @Setter(AccessLevel.PACKAGE)
    private String driverClassName;
    @Setter(AccessLevel.PACKAGE)
    private String username;
    @Setter(AccessLevel.PACKAGE)
    private String password;
    @Setter(AccessLevel.PACKAGE)
    private String databaseAlias = "oracle";
    @Setter
    private String description;
    @Setter
    private List<String> allowedSchemas = new ArrayList<>();
    private Map<String, DatabaseApplicationProperties> resolvedApplications = new LinkedHashMap<>();

    public Map<String, DatabaseApplicationProperties> getApplications() {
        return Collections.unmodifiableMap(resolvedApplications);
    }

    void setResolvedApplications(Map<String, DatabaseApplicationProperties> applications) {
        this.resolvedApplications = new LinkedHashMap<>(applications);
    }
}
