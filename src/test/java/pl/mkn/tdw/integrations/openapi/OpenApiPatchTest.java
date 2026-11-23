package pl.mkn.tdw.integrations.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.mkn.tdw.integrations.openapi.adapter.rest.OpenApiRestExecutor;
import pl.mkn.tdw.integrations.openapi.contract.*;
import pl.mkn.tdw.integrations.openapi.service.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.assertj.core.api.Assertions.*;

class OpenApiPatchTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenApiImporter importer = new OpenApiImporter(mapper);
    private final OpenApiSelectionService selection = new OpenApiSelectionService(mapper, importer);
    private String source() throws Exception { return Files.readString(Path.of("src/test/resources/openapi/patch-api.yaml")).replace("\r\n", "\n"); }

    @Test void selectsPatchWithItsOwnSecurityAndRejectsAnUnknownScheme() throws Exception {
        var source = source();
        var preview = selection.preview("items", source);
        assertThat(preview.operations().stream().filter(op -> op.method().equals("PATCH"))).hasSize(2)
                .allSatisfy(op -> assertThat(op.unavailableReason()).isNull());
        var prepared = selection.select("items", source, List.of(new OpenApiSelection.OperationKey("PATCH", "/items/{id}")));
        assertThat(prepared.operationCount()).isEqualTo(1);
        var document = selection.document("items", prepared.yaml()).specification();
        assertThat(document.path("paths").path("/items/{id}").has("get")).isFalse();
        assertThat(document.path("components").path("securitySchemes").has("bearerAuth")).isTrue();
        assertThat(document.path("components").path("securitySchemes").has("unused")).isFalse();
        assertThat(importer.parse("items", prepared.yaml()).get(0).method()).isEqualTo("PATCH");
        assertThatThrownBy(() -> selection.select("items", source.replace("security: [{bearerAuth: []}]", "security: [{missing: []}]"),
                List.of(new OpenApiSelection.OperationKey("PATCH", "/items/{id}")))).isInstanceOf(OpenApiException.class).hasMessageContaining("missing");
        assertThatThrownBy(() -> importer.parse("items", source.replace("security: [{bearerAuth: []}]", "security: [{missing: []}]")))
                .isInstanceOf(OpenApiException.class).hasMessageContaining("missing");
    }

    @Test void sendsActualPatchPartialJsonNullAndAuthenticationAndValidatesBeforeHttp() throws Exception {
        var calls = new AtomicInteger();
        var method = new AtomicReference<String>(); var body = new AtomicReference<String>();
        var contentType = new AtomicReference<String>(); var auth = new AtomicReference<String>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/items/7", exchange -> {
            calls.incrementAndGet(); method.set(exchange.getRequestMethod());
            body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
            exchange.sendResponseHeaders(204, -1); exchange.close();
        });
        server.start();
        try {
            var source = source().replace(":8099", ":" + server.getAddress().getPort());
            var operation = importer.parse("items", source).stream().filter(op -> op.name().endsWith("_patchItem")).findFirst().orElseThrow();
            var executor = new OpenApiRestExecutor(RestClient.builder(), mapper, profile -> "Bearer patch-test-token");
            var arguments = new OpenApiArguments(mapper.readTree("{\"path\":{\"id\":7},\"body\":{\"note\":\"Zażółć\"}}"));
            assertThat(executor.execute(new OpenApiExecution(operation, null), arguments).statusCode()).isEqualTo(204);
            assertThat(method).hasValue("PATCH"); assertThat(auth).hasValue("Bearer patch-test-token");
            assertThat(contentType.get()).startsWith("application/json");
            assertThat(mapper.readTree(body.get())).isEqualTo(arguments.json().get("body"));
            assertThat(mapper.readTree(body.get()).has("status")).isFalse();
            executor.execute(new OpenApiExecution(operation, null), new OpenApiArguments(mapper.readTree("{\"path\":{\"id\":7},\"body\":{\"note\":null}}")));
            assertThat(mapper.readTree(body.get()).get("note").isNull()).isTrue();
            for (var invalid : List.of("{\"path\":{\"id\":7}}", "{\"path\":{\"id\":7},\"body\":{}}", "{\"path\":{\"id\":7},\"body\":{\"note\":1}}"))
                assertThatThrownBy(() -> executor.execute(new OpenApiExecution(operation, null), new OpenApiArguments(mapper.readTree(invalid)))).isInstanceOf(OpenApiException.class);
            assertThat(calls).hasValue(2);
            var optional = importer.parse("items", source.replace("required: true\n        content:", "required: false\n        content:")).stream()
                    .filter(op -> op.name().endsWith("_patchItem")).findFirst().orElseThrow();
            executor.execute(new OpenApiExecution(optional, null), new OpenApiArguments(mapper.readTree("{\"path\":{\"id\":7}}")));
            assertThat(body).hasValue(""); assertThat(contentType.get()).isNull();
            var noBody = importer.parse("items", source).stream().filter(op -> op.name().endsWith("_refreshItem")).findFirst().orElseThrow();
            executor.execute(new OpenApiExecution(noBody, null), new OpenApiArguments(mapper.readTree("{\"path\":{\"id\":7}}")));
            assertThat(body).hasValue(""); assertThat(contentType.get()).isNull();
            assertThat(noBody.mutation()).isTrue(); assertThat(noBody.effect()).isEqualTo("UNKNOWN");
        } finally { server.stop(0); }
    }

    @Test void supportsJsonPatchOperationsOnlyWhenTheirDeclaredMediaTypeIsSupported() throws Exception {
        for (var media : List.of("application/json-patch+json", "application/merge-patch+json", "multipart/form-data"))
            assertThatThrownBy(() -> importer.parse("items", source().replace("application/json:", media + ":"))).isInstanceOf(OpenApiException.class);
    }
}
