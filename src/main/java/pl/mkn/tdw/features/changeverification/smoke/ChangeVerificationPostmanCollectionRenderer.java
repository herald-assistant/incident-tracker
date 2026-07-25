package pl.mkn.tdw.features.changeverification.smoke;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationNameValueResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeAssertionResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokePackResponse;
import pl.mkn.tdw.features.changeverification.job.api.ChangeVerificationSmokeTestResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ChangeVerificationPostmanCollectionRenderer {

    private static final String POSTMAN_SCHEMA =
            "https://schema.getpostman.com/json/collection/v2.1.0/collection.json";

    public Map<String, Object> render(ChangeVerificationSmokePackResponse smokePack) {
        var collection = new LinkedHashMap<String, Object>();
        collection.put("info", Map.of(
                "name", collectionName(smokePack),
                "schema", POSTMAN_SCHEMA
        ));
        collection.put("variable", List.of(Map.of(
                "key", "baseUrl",
                "value", "https://example.test",
                "type", "string"
        )));
        collection.put("item", smokePack.tests().stream()
                .map(this::renderItem)
                .toList());
        return collection;
    }

    private Map<String, Object> renderItem(ChangeVerificationSmokeTestResponse test) {
        var item = new LinkedHashMap<String, Object>();
        item.put("name", value(test.name(), test.id()));
        item.put("request", renderRequest(test));
        item.put("event", List.of(Map.of(
                "listen", "test",
                "script", Map.of(
                        "type", "text/javascript",
                        "exec", testScript(test)
                )
        )));
        return item;
    }

    private Map<String, Object> renderRequest(ChangeVerificationSmokeTestResponse test) {
        var request = new LinkedHashMap<String, Object>();
        request.put("method", value(test.method(), "GET").toUpperCase());
        request.put("header", test.headers().stream()
                .filter(ChangeVerificationNameValueResponse::enabled)
                .map(header -> Map.of(
                        "key", value(header.name(), ""),
                        "value", value(header.value(), "")
                ))
                .toList());
        request.put("url", renderUrl(test));
        if (StringUtils.hasText(test.requestBody())) {
            request.put("body", Map.of(
                    "mode", "raw",
                    "raw", test.requestBody(),
                    "options", Map.of("raw", Map.of("language", "json"))
            ));
        }
        request.put("description", requestDescription(test));
        return request;
    }

    private Map<String, Object> renderUrl(ChangeVerificationSmokeTestResponse test) {
        var path = normalizePath(test.path());
        var url = new LinkedHashMap<String, Object>();
        url.put("raw", "{{baseUrl}}" + path + queryString(test.queryParams()));
        url.put("host", List.of("{{baseUrl}}"));
        url.put("path", pathSegments(path));
        url.put("query", test.queryParams().stream()
                .filter(ChangeVerificationNameValueResponse::enabled)
                .map(queryParam -> Map.of(
                        "key", value(queryParam.name(), ""),
                        "value", value(queryParam.value(), "")
                ))
                .toList());
        return url;
    }

    private List<String> pathSegments(String path) {
        return List.of(path.replaceFirst("^/", "").split("/")).stream()
                .filter(StringUtils::hasText)
                .toList();
    }

    private String queryString(List<ChangeVerificationNameValueResponse> queryParams) {
        var enabled = queryParams.stream()
                .filter(ChangeVerificationNameValueResponse::enabled)
                .filter(param -> StringUtils.hasText(param.name()))
                .toList();
        if (enabled.isEmpty()) {
            return "";
        }
        return "?" + enabled.stream()
                .map(param -> param.name() + "=" + value(param.value(), ""))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
    }

    private List<String> testScript(ChangeVerificationSmokeTestResponse test) {
        var lines = new ArrayList<String>();
        lines.add("pm.test(\"request completed\", function () {");
        lines.add("  pm.expect(pm.response).to.exist;");
        lines.add("});");
        for (var assertion : test.responseAssertions()) {
            lines.addAll(assertionScript(assertion));
        }
        if (!test.dbAssertions().isEmpty()) {
            lines.add("// Readonly DB assertions to verify outside Postman:");
            test.dbAssertions().forEach(assertion -> lines.add("// - " + assertion));
        }
        if (!test.dbAssertionSpecs().isEmpty()) {
            lines.add("// Structured readonly DB assertions:");
            test.dbAssertionSpecs().forEach(assertion -> lines.add("// - %s %s %s".formatted(
                    value(assertion.id(), assertion.sql()),
                    value(assertion.operator(), "REVIEW"),
                    value(assertion.expectedValue(), "")
            )));
        }
        if (test.cleanup() != null) {
            lines.add("// Cleanup strategy: " + value(test.cleanup().strategy(), "n/a"));
            if (StringUtils.hasText(test.cleanup().manualSql())) {
                lines.add("// Manual SQL fallback for operator:");
                lines.add("// " + test.cleanup().manualSql());
            }
        }
        return List.copyOf(lines);
    }

    private List<String> assertionScript(ChangeVerificationSmokeAssertionResponse assertion) {
        var type = value(assertion.type(), "").toUpperCase();
        if ("STATUS".equals(type)) {
            return List.of(
                    "pm.test(\"status is " + value(assertion.expectedValue(), "200") + "\", function () {",
                    "  pm.response.to.have.status(" + value(assertion.expectedValue(), "200") + ");",
                    "});"
            );
        }
        if ("JSON_PATH".equals(type)) {
            return List.of(
                    "pm.test(\"json path " + value(assertion.target(), "$") + "\", function () {",
                    "  const json = pm.response.json();",
                    "  pm.expect(json).to.not.equal(undefined);",
                    "});"
            );
        }
        if ("HEADER".equals(type)) {
            return List.of(
                    "pm.test(\"header " + value(assertion.target(), "") + "\", function () {",
                    "  pm.response.to.have.header(\"" + value(assertion.target(), "") + "\");",
                    "});"
            );
        }
        return List.of("// Review assertion: " + value(assertion.target(), assertion.type()));
    }

    private String requestDescription(ChangeVerificationSmokeTestResponse test) {
        var lines = new ArrayList<String>();
        lines.add(value(test.purpose(), "Smoke test generated by Change Verification."));
        if (StringUtils.hasText(test.riskCovered())) {
            lines.add("Risk covered: " + test.riskCovered());
        }
        if (!test.sourceRefs().isEmpty()) {
            lines.add("Source refs: " + String.join(", ", test.sourceRefs()));
        }
        if (!test.cleanupHints().isEmpty()) {
            lines.add("Cleanup hints: " + String.join("; ", test.cleanupHints()));
        }
        return String.join("\n", lines);
    }

    private String collectionName(ChangeVerificationSmokePackResponse smokePack) {
        return value(smokePack.postmanCollectionName(), "Change Verification smoke pack");
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private String value(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }
}
