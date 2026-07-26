package pl.mkn.tdw.integrations.confluence;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Component
@RequiredArgsConstructor
public class ConfluenceRestPageAdapter implements ConfluencePagePort {

    private static final Pattern PAGE_ID_QUERY_PATTERN = Pattern.compile("[?&]pageId=(\\d+)(?:&|$)");
    private static final Pattern PAGE_ID_PATH_PATTERN = Pattern.compile("/pages/(\\d+)(?:/|$)");

    private final ConfluenceProperties properties;
    private final ConfluenceRestClientFactory restClientFactory;

    @Override
    public Optional<ConfluencePageContent> getPageContent(String pageUrl) {
        if (!StringUtils.hasText(pageUrl) || !isSupportedConfluenceUrl(pageUrl.trim())) {
            return Optional.empty();
        }

        var pageId = pageId(pageUrl.trim());
        if (!StringUtils.hasText(pageId)) {
            return Optional.of(new ConfluencePageContent(
                    "",
                    "",
                    pageUrl.trim(),
                    "",
                    "",
                    List.of("Confluence pageId could not be extracted from remote link URL.")
            ));
        }

        try {
            var response = restClientFactory.create()
                    .get()
                    .uri(contentUri(pageUrl.trim(), pageId))
                    .retrieve()
                    .body(JsonNode.class);
            return Optional.of(toContent(pageUrl.trim(), pageId, response));
        } catch (RestClientResponseException exception) {
            return Optional.of(new ConfluencePageContent(
                    pageId,
                    "",
                    pageUrl.trim(),
                    "",
                    "",
                    List.of("Confluence page fetch failed with HTTP " + exception.getStatusCode().value() + ".")
            ));
        } catch (RuntimeException exception) {
            return Optional.of(new ConfluencePageContent(
                    pageId,
                    "",
                    pageUrl.trim(),
                    "",
                    "",
                    List.of("Confluence page fetch failed: " + exception.getClass().getSimpleName() + ".")
            ));
        }
    }

    private ConfluencePageContent toContent(String pageUrl, String pageId, JsonNode response) {
        var storage = response != null ? response.path("body").path("storage").path("value") : null;
        var content = storage != null && storage.isTextual() ? storage.asText() : "";
        return new ConfluencePageContent(
                text(response, "id", pageId),
                text(response, "title", ""),
                pageUrl,
                limitText(stripHtml(content)),
                text(response != null ? response.path("version") : null, "number", ""),
                List.of()
        );
    }

    private boolean isSupportedConfluenceUrl(String pageUrl) {
        if (!StringUtils.hasText(properties.getUrlPattern())) {
            return false;
        }
        try {
            return Pattern.compile(properties.getUrlPattern().trim()).matcher(pageUrl).find();
        } catch (PatternSyntaxException exception) {
            return false;
        }
    }

    private String pageId(String pageUrl) {
        var queryMatcher = PAGE_ID_QUERY_PATTERN.matcher(pageUrl);
        if (queryMatcher.find()) {
            return queryMatcher.group(1);
        }
        var pathMatcher = PAGE_ID_PATH_PATTERN.matcher(pageUrl);
        return pathMatcher.find() ? pathMatcher.group(1) : "";
    }

    private URI contentUri(String pageUrl, String pageId) {
        return URI.create(restBaseUrl(pageUrl)
                + "/rest/api/content/" + UriUtils.encodePathSegment(pageId, StandardCharsets.UTF_8)
                + "?expand=body.storage,version");
    }

    private String restBaseUrl(String pageUrl) {
        if (StringUtils.hasText(properties.getBaseUrl())) {
            return stripTrailingSlash(properties.getBaseUrl().trim());
        }
        var uri = URI.create(pageUrl);
        var port = uri.getPort() >= 0 ? ":" + uri.getPort() : "";
        return uri.getScheme() + "://" + uri.getHost() + port;
    }

    private String stripHtml(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        var withBreaks = value
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)</li>", "\n");
        var withoutTags = withBreaks.replaceAll("<[^>]+>", " ");
        return HtmlUtils.htmlUnescape(withoutTags)
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll("\\n\\s+", "\n")
                .trim();
    }

    private String limitText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        var trimmed = value.trim();
        var maxCharacters = Math.max(100, properties.getMaxTextCharacters());
        return trimmed.length() > maxCharacters ? trimmed.substring(0, maxCharacters) : trimmed;
    }

    private String text(JsonNode node, String fieldName, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        var value = node.path(fieldName);
        if (value.isNumber() || value.isBoolean()) {
            return value.asText();
        }
        return value.isTextual() && StringUtils.hasText(value.asText()) ? value.asText().trim() : fallback;
    }

    private String stripTrailingSlash(String value) {
        return value.replaceFirst("/+$", "");
    }
}
