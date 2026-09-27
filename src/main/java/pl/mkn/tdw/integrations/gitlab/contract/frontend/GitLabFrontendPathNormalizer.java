package pl.mkn.tdw.integrations.gitlab.contract.frontend;

import org.springframework.util.StringUtils;

import java.util.ArrayList;

/** Shared normalization for validated frontend source paths. */
public final class GitLabFrontendPathNormalizer {

    private GitLabFrontendPathNormalizer() {
    }

    public static String normalize(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            return "";
        }
        var segments = new ArrayList<String>();
        for (var segment : rawPath.trim().replace('\\', '/').split("/")) {
            if (!StringUtils.hasText(segment) || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (segments.isEmpty()) {
                    return "";
                }
                segments.remove(segments.size() - 1);
            } else {
                segments.add(segment);
            }
        }
        return String.join("/", segments);
    }
}
