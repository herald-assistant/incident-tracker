package pl.mkn.tdw.integrations.openapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiException;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiSelection.OperationKey;
import pl.mkn.tdw.integrations.openapi.service.OpenApiImporter;
import pl.mkn.tdw.integrations.openapi.service.OpenApiSelectionService;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class OpenApiToolNamesTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final OpenApiImporter importer = new OpenApiImporter(mapper);
    private final OpenApiSelectionService selection = new OpenApiSelectionService(mapper, importer);

    private ObjectNode source(String... ids) {
        var root = mapper.createObjectNode().put("openapi", "3.1.0");
        root.putObject("info").put("title", "Names").put("version", "1");
        root.putArray("servers").addObject().put("url", "https://example.invalid");
        var paths = root.putObject("paths");
        for (int index = 0; index < ids.length; index++) {
            var op = paths.putObject("/items/" + index).putObject("get");
            op.put("operationId", ids[index]).put("summary", "Odczytaj produkt");
            op.putObject("responses").putObject("204").put("description", "OK");
        }
        return root;
    }

    @Test void keepsNamesAt64AndShortens65WithoutChangingTheOperationContract() {
        var id64 = "a".repeat(57); // oa_crm_ has seven characters.
        var id65 = id64 + "b";
        var shortName = importer.parse("crm", source(id64).toString()).get(0).name();
        var operation = importer.parse("crm", source(id65).toString()).get(0);
        assertThat(shortName).isEqualTo("oa_crm_" + id64).hasSize(64);
        assertThat(operation.name()).hasSize(64).matches("oa_[a-zA-Z0-9_]+")
                .startsWith(("oa_crm_" + id65).substring(0, 47)).matches(".*_[0-9a-f]{16}");
        assertThat(operation.description()).contains("operationId: " + id65);
        assertThat(operation.method()).isEqualTo("GET");
        assertThat(operation.path()).isEqualTo("/items/0");
    }

    @Test void preservesLongIdentifiersAndNamesAcrossReorderSelectionAndReload() {
        var first = "getProduct" + "Data".repeat(40) + "First";
        var second = "getProduct" + "Data".repeat(40) + "Second";
        var document = source(first, second).toString();
        assertThat(selection.preview("api_12345678", document).operations()).allSatisfy(candidate ->
                assertThat(candidate.unavailableReason()).isNull());
        var operations = importer.parse("api_12345678", document);
        assertThat(operations).extracting(op -> op.name()).doesNotHaveDuplicates().allSatisfy(name -> assertThat(name).hasSize(64));
        assertThat(importer.parse("api_12345678", source(second, first).toString()))
                .extracting(op -> op.name()).containsExactly(operations.get(1).name(), operations.get(0).name());
        assertThat(importer.parse("api_87654321", document).get(0).name()).isNotEqualTo(operations.get(0).name());
        var selected = selection.select("api_12345678", document, List.of(new OperationKey("GET", "/items/1")));
        var preserved = selection.document("api_12345678", selected.yaml()).specification();
        assertThat(preserved.path("paths").path("/items/1").path("get").path("operationId").asText()).isEqualTo(second);
        assertThat(importer.parse("api_12345678", selected.yaml()).get(0).name()).isEqualTo(operations.get(1).name());
    }

    @Test void rejectsDuplicatesInvalidIdsAndCollisionsWithAnExistingShortName() {
        for (var id : List.of("", "1product", "get-product", "get product")) {
            assertThat(selection.preview("crm", source(id).toString()).operations().get(0).unavailableReason()).isNotBlank();
            assertThatThrownBy(() -> importer.parse("crm", source(id).toString())).isInstanceOf(OpenApiException.class);
        }
        var longId = "get" + "Product".repeat(20);
        assertThatThrownBy(() -> importer.parse("crm", source(longId, longId).toString()))
                .isInstanceOf(OpenApiException.class).hasMessageContaining("unikalny operationId");
        var shortId = importer.parse("crm", source(longId).toString()).get(0).name().substring("oa_crm_".length());
        for (var document : List.of(source(longId, shortId), source(shortId, longId)))
            assertThatThrownBy(() -> importer.parse("crm", document.toString()))
                    .isInstanceOf(OpenApiException.class).hasMessageContaining("Kolizja");
    }
}
