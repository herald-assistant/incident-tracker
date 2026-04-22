package pl.mkn.incidenttracker.analysis.adapter.database;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class DatabaseApplicationProperties {

    private String schema;
    private String description;
    private List<String> applicationNamePatterns = new ArrayList<>();
    private List<String> deploymentNamePatterns = new ArrayList<>();
    private List<String> containerNamePatterns = new ArrayList<>();
    private List<String> projectNamePatterns = new ArrayList<>();
    private List<String> relatedSchemas = new ArrayList<>();
}
