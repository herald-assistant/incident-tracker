package pl.mkn.tdw.features.configdriftviewer.scope;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class ConfigDriftViewerScopeException extends UserFacingApplicationException {

    private ConfigDriftViewerScopeException(
            String code,
            UserFacingErrorType errorType,
            String message
    ) {
        super(code, errorType, message);
    }

    public static ConfigDriftViewerScopeException repositoryNotFound(String repositoryId) {
        return new ConfigDriftViewerScopeException(
                "RUNTIME_CONFIGURATION_REPOSITORY_NOT_FOUND",
                UserFacingErrorType.NOT_FOUND,
                "Runtime configuration repository is not available: " + safeId(repositoryId)
        );
    }

    public static ConfigDriftViewerScopeException repositoryUnavailable(String repositoryId) {
        return new ConfigDriftViewerScopeException(
                "RUNTIME_CONFIGURATION_REPOSITORY_UNAVAILABLE",
                UserFacingErrorType.SERVICE_UNAVAILABLE,
                "Runtime configuration repository is not configured correctly: " + safeId(repositoryId)
        );
    }

    public static ConfigDriftViewerScopeException systemNotFound(String systemId) {
        return new ConfigDriftViewerScopeException(
                "RUNTIME_CONFIGURATION_SYSTEM_NOT_FOUND",
                UserFacingErrorType.NOT_FOUND,
                "Operational Context system is not available: " + safeId(systemId)
        );
    }

    public static ConfigDriftViewerScopeException systemNotInternalService(String systemId) {
        return new ConfigDriftViewerScopeException(
                "RUNTIME_CONFIGURATION_SYSTEM_NOT_INTERNAL_SERVICE",
                UserFacingErrorType.BAD_REQUEST,
                "Operational Context system must have kind internal-service: " + safeId(systemId)
        );
    }

    public static ConfigDriftViewerScopeException configurationDirectoryMissing(String systemId) {
        return new ConfigDriftViewerScopeException(
                "RUNTIME_CONFIGURATION_DIRECTORY_MISSING",
                UserFacingErrorType.BAD_REQUEST,
                "Operational Context system has no configuration-directory runtime signal: " + safeId(systemId)
        );
    }

    public static ConfigDriftViewerScopeException configurationDirectoryAmbiguous(String systemId) {
        return new ConfigDriftViewerScopeException(
                "RUNTIME_CONFIGURATION_DIRECTORY_AMBIGUOUS",
                UserFacingErrorType.CONFLICT,
                "Operational Context system has more than one configuration-directory runtime signal: "
                        + safeId(systemId)
        );
    }

    public static ConfigDriftViewerScopeException configurationDirectoryInvalid(String systemId) {
        return new ConfigDriftViewerScopeException(
                "RUNTIME_CONFIGURATION_DIRECTORY_INVALID",
                UserFacingErrorType.BAD_REQUEST,
                "Operational Context system has an unsafe configuration-directory runtime signal: "
                        + safeId(systemId)
        );
    }

    private static String safeId(String value) {
        if (value == null) {
            return "<missing>";
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "?");
    }
}
