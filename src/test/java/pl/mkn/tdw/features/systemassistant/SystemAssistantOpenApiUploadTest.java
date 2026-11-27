package pl.mkn.tdw.features.systemassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.tomcat.util.http.fileupload.FileUpload;
import org.apache.tomcat.util.http.fileupload.UploadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.client.RestClient;
import pl.mkn.tdw.agenttools.openapi.OpenApiToolSetFactory;
import pl.mkn.tdw.integrations.httpauth.contract.HttpAuthProfile;
import pl.mkn.tdw.integrations.httpauth.service.HttpAuthenticationService;
import pl.mkn.tdw.integrations.openapi.adapter.rest.OpenApiRestExecutor;
import pl.mkn.tdw.integrations.openapi.contract.*;
import pl.mkn.tdw.integrations.openapi.service.OpenApiImporter;
import pl.mkn.tdw.integrations.openapi.service.OpenApiSelectionService;
import pl.mkn.tdw.testsupport.integrations.IntegrationRestClientBuilderFactoryTestCreator;
import pl.mkn.tdw.testsupport.openapi.OpenApiFileFixtures;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class SystemAssistantOpenApiUploadTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenApiImporter importer = new OpenApiImporter(mapper);
    private final OpenApiSelectionService selection = new OpenApiSelectionService(mapper, importer);
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicBoolean disconnect = new AtomicBoolean();
    private HttpServer server;
    private volatile List<ReceivedPart> parts;
    private volatile String target;
    private volatile String authorization;
    private Map<String, OpenApiFile> files;

    record ReceivedPart(String name, String filename, String mime, byte[] bytes) {}

    @BeforeEach void startReceiver() throws Exception {
        files = Map.of("crm-offer", new OpenApiFile("oferta główna.pdf", "application/pdf", OpenApiFileFixtures.pdf("CRM-1")),
                "crm-cover", new OpenApiFile("okładka.bmp", "image/bmp", OpenApiFileFixtures.bmp("CRM-2")));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try (exchange) {
                calls.incrementAndGet();
                target = exchange.getRequestMethod() + " " + exchange.getRequestURI();
                authorization = exchange.getRequestHeaders().getFirst("Authorization");
                var mime = exchange.getRequestHeaders().getFirst("Content-Type");
                if (mime.startsWith("multipart/form-data")) parts = parseMultipart(exchange, mime);
                else parts = List.of(new ReceivedPart(null, null, mime, exchange.getRequestBody().readAllBytes()));
                if (disconnect.get()) return;
                var result = mapper.writeValueAsBytes(Map.of("receipt", "CRM-314"));
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(201, result.length);
                exchange.getResponseBody().write(result);
            }
        });
        server.start();
    }

    @AfterEach void stopReceiver() { server.stop(0); }

    private static List<ReceivedPart> parseMultipart(HttpExchange exchange, String mime) throws java.io.IOException {
        var context = new UploadContext() {
            public String getContentType() { return mime; }
            public String getCharacterEncoding() { return "UTF-8"; }
            public long contentLength() { return -1; }
            public InputStream getInputStream() { return exchange.getRequestBody(); }
        };
        var list = new ArrayList<ReceivedPart>();
        var iterator = new FileUpload().getItemIterator(context);
        while (iterator.hasNext()) {
            var item = iterator.next();
            list.add(new ReceivedPart(item.getFieldName(), item.getName(), item.getContentType(), item.openStream().readAllBytes()));
        }
        return list;
    }

    private String source(String fixture) throws Exception {
        return Files.readString(Path.of("src/test/resources/openapi/" + fixture + ".yaml"))
                .replace(":8099", ":" + server.getAddress().getPort()).replace("\r\n", "\n");
    }

    private OpenApiOperation selected(String source, String method, String path) {
        var preview = selection.preview("crm", source).specification().path("paths").get(path);
        var selected = selection.select("crm", source, List.of(new OpenApiSelection.OperationKey(method, path)));
        var swagger = selection.document("crm", selected.yaml()).specification().path("paths").get(path);
        assertThat(swagger).isNotNull().isEqualTo(preview);
        assertThat(swagger.toString()).doesNotContain("attachmentId");
        return importer.parse("crm", selected.yaml()).get(0);
    }

    private ToolCallback callback(OpenApiOperation operation, Map<String, OpenApiFile> available) {
        var auth = new HttpAuthenticationService(null, null);
        var rest = new OpenApiRestExecutor(IntegrationRestClientBuilderFactoryTestCreator.create(RestClient.builder()), mapper, auth);
        var bearer = new HttpAuthProfile("crm", "Demo", "BEARER", null, null, "fixture-token", null, null, null, null, null, null, null);
        return new OpenApiToolSetFactory(rest, mapper).create(List.of(new OpenApiExecution(operation, bearer)), new AtomicBoolean(), available).get(0);
    }

    private static Map<String, String> ref(String id) { return Map.of("attachmentId", id); }

    private void assertOriginal(ReceivedPart part, String expectedName, String id, String mime) {
        var file = files.get(id);
        assertThat(part.name()).isEqualTo(expectedName);
        assertThat(part.filename()).isEqualTo(file.name());
        assertThat(part.mime()).isEqualTo(mime);
        assertThat(part.bytes()).isEqualTo(file.content());
    }

    @Test void sendsOriginalPdfAndStructuredMetadataWithContractFieldNames() throws Exception {
        var operation = selected(source("crm-folder-upload"), "POST", "/folders/entries");
        var body = operation.inputSchema().at("/properties/body/properties");
        assertThat(body.path("original").at("/properties/attachmentId/type").asText()).isEqualTo("string");
        assertThat(body.path("labels").path("additionalProperties").isObject()).isTrue();
        var labels = Map.of("crm:tags", List.of("nowa", "oferta"), "attachmentId", "ordinary-metadata");
        var tool = callback(operation, files);
        var result = tool.call(mapper.writeValueAsString(Map.of("body", Map.of("folder", "CRM-1", "labels", labels, "original", ref("crm-offer")))));
        assertThat(mapper.readTree(result).at("/body/receipt").asText()).isEqualTo("CRM-314");
        assertThat(calls).hasValue(1);
        assertThat(authorization).isEqualTo("Bearer fixture-token");
        assertThat(target).isEqualTo("POST /folders/entries");
        assertThat(parts).extracting(ReceivedPart::name).containsExactlyInAnyOrder("folder", "original", "labels");
        for (var part : parts) {
            switch (part.name()) {
                case "folder" -> {
                    assertThat(part.mime()).isEqualTo("text/plain");
                    assertThat(new String(part.bytes(), StandardCharsets.UTF_8)).isEqualTo("CRM-1");
                }
                case "original" -> assertOriginal(part, "original", "crm-offer", "application/octet-stream");
                case "labels" -> {
                    assertThat(part.mime()).isEqualTo("application/json");
                    assertThat(mapper.readTree(part.bytes())).isEqualTo(mapper.valueToTree(labels));
                }
            }
        }
    }

    @Test void sendsNamedBitmapAndRepeatedFilePartsInOrderAndValidatesAllPartsBeforeSending() throws Exception {
        var operation = selected(source("crm-gallery-upload"), "PUT", "/gallery/{id}");
        var tool = callback(operation, files);
        var args = mapper.valueToTree(Map.of("path", Map.of("id", "CRM-2"), "body", Map.of("cover", ref("crm-cover"),
                "pages", List.of(ref("crm-offer"), ref("crm-cover")), "approved", true, "revision", 2)));
        var invalid = args.deepCopy();
        invalid.withObject("/body").put("checksum", "readOnly");
        assertThatThrownBy(() -> tool.call(invalid.toString())).isInstanceOf(OpenApiException.class);
        invalid.withObject("/body").remove("checksum");
        invalid.withObject("/body").set("cover", mapper.valueToTree(ref("crm-offer")));
        assertThatThrownBy(() -> tool.call(invalid.toString())).hasMessageContaining("MIME");
        invalid.withObject("/body").remove("pages");
        assertThatThrownBy(() -> tool.call(invalid.toString())).isInstanceOf(OpenApiException.class);
        assertThatThrownBy(() -> callback(operation, Map.of("crm-cover", files.get("crm-cover"))).call(args.toString()))
                .hasMessageContaining("dostępny");
        assertThat(calls).hasValue(0);
        assertThat(tool.call(args.toString())).contains("CRM-314");
        assertThat(target).isEqualTo("PUT /gallery/CRM-2");
        assertThat(calls).hasValue(1);
        assertThat(parts).hasSize(5);
        var pages = parts.stream().filter(p -> p.name().equals("pages")).toList();
        assertOriginal(pages.get(0), "pages", "crm-offer", "application/octet-stream");
        assertOriginal(pages.get(1), "pages", "crm-cover", "application/octet-stream");
        assertOriginal(parts.stream().filter(p -> p.name().equals("cover")).findFirst().orElseThrow(), "cover", "crm-cover", "image/bmp");
        assertThat(parts).filteredOn(p -> p.name().equals("approved") || p.name().equals("revision"))
                .extracting(p -> new String(p.bytes(), StandardCharsets.UTF_8)).containsExactlyInAnyOrder("true", "2");
    }

    @Test void choosesDeclaredRawMimePreservesLargeOriginalAndBlocksWritesAfterLostResponse() throws Exception {
        var source = source("crm-file-upload");
        var operation = selected(source, "POST", "/files");
        var tool = callback(operation, files);
        var pdf = mapper.writeValueAsString(Map.of("body", ref("crm-offer")));
        var bmp = mapper.writeValueAsString(Map.of("body", ref("crm-cover")));
        var pdfOnly = selected(source.replace("          application/octet-stream: {}", ""), "POST", "/files");
        assertThatThrownBy(() -> callback(pdfOnly, files).call(bmp)).hasMessageContaining("MIME");
        assertThat(calls).hasValue(0);
        assertThat(tool.call(pdf)).contains("CRM-314");
        assertThat(parts.get(0).bytes()).isEqualTo(files.get("crm-offer").content());
        assertThat(parts.get(0).mime()).isEqualTo("application/pdf");
        disconnect.set(true);
        assertThat(tool.call(bmp)).contains("OUTCOME_UNKNOWN");
        assertThat(parts.get(0).mime()).isEqualTo("application/octet-stream");
        assertThat(parts.get(0).bytes()).hasSizeGreaterThan(65_536).isEqualTo(files.get("crm-cover").content());
        assertThat(tool.call(pdf)).contains("WRITE_BLOCKED");
        assertThat(calls).hasValue(2);
    }

    @Test void rejectsUnsupportedEncodingsAndAmbiguousWireTypesAtImport() throws Exception {
        var gallery = source("crm-gallery-upload");
        assertThatThrownBy(() -> importer.parse("crm", gallery.replace("{contentType: image/bmp}", "{headers: {}}")))
                .hasMessageContaining("encoding/cover/headers");
        assertThatThrownBy(() -> importer.parse("crm", gallery.replace("cover: {}", "cover: {oneOf: [{type: string}, {}]}")))
                .hasMessageContaining("properties/cover");
        assertThatThrownBy(() -> importer.parse("crm", gallery.replace("          multipart/form-data:", "          application/json: {schema: {}}\n          multipart/form-data:")))
                .hasMessageContaining("requestBody/content");
    }

    @Test void sendsOptionalPartsOnlyWhenSuppliedAndKeepsBytesOutOfSerializedArguments() throws Exception {
        var operation = selected(source("crm-gallery-upload"), "PUT", "/gallery/{id}");
        var args = mapper.valueToTree(Map.of("path", Map.of("id", "CRM-3"),
                "body", Map.of("cover", ref("crm-cover"), "pages", List.of(ref("crm-cover")))));
        var resolved = operation.upload().bind(args, files);
        assertThat(resolved.files()).containsOnlyKeys("crm-cover");
        assertThat(mapper.readTree(mapper.writeValueAsBytes(resolved)).size()).isEqualTo(1);
        assertThat(callback(operation, files).call(args.toString())).contains("CRM-314");
        assertThat(parts).extracting(ReceivedPart::name).containsExactlyInAnyOrder("cover", "pages");
    }
}
