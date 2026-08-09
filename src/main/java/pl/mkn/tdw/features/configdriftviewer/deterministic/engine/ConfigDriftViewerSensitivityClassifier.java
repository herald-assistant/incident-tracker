package pl.mkn.tdw.features.configdriftviewer.deterministic.engine;

import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerSensitivity;

import java.util.Set;

final class ConfigDriftViewerSensitivityClassifier {

    private static final Set<String> SENSITIVE_TOKENS = Set.of(
            "password",
            "passwd",
            "pwd",
            "secret",
            "token",
            "credential",
            "credentials",
            "authorization",
            "apikey",
            "privatekey",
            "keystore",
            "truststore",
            "certificate",
            "username",
            "user"
    );

    ConfigDriftViewerSensitivity classify(String path) {
        var normalized = (path != null ? path : "")
                .replaceAll("([a-z0-9])([A-Z])", "$1.$2")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", ".");
        for (var token : normalized.split("\\.")) {
            if (SENSITIVE_TOKENS.contains(token)) {
                return ConfigDriftViewerSensitivity.SENSITIVE;
            }
        }
        return ConfigDriftViewerSensitivity.NON_SENSITIVE;
    }
}
