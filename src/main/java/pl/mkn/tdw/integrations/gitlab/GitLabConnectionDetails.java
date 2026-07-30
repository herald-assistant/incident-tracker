package pl.mkn.tdw.integrations.gitlab;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.util.StringUtils;

public final class GitLabConnectionDetails {

    private final String id;
    private final String baseUrl;
    private final String token;
    private final boolean ignoreSslErrors;

    public GitLabConnectionDetails(String id, String baseUrl, String token, boolean ignoreSslErrors) {
        this.id = normalize(id);
        this.baseUrl = normalize(baseUrl);
        this.token = normalize(token);
        this.ignoreSslErrors = ignoreSslErrors;
    }

    public String id() {
        return id;
    }

    public String baseUrl() {
        return baseUrl;
    }

    @JsonIgnore
    public String token() {
        return token;
    }

    public boolean ignoreSslErrors() {
        return ignoreSslErrors;
    }

    @Override
    public String toString() {
        return "GitLabConnectionDetails[id=" + id
                + ", baseUrl=" + baseUrl
                + ", token=<redacted>"
                + ", ignoreSslErrors=" + ignoreSslErrors
                + "]";
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
