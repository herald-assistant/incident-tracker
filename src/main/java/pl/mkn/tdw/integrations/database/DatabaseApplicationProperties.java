package pl.mkn.tdw.integrations.database;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class DatabaseApplicationProperties {

    private String databaseUser;
    private String schema;
    private String description;
    private List<String> applicationPatterns = new ArrayList<>();
    private List<String> relatedSchemas = new ArrayList<>();
}
