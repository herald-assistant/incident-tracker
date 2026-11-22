package pl.mkn.tdw.integrations.openapi.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashSet;
import java.util.Set;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiException;

/** Security metadata documents the API; operator-owned authentication is selected separately. */
final class OpenApiSecurity {
    private OpenApiSecurity() { }

    static void validate(JsonNode root) {
        var schemes = root.path("components").path("securitySchemes");
        if (!schemes.isMissingNode() && (!schemes.isObject() || schemes.size() > 100)) fail("Nieprawidłowe securitySchemes.");
        schemes.fields().forEachRemaining(entry -> {
            var scheme = entry.getValue();
            if (!scheme.isObject()) fail("Nieprawidłowy securityScheme.");
            var type = scheme.path("type").asText();
            switch (type) {
                case "http" -> {
                    fields(scheme, "type", "description", "scheme", "bearerFormat");
                    if (!Set.of("basic", "bearer").contains(scheme.path("scheme").asText().toLowerCase(java.util.Locale.ROOT)))
                        fail("Obsługiwane HTTP securitySchemes: basic, bearer.");
                }
                case "openIdConnect" -> {
                    fields(scheme, "type", "description", "openIdConnectUrl");
                    url(scheme.path("openIdConnectUrl"));
                }
                case "oauth2" -> {
                    fields(scheme, "type", "description", "flows");
                    var flows = scheme.path("flows");
                    if (!flows.isObject() || flows.isEmpty()) fail("OAuth2 wymaga flows.");
                    flows.fields().forEachRemaining(flow -> {
                        if (!Set.of("authorizationCode", "clientCredentials", "implicit", "password").contains(flow.getKey())) fail("Nieznany OAuth2 flow.");
                        fields(flow.getValue(), "authorizationUrl", "tokenUrl", "refreshUrl", "scopes");
                        if (Set.of("authorizationCode", "implicit").contains(flow.getKey())) url(flow.getValue().path("authorizationUrl"));
                        if (!flow.getKey().equals("implicit")) url(flow.getValue().path("tokenUrl"));
                        if (flow.getValue().has("refreshUrl")) url(flow.getValue().get("refreshUrl"));
                        if (!flow.getValue().path("scopes").isObject()) fail("OAuth2 scopes musi być obiektem.");
                        flow.getValue().path("scopes").forEach(scope -> { if (!scope.isTextual()) fail("Opis scope musi być tekstem."); });
                    });
                }
                default -> fail("Obsługiwane securitySchemes: Basic, Bearer, OAuth2 i openIdConnect.");
            }
        });
        requirement(root, schemes);
        root.path("paths").forEach(path -> path.fields().forEachRemaining(entry -> {
            if (Set.of("get", "post", "put", "delete").contains(entry.getKey())) requirement(entry.getValue(), schemes);
        }));
    }

    static Set<String> usedSchemes(JsonNode source, JsonNode paths) {
        var names = new LinkedHashSet<String>();
        addNames(source.path("security"), names);
        paths.forEach(path -> path.fields().forEachRemaining(entry -> {
            if (Set.of("get", "post", "put", "delete").contains(entry.getKey())) addNames(entry.getValue().path("security"), names);
        }));
        return names;
    }

    private static void addNames(JsonNode security, Set<String> names) {
        security.forEach(requirement -> requirement.fieldNames().forEachRemaining(names::add));
    }

    private static void requirement(JsonNode owner, JsonNode schemes) {
        if (!owner.has("security")) return;
        var security = owner.get("security");
        if (!security.isArray()) fail("security musi być tablicą.");
        security.forEach(requirement -> {
            if (!requirement.isObject()) fail("Nieprawidłowe wymaganie security.");
            requirement.fields().forEachRemaining(entry -> {
                if (!schemes.has(entry.getKey())) fail("Nieznany securityScheme: " + entry.getKey());
                if (!entry.getValue().isArray()) fail("Security scopes muszą być tablicą.");
                entry.getValue().forEach(scope -> { if (!scope.isTextual()) fail("Scope musi być tekstem."); });
                if (Set.of("basic", "bearer").contains(schemes.path(entry.getKey()).path("scheme").asText()) && !entry.getValue().isEmpty())
                    fail("Basic i Bearer nie definiują OAuth2 scopes.");
            });
        });
    }

    private static void fields(JsonNode node, String... allowed) {
        var names = Set.of(allowed);
        node.fieldNames().forEachRemaining(name -> { if (!names.contains(name)) fail("Nieobsługiwane pole security: " + name); });
    }

    private static void url(JsonNode node) {
        try {
            var uri = java.net.URI.create(node.asText());
            if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalArgumentException();
        } catch (RuntimeException failure) { fail("Nieprawidłowy URL w securityScheme."); }
    }
    private static void fail(String message) { throw new OpenApiException("/security: " + message); }
}
