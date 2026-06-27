package pl.mkn.tdw.aiplatform.copilot.runtime.auth;

import org.springframework.util.StringUtils;

import java.util.Locale;

public enum CopilotAuthMode {
    LOCAL_TOKEN,
    GITHUB_APP;

    public static CopilotAuthMode from(String value) {
        if (!StringUtils.hasText(value)) {
            return LOCAL_TOKEN;
        }

        return CopilotAuthMode.valueOf(value.trim().replace('-', '_').toUpperCase(Locale.ROOT));
    }
}
