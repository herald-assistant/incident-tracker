package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.engine;

import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationSensitivity;

import java.util.Set;

final class RuntimeConfigurationSensitivityClassifier {

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

    RuntimeConfigurationSensitivity classify(String path) {
        var normalized = (path != null ? path : "")
                .replaceAll("([a-z0-9])([A-Z])", "$1.$2")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", ".");
        for (var token : normalized.split("\\.")) {
            if (SENSITIVE_TOKENS.contains(token)) {
                return RuntimeConfigurationSensitivity.SENSITIVE;
            }
        }
        return RuntimeConfigurationSensitivity.NON_SENSITIVE;
    }
}
