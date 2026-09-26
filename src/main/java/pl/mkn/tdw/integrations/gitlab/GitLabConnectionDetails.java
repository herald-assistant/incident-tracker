package pl.mkn.tdw.integrations.gitlab;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.util.StringUtils;

public final class GitLabConnectionDetails {

    private final String id;
    private final String baseUrl;
    private final String token;

    public GitLabConnectionDetails(String id, String baseUrl, String token) {
        this.id = normalize(id);
        this.baseUrl = normalize(baseUrl);
        this.token = normalize(token);
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

    @Override
    public String toString() {
        return "GitLabConnectionDetails[id=" + id
                + ", baseUrl=" + baseUrl
                + ", token=<redacted>"
                + "]";
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
