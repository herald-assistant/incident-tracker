package pl.mkn.tdw.features.flowexplorer.endpoint;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class FlowExplorerGitLabConfigurationException extends UserFacingApplicationException {

    public FlowExplorerGitLabConfigurationException(String propertyName) {
        super(
                "FLOW_EXPLORER_GITLAB_CONFIGURATION_MISSING",
                UserFacingErrorType.SERVICE_UNAVAILABLE,
                "Flow Explorer GitLab configuration is missing required property: " + propertyName
        );
    }
}
