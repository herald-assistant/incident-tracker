package pl.mkn.tdw.integrations.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.mkn.tdw.integrations.httpauth.adapter.rest.HttpAuthProtocolClient;
import pl.mkn.tdw.integrations.httpauth.contract.HttpAuthProfile;
import pl.mkn.tdw.integrations.httpauth.service.HttpAuthenticationService;
import pl.mkn.tdw.integrations.openapi.adapter.rest.OpenApiRestExecutor;
import pl.mkn.tdw.integrations.openapi.contract.*;
import pl.mkn.tdw.integrations.openapi.service.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

class OpenApiAuthenticationTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void sendsOnlySelectedCredentialsAndObtainsTokensBeforeBusinessRequests() throws Exception {
        var headers = new java.util.ArrayList<String>();
        var tokenCalls = new AtomicInteger();
        var tokenAuthorization = new AtomicReference<String>();
        var tokenForm = new AtomicReference<String>();
        var expires = new AtomicInteger(3600);
        var fail = new AtomicBoolean();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api", exchange -> {
            headers.add(exchange.getRequestHeaders().getFirst("Authorization"));
            var body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.createContext("/token", exchange -> {
            var count = tokenCalls.incrementAndGet();
            tokenAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            tokenForm.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            var body = (fail.get() ? "{\"error\":\"invalid_client\"}" :
                    "{\"access_token\":\"crm-token-" + count + "\",\"token_type\":\"Bearer\",\"expires_in\":" + expires.get() + "}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(fail.get() ? 400 : 200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        server.start();
        try {
            var base = "http://127.0.0.1:" + server.getAddress().getPort();
            var protocol = new HttpAuthProtocolClient(RestClient.builder());
            var auth = new HttpAuthenticationService(protocol, new pl.mkn.tdw.integrations.httpauth.service.OidcLoginService(protocol, java.time.Clock.systemUTC()));
            var executor = new OpenApiRestExecutor(RestClient.builder(), mapper, auth);
            var operation = new OpenApiOperation("oa_crm_read", "CRM", "POST", java.net.URI.create(base), "/api", List.of(),
                    mapper.readTree("{\"type\":\"object\"}"), mapper.readTree("{\"type\":\"object\"}"), "UNKNOWN", java.util.Map.of());
            var arguments = new OpenApiArguments(mapper.createObjectNode());
            assertThat(executor.execute(new OpenApiExecution(operation, basic()), arguments).statusCode()).isEqualTo(200);
            assertThat(executor.execute(new OpenApiExecution(operation, bearer()), arguments).statusCode()).isEqualTo(200);
            assertThat(executor.execute(new OpenApiExecution(operation, null), arguments).statusCode()).isEqualTo(200);
            var oauth = oauth(base + "/token", "client_secret_basic", "client-one");
            executor.execute(new OpenApiExecution(operation, oauth), arguments); executor.execute(new OpenApiExecution(operation, oauth), arguments);
            assertThat(tokenCalls).hasValue(1);
            assertThat(headers).containsExactly("Basic b3BlcmF0b3I6cHVibGljLXBhc3M=", "Bearer public-token", null, "Bearer crm-token-1", "Bearer crm-token-1");
            assertThat(tokenAuthorization.get()).startsWith("Basic ");
            assertThat(tokenForm.get()).contains("grant_type=client_credentials", "scope=crm.read").doesNotContain("client_secret");
            expires.set(1);
            var post = oauth(base + "/token", "client_secret_post", "client-two");
            executor.execute(new OpenApiExecution(operation, post), arguments); executor.execute(new OpenApiExecution(operation, post), arguments);
            assertThat(tokenCalls).hasValue(3);
            assertThat(tokenAuthorization.get()).isNull();
            assertThat(tokenForm.get()).contains("client_id=client-two", "client_secret=public-secret");
            fail.set(true);
            assertThat(executor.execute(new OpenApiExecution(operation, oauth(base + "/token", "client_secret_basic", "broken")), arguments).outcome()).isEqualTo("AUTH_REQUIRED");
            assertThat(headers).hasSize(7);
        } finally { server.stop(0); }
    }

    @Test void selectsSecuritySchemesByNameAndPreservesDocumentationWithoutEnablingAuth() {
        var yaml = """
                openapi: 3.0.3
                servers: [{url: https://crm.example.com}]
                security: [{crmLogin: []}]
                paths:
                  /api:
                    get:
                      operationId: read
                      responses: {'200': {description: OK}}
                components:
                  securitySchemes:
                    crmLogin: {type: http, scheme: bearer}
                    unused: {type: http, scheme: basic}
                """;
        var importer = new OpenApiImporter(mapper);
        var selection = new OpenApiSelectionService(mapper, importer);
        var prepared = selection.select("crm", yaml, List.of(new OpenApiSelection.OperationKey("GET", "/api")));
        assertThat(prepared.yaml()).contains("crmLogin", "securitySchemes").doesNotContain("unused");
        assertThat(importer.parse("crm", prepared.yaml())).hasSize(1);
        assertThatThrownBy(() -> importer.parse("crm", yaml.replace("scheme: bearer", "scheme: digest"))).isInstanceOf(OpenApiException.class);
    }

    private HttpAuthProfile basic() { return new HttpAuthProfile("basic", "CRM", "BASIC", "operator", "public-pass", null, null, null, null, null, null, null, null); }
    private HttpAuthProfile bearer() { return new HttpAuthProfile("bearer", "CRM", "BEARER", null, null, "public-token", null, null, null, null, null, null, null); }
    private HttpAuthProfile oauth(String url, String method, String client) { return new HttpAuthProfile("oauth", "CRM", "OAUTH2", null, null, null, url, client, "public-secret", "crm.read", null, null, method); }
}
