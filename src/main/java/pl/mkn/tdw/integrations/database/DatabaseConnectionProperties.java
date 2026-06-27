package pl.mkn.tdw.integrations.database;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DatabaseConnectionProperties {

    private String jdbcUrl;
    private String driverClassName;
    private String username;
    private String password;
    private String databaseAlias = "oracle";
    private String description;
}
