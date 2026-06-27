package pl.mkn.tdw.shared.ai;

import org.springframework.util.StringUtils;

public record AnalysisAiAuthRef(
        String provider,
        String mode,
        String principalId,
        String providerAccountLabel,
        boolean userBilling
) {

    public static final String PROVIDER_GITHUB = "github";
    public static final String MODE_LOCAL_TOKEN = "LOCAL_TOKEN";
    public static final String MODE_GITHUB_APP = "GITHUB_APP";
    public static final String LOCAL_TOKEN_PRINCIPAL = "local-token";

    public AnalysisAiAuthRef {
        provider = normalize(provider);
        mode = normalize(mode);
        principalId = normalize(principalId);
        providerAccountLabel = normalize(providerAccountLabel);
    }

    public static AnalysisAiAuthRef localToken(String displayName) {
        return new AnalysisAiAuthRef(
                PROVIDER_GITHUB,
                MODE_LOCAL_TOKEN,
                LOCAL_TOKEN_PRINCIPAL,
                StringUtils.hasText(displayName) ? displayName.trim() : "Local developer token",
                false
        );
    }

    public static AnalysisAiAuthRef githubApp(String operatorSessionId, String githubLogin) {
        return new AnalysisAiAuthRef(
                PROVIDER_GITHUB,
                MODE_GITHUB_APP,
                operatorSessionId,
                githubLogin,
                true
        );
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
