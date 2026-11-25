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

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.*;

class OpenApiBinaryResponsesTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenApiImporter importer = new OpenApiImporter(mapper);

    @Test void importsAndSelectsBinarySchemasAndReferencesInBothVersions() {
        for (var version : List.of("3.0.3", "3.1.0")) {
            var yaml = yaml("http://example.invalid").replace("3.1.0", version);
            var selected = new OpenApiSelectionService(mapper, importer).select("files", yaml,
                    List.of(new OpenApiSelection.OperationKey("GET", "/file")));
            assertThat(importer.parse("files", selected.yaml()).get(0).responseContentTypes().get("200"))
                    .containsExactly("application/octet-stream");
            var referenced = yaml.replace("{type: string, format: binary}", "{$ref: '#/components/schemas/Download'}")
                    + "\ncomponents:\n  schemas:\n    Download: {type: string, format: binary}\n";
            assertThat(new OpenApiSelectionService(mapper, importer).select("files", referenced,
                    List.of(new OpenApiSelection.OperationKey("GET", "/file"))).operationCount()).isEqualTo(1);
            for (var invalid : List.of("{type: string}", "{type: object}", "{type: string, format: byte}"))
                assertThatThrownBy(() -> importer.parse("files", yaml.replace("{type: string, format: binary}", invalid)))
                        .hasMessageContaining("format: binary");
        }
    }

    @Test void detectsEverySupportedFormatWithGenericOrMissingMimeAndUsesSafeNames() throws Exception {
        try (var endpoint = new Endpoint()) {
            var operation = importer.parse("files", yaml(endpoint.url())).get(0);
            for (var responseMime : new String[]{"application/octet-stream", null}) {
                endpoint.mime = responseMime;
                for (var mime : OpenApiFile.EXTENSIONS.keySet()) {
                    endpoint.bytes = file(mime);
                    endpoint.name = null;
                    var result = endpoint.execute(operation);
                    assertThat(result.outcome()).isEqualTo("RESPONSE_RECEIVED");
                    assertThat(result.statusCode()).isEqualTo(200);
                    assertThat(result.file().mimeType()).isEqualTo(mime);
                    assertThat(result.file().content()).containsExactly(endpoint.bytes);
                    assertThat(result.file().name()).isEqualTo("response." + OpenApiFile.EXTENSIONS.get(mime));
                    assertThat(result.body().path("size").asInt()).isEqualTo(endpoint.bytes.length);
                    assertThat(mapper.writeValueAsString(result)).doesNotContain("\"data\"", "\"encoding\"", "\"file\"");
                    assertThat(endpoint.accept).contains("application/octet-stream");
                }
            }
            endpoint.bytes = OpenApiFileFixtures.pdf("CRM-831");
            endpoint.name = "../../crm-document.pdf";
            assertThat(endpoint.execute(operation).file().name()).isEqualTo("crm-document.pdf");
        }
    }

    @Test void validatesConcreteMimeAgainstDetectedFormat() throws Exception {
        try (var endpoint = new Endpoint()) {
            var operation = importer.parse("files", yaml(endpoint.url())).get(0);
            endpoint.bytes = OpenApiFileFixtures.png("CRM-215");
            endpoint.mime = "image/png; charset=UTF-8";
            assertThat(endpoint.execute(operation).file().mimeType()).isEqualTo("image/png");
            for (var mismatch : List.of("application/pdf", "application/zip")) {
                endpoint.mime = mismatch;
                assertDeliveryFailed(endpoint.execute(operation));
            }
        }
    }

    @Test void rejectsUnknownTruncatedAndOrdinaryZipDataWithoutRetryingAMutation() throws Exception {
        try (var endpoint = new Endpoint()) {
            var operation = importer.parse("files", yaml(endpoint.url()).replace("    get:", "    post:")
                    .replace("READ_ONLY", "MUTATION")).get(0);
            var zip = new ByteArrayOutputStream();
            try (var archive = new ZipOutputStream(zip)) {
                archive.putNextEntry(new ZipEntry("crm.txt"));
                archive.write("CRM-831".getBytes(StandardCharsets.UTF_8));
                archive.closeEntry();
            }
            for (var invalid : List.of(new byte[0], new byte[]{'P', 'K', 3, 4, 1, 2, 3}, zip.toByteArray(),
                    new byte[]{(byte) 0x89, 'P', 'N', 'G'}, "unknown".getBytes(StandardCharsets.UTF_8))) {
                endpoint.bytes = invalid;
                endpoint.name = "pretend.pdf";
                var before = endpoint.calls.get();
                assertDeliveryFailed(endpoint.execute(operation));
                assertThat(endpoint.calls.get()).isEqualTo(before + 1);
            }
        }
    }

    @Test void honorsResponseStatusAndDefaultAndKeepsNoContentResponsesEmpty() throws Exception {
        try (var endpoint = new Endpoint()) {
            endpoint.bytes = OpenApiFileFixtures.pdf("CRM-682");
            endpoint.status = 201;
            var operation = importer.parse("files", yaml(endpoint.url()).replace("'200':", "'201':")).get(0);
            assertThat(endpoint.execute(operation).file().mimeType()).isEqualTo("application/pdf");
            var fallback = yaml(endpoint.url()).replace("'200':", "default:")
                    .replace("        default: {description: Błąd, content: {application/json: {schema: {}}}}\n", "");
            operation = importer.parse("files", fallback).get(0);
            assertThat(endpoint.execute(operation).file().mimeType()).isEqualTo("application/pdf");
            for (var status : List.of(204, 205)) {
                endpoint.status = status;
                var result = endpoint.execute(operation);
                assertThat(result.outcome()).isEqualTo("RESPONSE_RECEIVED");
                assertThat(result.statusCode()).isEqualTo(status);
                assertThat(result.file()).isNull();
                assertThat(result.body().isNull()).isTrue();
            }
            endpoint.status = 404;
            endpoint.mime = "application/json";
            endpoint.bytes = "{\"message\":\"CRM document missing\"}".getBytes(StandardCharsets.UTF_8);
            var result = endpoint.execute(importer.parse("files", yaml(endpoint.url())).get(0));
            assertThat(result.statusCode()).isEqualTo(404);
            assertThat(result.body().path("message").asText()).isEqualTo("CRM document missing");
            assertThat(result.file()).isNull();
        }
    }

    @Test void preservesJsonTextWildcardAndConcreteFileDeclarations() throws Exception {
        try (var endpoint = new Endpoint()) {
            var yaml = yaml(endpoint.url()).replace("        default:",
                    "            application/json: {schema: {}}\n            text/plain: {schema: {type: string}}\n        default:");
            var operation = importer.parse("files", yaml).get(0);
            endpoint.mime = "application/json";
            endpoint.bytes = "{\"code\":\"CRM-831\"}".getBytes(StandardCharsets.UTF_8);
            assertThat(endpoint.execute(operation).body().path("code").asText()).isEqualTo("CRM-831");
            endpoint.mime = "text/plain";
            endpoint.bytes = "CRM-682".getBytes(StandardCharsets.UTF_8);
            assertThat(endpoint.execute(operation).body().asText()).isEqualTo("CRM-682");
            endpoint.bytes = OpenApiFileFixtures.pdf("CRM-215");
            for (var mime : new String[]{"application/octet-stream", null}) {
                endpoint.mime = mime;
                var wildcard = yaml(endpoint.url()).replace("application/octet-stream: {schema: {type: string, format: binary}}", "\"*/*\": {}");
                var result = endpoint.execute(importer.parse("files", wildcard).get(0));
                assertThat(result.file()).isNull();
                assertThat(result.body().path("encoding").asText()).isEqualTo("base64");
                var concrete = yaml(endpoint.url()).replace("application/octet-stream", "application/pdf");
                assertThat(endpoint.execute(importer.parse("files", concrete).get(0)).outcome()).isEqualTo("REQUEST_FAILED");
            }
        }
    }

    private void assertDeliveryFailed(OpenApiResult result) throws Exception {
        assertThat(result.outcome()).isEqualTo("RESPONSE_RECEIVED");
        assertThat(result.statusCode()).isEqualTo(200);
        assertThat(result.file()).isNull();
        assertThat(result.body().path("fileDelivery").asText()).isEqualTo("FAILED");
        assertThat(mapper.writeValueAsString(result)).doesNotContain("\"data\"", "\"encoding\"");
    }

    private byte[] file(String mime) throws Exception {
        if (mime.equals("application/pdf")) return OpenApiFileFixtures.pdf("CRM-831");
        if (mime.endsWith("wordprocessingml.document")) return OpenApiFileFixtures.docx("CRM-682");
        // WebP is not encoded by JDK ImageIO; this header exercises format identification only.
        if (mime.equals("image/webp")) return new byte[]{'R', 'I', 'F', 'F', 12, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', 'L', 0, 0, 0, 0};
        try (var bytes = new ByteArrayOutputStream()) {
            assertThat(ImageIO.write(OpenApiFileFixtures.image("CRM-215"), OpenApiFile.EXTENSIONS.get(mime), bytes)).isTrue();
            return bytes.toByteArray();
        }
    }

    private static String yaml(String url) {
        return """
                openapi: 3.1.0
                info: {title: CRM files, version: '1'}
                servers: [{url: '%s'}]
                paths:
                  /file:
                    get:
                      operationId: download
                      x-tdw-operation-effect: READ_ONLY
                      responses:
                        '200':
                          description: Plik
                          content:
                            application/octet-stream: {schema: {type: string, format: binary}}
                        default: {description: Błąd, content: {application/json: {schema: {}}}}
                """.formatted(url);
    }

    private class Endpoint implements AutoCloseable {
        final HttpServer server;
        final AtomicInteger calls = new AtomicInteger();
        final OpenApiRestExecutor executor = new OpenApiRestExecutor(
                IntegrationRestClientBuilderFactoryTestCreator.create(RestClient.builder()), mapper, ignored -> null);
        volatile String mime = "application/octet-stream";
        volatile String name;
        volatile String accept;
        volatile byte[] bytes = new byte[0];
        volatile int status = 200;

        Endpoint() throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/file", exchange -> {
                calls.incrementAndGet();
                accept = exchange.getRequestHeaders().getFirst("Accept");
                if (mime != null) exchange.getResponseHeaders().set("Content-Type", mime);
                if (name != null) exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + name + "\"");
                exchange.sendResponseHeaders(status, status == 204 || status == 205 || bytes.length == 0 ? -1 : bytes.length);
                if (status != 204 && status != 205 && bytes.length > 0) exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            server.start();
        }

        String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        OpenApiResult execute(OpenApiOperation operation) {
            return executor.execute(new OpenApiExecution(operation, null), new OpenApiArguments(mapper.createObjectNode()));
        }
        @Override public void close() { server.stop(0); }
    }
}
