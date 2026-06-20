package pl.mkn.incidenttracker.features.flowexplorer.endpoint;

import pl.mkn.incidenttracker.shared.error.UserFacingApplicationException;
import pl.mkn.incidenttracker.shared.error.UserFacingErrorType;

public class FlowExplorerGitLabConfigurationException extends UserFacingApplicationException {

    public FlowExplorerGitLabConfigurationException(String propertyName) {
        super(
                "FLOW_EXPLORER_GITLAB_CONFIGURATION_MISSING",
                UserFacingErrorType.SERVICE_UNAVAILABLE,
                "Flow Explorer GitLab configuration is missing required property: " + propertyName
        );
    }
}
