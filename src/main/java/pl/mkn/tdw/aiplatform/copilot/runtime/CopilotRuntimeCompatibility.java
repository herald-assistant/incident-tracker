package pl.mkn.tdw.aiplatform.copilot.runtime;

import com.github.copilot.CopilotClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Properties;
import java.util.regex.Pattern;

@Component
public class CopilotRuntimeCompatibility {

    private static final String VERSION_RESOURCE = "copilot-runtime-version.properties";
    private static final Pattern VERSION_PATTERN = Pattern.compile(".*?(\\d+)\\.(\\d+)\\.(\\d+).*");
    private static final VersionRequirements REQUIREMENTS = loadRequirements();

    public CopilotRuntimeVersionInfo inspect(CopilotClient client) {
        var status = client.getStatus().join();
        if (status == null || !StringUtils.hasText(status.getVersion())) {
            throw new IllegalStateException("Copilot CLI did not report its runtime version.");
        }

        var cliVersion = status.getVersion().trim();
        return new CopilotRuntimeVersionInfo(
                REQUIREMENTS.sdkVersion(),
                cliVersion,
                status.getProtocolVersion(),
                REQUIREMENTS.minimumCliVersion(),
                isAtLeast(cliVersion, REQUIREMENTS.minimumCliVersion())
        );
    }

    static boolean isAtLeast(String actual, String minimum) {
        var actualVersion = parseVersion(actual);
        var minimumVersion = parseVersion(minimum);
        for (var index = 0; index < actualVersion.length; index++) {
            var comparison = Integer.compare(actualVersion[index], minimumVersion[index]);
            if (comparison != 0) {
                return comparison > 0;
            }
        }
        return true;
    }

    private static int[] parseVersion(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Copilot runtime version must not be blank.");
        }
        var matcher = VERSION_PATTERN.matcher(value.trim());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Unsupported Copilot runtime version: " + value);
        }
        return new int[]{
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))
        };
    }

    private static VersionRequirements loadRequirements() {
        var properties = new Properties();
        try (var input = CopilotRuntimeCompatibility.class.getClassLoader().getResourceAsStream(VERSION_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("Missing " + VERSION_RESOURCE + ".");
            }
            properties.load(input);
        } catch (IOException failure) {
            throw new IllegalStateException("Could not read " + VERSION_RESOURCE + ".", failure);
        }

        return new VersionRequirements(
                required(properties, "copilot.sdk.version"),
                required(properties, "copilot.cli.minimum-version")
        );
    }

    private static String required(Properties properties, String key) {
        var value = properties.getProperty(key);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Missing Copilot runtime version property: " + key);
        }
        return value.trim();
    }

    private record VersionRequirements(String sdkVersion, String minimumCliVersion) {
    }
}
