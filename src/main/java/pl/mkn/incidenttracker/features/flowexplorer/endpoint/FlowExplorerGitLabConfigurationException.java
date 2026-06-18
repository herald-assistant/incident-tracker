package pl.mkn.incidenttracker.features.flowexplorer.endpoint;

public class FlowExplorerGitLabConfigurationException extends RuntimeException {

    public FlowExplorerGitLabConfigurationException(String propertyName) {
        super("Flow Explorer GitLab configuration is missing required property: " + propertyName);
    }
}
