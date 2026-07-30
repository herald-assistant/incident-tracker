package pl.mkn.tdw.features.runtimeconfigurationverification.scope;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public class RuntimeConfigurationScopeException extends UserFacingApplicationException {

    private RuntimeConfigurationScopeException(
            String code,
            UserFacingErrorType errorType,
            String message
    ) {
        super(code, errorType, message);
    }

    public static RuntimeConfigurationScopeException repositoryNotFound(String repositoryId) {
        return new RuntimeConfigurationScopeException(
                "RUNTIME_CONFIGURATION_REPOSITORY_NOT_FOUND",
                UserFacingErrorType.NOT_FOUND,
                "Runtime configuration repository is not available: " + safeId(repositoryId)
        );
    }

    public static RuntimeConfigurationScopeException repositoryUnavailable(String repositoryId) {
        return new RuntimeConfigurationScopeException(
                "RUNTIME_CONFIGURATION_REPOSITORY_UNAVAILABLE",
                UserFacingErrorType.SERVICE_UNAVAILABLE,
                "Runtime configuration repository is not configured correctly: " + safeId(repositoryId)
        );
    }

    public static RuntimeConfigurationScopeException systemNotFound(String systemId) {
        return new RuntimeConfigurationScopeException(
                "RUNTIME_CONFIGURATION_SYSTEM_NOT_FOUND",
                UserFacingErrorType.NOT_FOUND,
                "Operational Context system is not available: " + safeId(systemId)
        );
    }

    public static RuntimeConfigurationScopeException systemNotInternal(String systemId) {
        return new RuntimeConfigurationScopeException(
                "RUNTIME_CONFIGURATION_SYSTEM_NOT_INTERNAL",
                UserFacingErrorType.BAD_REQUEST,
                "Operational Context system must have kind internal-system: " + safeId(systemId)
        );
    }

    public static RuntimeConfigurationScopeException configurationDirectoryMissing(String systemId) {
        return new RuntimeConfigurationScopeException(
                "RUNTIME_CONFIGURATION_DIRECTORY_MISSING",
                UserFacingErrorType.BAD_REQUEST,
                "Operational Context system has no configuration-directory runtime signal: " + safeId(systemId)
        );
    }

    public static RuntimeConfigurationScopeException configurationDirectoryAmbiguous(String systemId) {
        return new RuntimeConfigurationScopeException(
                "RUNTIME_CONFIGURATION_DIRECTORY_AMBIGUOUS",
                UserFacingErrorType.CONFLICT,
                "Operational Context system has more than one configuration-directory runtime signal: "
                        + safeId(systemId)
        );
    }

    public static RuntimeConfigurationScopeException configurationDirectoryInvalid(String systemId) {
        return new RuntimeConfigurationScopeException(
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
