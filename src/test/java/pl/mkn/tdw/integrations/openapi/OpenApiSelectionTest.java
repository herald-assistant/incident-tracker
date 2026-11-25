package pl.mkn.tdw.integrations.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiException;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiSelection.OperationKey;
import pl.mkn.tdw.integrations.openapi.service.OpenApiImporter;
import pl.mkn.tdw.integrations.openapi.service.OpenApiSelectionService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class OpenApiSelectionTest {
    @Test void exposesOnlyAValidatedReadOnlyDocument() throws Exception {
        var yaml = Files.readString(Path.of("src/test/resources/openapi/crm-api.yaml"));
        var document = selection.document("crm", yaml);
        assertThat(document.specification().path("paths").size()).isEqualTo(2);
        ((ObjectNode) document.specification()).remove("paths");
        assertThat(document.specification().has("paths")).isTrue();
        assertThat(selection.document("crm", yaml.replace("127.0.0.1", "example.invalid")).specification()
                .path("servers").get(0).path("url").asText()).contains("example.invalid");
    }
    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenApiImporter importer = new OpenApiImporter(mapper);
    private final OpenApiSelectionService selection = new OpenApiSelectionService(mapper, importer);
    private ObjectNode fixture() throws Exception {
        return mapper.valueToTree(new Yaml(new SafeConstructor(new LoaderOptions()))
                .load(Files.readString(Path.of("src/test/resources/openapi/crm-api.yaml"))));
    }
    @Test void extractsOneOperationFromLargeDocumentAndClosesOnlyItsReferences() throws Exception {
        var document = fixture();
        var paths = (ObjectNode) document.get("paths");
        for (var index = 0; index < 300; index++) {
            var operation = paths.putObject("/unused/" + index).putObject("get");
            operation.put("operationId", "unused" + index).put("description", "Large source ".repeat(60));
            operation.putObject("callbacks"); // Unsupported, but never selected.
        }
        var schemas = (ObjectNode) document.path("components").path("schemas");
        ((ObjectNode) schemas.path("Ticket").path("properties")).putObject("address").put("$ref", "#/components/schemas/Address");
        schemas.putObject("Address").put("type", "object").putObject("properties").putObject("city").put("type", "string");
        schemas.putObject("NeverUsed").put("$ref", "https://example.invalid/external.yaml");
        var yaml = document.toString();
        assertThat(yaml.length()).isGreaterThan(100_000);
        var preview = selection.preview("crm", yaml);
        assertThat(preview.operations()).hasSize(303);
        assertThat(preview.specification().path("paths").size()).isEqualTo(302);
        assertThat(preview.specification().path("components").path("schemas").has("NeverUsed")).isTrue();
        ((ObjectNode) preview.specification()).remove("paths");
        assertThat(preview.specification().has("paths")).isTrue();
        var prepared = selection.select("crm", yaml, List.of(new OperationKey("GET", "/tickets/{id}")));
        assertThat(prepared.operationCount()).isEqualTo(1);
        assertThat(prepared.yaml()).contains("getTicket", "Address", "#/components/schemas/Ticket")
                .doesNotContain("updateTicket", "simulateCost", "unused", "NeverUsed", "example.invalid");
        var operations = importer.parse("crm", prepared.yaml());
        assertThat(operations).hasSize(1);
        assertThat(operations.get(0).parameters()).containsExactly(new pl.mkn.tdw.integrations.openapi.contract.OpenApiOperation.Parameter("id", "path", false, false, false));
    }
    @Test void retainsSharedPathParametersAndSelectedMethodsWithoutExtraEndpoints() throws Exception {
        var prepared = selection.select("crm", fixture().toString(), List.of(
                new OperationKey("GET", "/tickets/{id}"), new OperationKey("PUT", "/tickets/{id}")));
        assertThat(prepared.operationCount()).isEqualTo(2);
        assertThat(importer.parse("crm", prepared.yaml())).extracting(operation -> operation.method()).containsExactly("GET", "PUT");
        assertThat(prepared.yaml()).contains("parameters:", "required:", "Ticket:").doesNotContain("/simulate", "simulateCost");
    }
    @Test void rejectsUnknownDuplicateUnsupportedAndInvalidSelectedOperations() throws Exception {
        var document = fixture();
        var key = new OperationKey("GET", "/tickets/{id}");
        for (var keys : List.of(List.<OperationKey>of(), List.of(key, key), List.of(new OperationKey("GET", "/missing"))))
            assertThatThrownBy(() -> selection.select("crm", document.toString(), keys)).isInstanceOf(OpenApiException.class);
        ((ObjectNode) document.path("paths").path("/tickets/{id}")).set("head", document.path("paths").path("/tickets/{id}").get("put"));
        assertThat(selection.preview("crm", document.toString()).operations()).anySatisfy(candidate -> {
            assertThat(candidate.method()).isEqualTo("HEAD"); assertThat(candidate.unavailableReason()).isNotBlank();
        });
        assertThatThrownBy(() -> selection.select("crm", document.toString(), List.of(new OperationKey("HEAD", "/tickets/{id}"))))
                .isInstanceOf(OpenApiException.class);
        ((ObjectNode) document.path("components").path("schemas").path("Ticket").path("properties").path("title")).put("nullable", "wrong");
        assertThatThrownBy(() -> selection.select("crm", document.toString(), List.of(key))).isInstanceOf(OpenApiException.class);
        assertThatThrownBy(() -> selection.preview("crm", "openapi: 3.0.3\nopenapi: 3.0.2")).isInstanceOf(OpenApiException.class);
    }
    @Test void rejectsUnresolvedExternalOrCyclicDependenciesOfTheSelection() throws Exception {
        for (var ref : List.of("#/components/schemas/Missing", "https://example.invalid/schema", "#/components/schemas/Ticket")) {
            var document = fixture();
            ((ObjectNode) document.path("components").path("schemas")).set("Ticket", mapper.createObjectNode().put("$ref", ref));
            assertThatThrownBy(() -> selection.select("crm", document.toString(), List.of(new OperationKey("GET", "/tickets/{id}"))))
                    .isInstanceOf(OpenApiException.class);
        }
    }
}
