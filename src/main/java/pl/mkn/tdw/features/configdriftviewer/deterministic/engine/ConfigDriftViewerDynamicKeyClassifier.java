package pl.mkn.tdw.features.configdriftviewer.deterministic.engine;

import java.util.regex.Pattern;

final class ConfigDriftViewerDynamicKeyClassifier {

    private static final Pattern UUID = Pattern.compile(
            "(?i)[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
    );
    private static final Pattern LONG_NUMBER = Pattern.compile("\\d{5,}");
    private static final Pattern LONG_HEX = Pattern.compile("(?i)[0-9a-f]{16,}");

    boolean dynamic(String key) {
        if (key == null || key.isBlank() || key.startsWith("[")) {
            return false;
        }
        return key.contains("@")
                || key.length() > 64
                || UUID.matcher(key).matches()
                || LONG_NUMBER.matcher(key).matches()
                || LONG_HEX.matcher(key).matches();
    }
}
