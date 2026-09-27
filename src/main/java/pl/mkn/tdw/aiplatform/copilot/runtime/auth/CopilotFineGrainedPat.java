package pl.mkn.tdw.aiplatform.copilot.runtime.auth;

import org.springframework.util.StringUtils;

public final class CopilotFineGrainedPat {

    private static final String PREFIX = "github_pat_";

    private CopilotFineGrainedPat() {
    }

    public static boolean valid(String value) {
        return StringUtils.hasText(value) && value.trim().startsWith(PREFIX)
                && value.trim().length() > PREFIX.length();
    }
}
