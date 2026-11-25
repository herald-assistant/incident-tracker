package pl.mkn.tdw.integrations.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import pl.mkn.tdw.integrations.openapi.adapter.rest.OpenApiRestExecutor;
import pl.mkn.tdw.integrations.openapi.contract.*;
import pl.mkn.tdw.integrations.openapi.service.OpenApiImporter;
import pl.mkn.tdw.integrations.openapi.service.OpenApiSelectionService;
import pl.mkn.tdw.testsupport.integrations.IntegrationRestClientBuilderFactoryTestCreator;
import pl.mkn.tdw.testsupport.openapi.OpenApiFileFixtures;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class OpenApiFilesTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenApiImporter importer = new OpenApiImporter(mapper);

    @Test void importsAndSelectsExplicitFileMediaTypesInBothOpenApiVersions() {
        for (var version : List.of("3.0.3", "3.1.0")) {
            var yaml = OpenApiFileFixtures.yaml("http://example.invalid").replace("3.1.0", version);
            var selected = new OpenApiSelectionService(mapper, importer).select("files", yaml,
                    List.of(new OpenApiSelection.OperationKey("GET", "/file/{format}")));
            assertThat(importer.parse("files", selected.yaml()).get(0).responseContentTypes().get("200"))
                    .containsExactlyInAnyOrderElementsOf(OpenApiFile.EXTENSIONS.keySet());
            var referenced = yaml.replace("application/pdf: {schema: {type: string, format: binary}}",
                    "application/pdf: {schema: {$ref: '#/components/schemas/Download'}}")
                    + "\ncomponents:\n  schemas:\n    Download: {type: string, format: binary}\n";
            var withRefs = new OpenApiSelectionService(mapper, importer).select("files", referenced,
                    List.of(new OpenApiSelection.OperationKey("GET", "/file/{format}")));
            assertThat(importer.parse("files", withRefs.yaml()).get(0).responseContentTypes().get("200"))
                    .contains("application/pdf");
            assertThatThrownBy(() -> importer.parse("files", yaml.replace("format: binary", "format: byte")))
                    .hasMessageContaining("format: binary");
        }
    }

    @Test void returnsTypedBytesAndSafeNamesWithoutSerializingTheFileIntoJson() throws Exception {
        var mime = new AtomicReference<>("application/pdf");
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var bytes = new byte[]{1, 2, 3, 4, 5};
        server.createContext("/file", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Accept")).contains(mime.get());
            exchange.getResponseHeaders().set("Content-Type", mime.get());
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"../../document.pdf\"");
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var yaml = OpenApiFileFixtures.yaml("http://127.0.0.1:" + server.getAddress().getPort());
            var operation = importer.parse("files", yaml).get(0);
            var executor = new OpenApiRestExecutor(IntegrationRestClientBuilderFactoryTestCreator.create(RestClient.builder()), mapper, ignored -> null);
            for (var media : OpenApiFile.EXTENSIONS.keySet()) {
                mime.set(media);
                var result = executor.execute(new OpenApiExecution(operation, null),
                        new OpenApiArguments(mapper.readTree("{\"path\":{\"format\":\"test\"}}")));
                assertThat(result.failed()).isFalse();
                assertThat(result.file().content()).containsExactly(bytes);
                assertThat(result.file().mimeType()).isEqualTo(media);
                assertThat(result.file().name()).isEqualTo("document.pdf");
                assertThat(result.body().path("size").asInt()).isEqualTo(5);
                assertThat(mapper.writeValueAsString(result)).doesNotContain("AQIDBAU=", "\"file\"", "\"data\"", "encoding");
            }
        } finally { server.stop(0); }
    }

    @Test void distinguishesWildcardFilesDefaultJsonErrorsAndUndeclaredFileMediaTypes() throws Exception {
        var mime = new AtomicReference<>("application/pdf");
        var status = new AtomicInteger(200);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/file", exchange -> {
            var bytes = "{\"message\":\"not found\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", mime.get());
            exchange.sendResponseHeaders(status.get(), bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            var source = OpenApiFileFixtures.yaml("http://127.0.0.1:" + server.getAddress().getPort());
            var document = new OpenApiSelectionService(mapper, importer).preview("files", source).specification().deepCopy();
            var content = (com.fasterxml.jackson.databind.node.ObjectNode) document.at("/paths/~1file~1{format}/get/responses/200/content");
            content.removeAll().set("*/*", mapper.createObjectNode());
            var executor = new OpenApiRestExecutor(IntegrationRestClientBuilderFactoryTestCreator.create(RestClient.builder()), mapper, ignored -> null);
            var arguments = new OpenApiArguments(mapper.readTree("{\"path\":{\"format\":\"test\"}}"));
            var operation = importer.parse("files", document.toString()).get(0);
            var file = executor.execute(new OpenApiExecution(operation, null), arguments);
            assertThat(file.file().name()).isEqualTo("response.pdf");
            assertThat(file.body().has("data")).isFalse();

            status.set(404); mime.set("application/json");
            var error = executor.execute(new OpenApiExecution(operation, null), arguments);
            assertThat(error.statusCode()).isEqualTo(404); assertThat(error.file()).isNull();
            assertThat(error.body().path("message").asText()).isEqualTo("not found");

            content.removeAll().set("application/pdf", mapper.readTree("{\"schema\":{\"type\":\"string\",\"format\":\"binary\"}}"));
            status.set(200); mime.set("image/png");
            var mismatch = executor.execute(new OpenApiExecution(importer.parse("files", document.toString()).get(0), null), arguments);
            assertThat(mismatch.outcome()).isEqualTo("REQUEST_FAILED"); assertThat(mismatch.file()).isNull();
        } finally { server.stop(0); }
    }
}
