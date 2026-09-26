package pl.mkn.tdw.integrations.database.config;

public final class DatabaseEnvironmentPropertiesTestCreator {

    private DatabaseEnvironmentPropertiesTestCreator() {
    }

    public static DatabaseEnvironmentProperties withJdbcUrl(String jdbcUrl) {
        var properties = new DatabaseEnvironmentProperties();
        properties.setJdbcUrl(jdbcUrl);
        return properties;
    }

    public static DatabaseEnvironmentProperties withJdbcUrlAndDriver(String jdbcUrl, String driverClassName) {
        var properties = withJdbcUrl(jdbcUrl);
        properties.setDriverClassName(driverClassName);
        return properties;
    }

    public static DatabaseEnvironmentProperties withDatabaseAlias(String databaseAlias) {
        var properties = new DatabaseEnvironmentProperties();
        properties.setDatabaseAlias(databaseAlias);
        return properties;
    }
}
