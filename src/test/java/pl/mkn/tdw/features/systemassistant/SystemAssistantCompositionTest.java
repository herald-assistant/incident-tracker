package pl.mkn.tdw.features.systemassistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import pl.mkn.tdw.agenttools.openapi.*;
import pl.mkn.tdw.aiplatform.copilot.tools.CopilotSdkToolFactory;
import pl.mkn.tdw.aiplatform.copilot.tools.context.CopilotToolSessionContext;
import pl.mkn.tdw.aiplatform.copilot.tools.description.CopilotToolDescriptionContext;
import pl.mkn.tdw.features.systemassistant.config.*;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantConfiguration;
import pl.mkn.tdw.integrations.openapi.contract.*;
import pl.mkn.tdw.integrations.openapi.service.*;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;
import pl.mkn.tdw.localworkspace.storage.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import pl.mkn.tdw.testsupport.integrations.IntegrationRestClientBuilderFactoryTestCreator;

class SystemAssistantCompositionTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @TempDir Path directory;

    @Test void preservesNestedPatternsInTheSdkParameters() throws Exception {
        var source = Files.readString(Path.of("src/test/resources/openapi/pattern-api.yaml"));
        var operation = new OpenApiImporter(mapper).parse("demo", source).get(0);
        var callbacks = new OpenApiToolSetFactory((execution, args) ->
                new OpenApiResult("RESPONSE_RECEIVED", 200, mapper.createObjectNode(), "test"), mapper)
                .create(List.of(new OpenApiExecution(operation, null)));
        var sdk = new CopilotSdkToolFactory(List.of(), mapper, List.of(), null).createToolDefinitions(
                new CopilotToolSessionContext("pattern", "pattern-session", Map.of()), CopilotToolDescriptionContext.profile("system-assistant"), callbacks);
        var code = mapper.valueToTree(sdk.get(0).parameters()).path("properties").path("body").path("properties")
                .path("records").path("items").path("allOf").get(0).path("properties").path("code");
        assertThat(code.path("pattern").asText()).isEqualTo("^[A-Z]{2}-[0-9]{3}$");
    }

    @Test void returnsPlainTextAsAStringInsideTheExistingSdkCallbackResult() throws Exception {
        var source = Files.readString(Path.of("src/test/resources/openapi/text-responses-api.yaml"));
        var operation = new OpenApiImporter(mapper).parse("demo", source).get(0);
        var callbacks = new OpenApiToolSetFactory((execution, args) ->
                new OpenApiResult("RESPONSE_RECEIVED", 200, mapper.getNodeFactory().textNode("READY"), "test"), mapper)
                .create(List.of(new OpenApiExecution(operation, null)));
        var sdk = new CopilotSdkToolFactory(List.of(), mapper, List.of(), null).createToolDefinitions(
                new CopilotToolSessionContext("text", "text-session", Map.of()), CopilotToolDescriptionContext.profile("system-assistant"), callbacks);
        assertThat(sdk.get(0).name()).isEqualTo(callbacks.get(0).getToolDefinition().name());
        var result = mapper.readTree(callbacks.get(0).call("{}"));
        assertThat(result.path("body").isTextual()).isTrue();
        assertThat(result.path("body").asText()).isEqualTo("READY");
    }

    @Test void publishesTypedQueryArraysToTheSdkAndPassesTheirValuesToTheSharedCallback() throws Exception {
        var source = Files.readString(Path.of("src/test/resources/openapi/query-arrays-api.yaml"));
        var importer = new OpenApiImporter(mapper);
        var selected = new OpenApiSelectionService(mapper, importer).select("items", source,
                List.of(new OpenApiSelection.OperationKey("GET", "/items/{id}")));
        var operation = importer.parse("items", selected.yaml()).get(0);
        var calls = new AtomicInteger();
        var callbacks = new OpenApiToolSetFactory((execution, args) -> {
            calls.incrementAndGet();
            assertThat(args.json().path("query").path("ids")).isEqualTo(mapper.valueToTree(List.of(101, 102)));
            return new OpenApiResult("RESPONSE_RECEIVED", 200, mapper.createObjectNode(), "test");
        }, mapper).create(List.of(new OpenApiExecution(operation, null)));
        var sdk = new CopilotSdkToolFactory(List.of(), mapper, List.of(), null).createToolDefinitions(
                new CopilotToolSessionContext("arrays", "arrays-session", Map.of()), CopilotToolDescriptionContext.profile("system-assistant"), callbacks);
        var ids = mapper.valueToTree(sdk.get(0).parameters()).path("properties").path("query").path("properties").path("ids");
        assertThat(ids.path("type").asText()).isEqualTo("array");
        assertThat(ids.path("items").path("type").asText()).isEqualTo("integer");
        assertThat(callbacks.get(0).call("{\"path\":{\"id\":\"7\"},\"query\":{\"ids\":[101,102]}}")).contains("RESPONSE_RECEIVED");
        assertThat(calls).hasValue(1);
    }

    @Test void rejectsCollidingShortenedNamesAcrossApisBeforePublication() throws Exception {
        var operationId = "getProduct" + "Details".repeat(20);
        var source = Files.readString(Path.of("src/test/resources/openapi/crm-api.yaml"));
        var importer = new OpenApiImporter(mapper);
        var service = SystemAssistantTestSupport.configuration(importer);
        var config = SystemAssistantTestSupport.config(List.of(
                new SystemAssistantConfiguration.ApiDefinition("crm", "CRM", source.replace("operationId: getTicket", "operationId: b_" + operationId)),
                new SystemAssistantConfiguration.ApiDefinition("crm_b", "CRM B", source.replace("operationId: getTicket", "operationId: " + operationId))), List.of());
        assertThatThrownBy(() -> service.save(config)).hasMessageContaining("powtórzone nazwy narzędzi");
        assertThat(service.current().apis()).isEmpty();
        assertThat(service.snapshot().forProfile("test_profile").executions()).isEmpty();
    }

    @Test void persistsFullOperationIdAndPublishesItsStableShortNameToTheSdk() throws Exception {
        var operationId = "getProduct" + "PricingDetails".repeat(12);
        var importer = new OpenApiImporter(mapper);
        var source = Files.readString(Path.of("src/test/resources/openapi/crm-api.yaml"))
                .replace("operationId: getTicket", "operationId: " + operationId);
        var selected = new OpenApiSelectionService(mapper, importer).select("crm", source,
                List.of(new OpenApiSelection.OperationKey("GET", "/tickets/{id}")));
        var properties = new LocalWorkspaceProperties(); properties.setDirectory(directory.toString());
        try (var validation = Validation.buildDefaultValidatorFactory()) {
            var store = new SystemAssistantConfigurationStore(new LocalWorkspacePaths(properties), new LocalWorkspaceJsonFileStore(mapper), validation.getValidator());
            var service = new SystemAssistantConfigurationService(importer, store, new OpenApiToolCatalog()); service.load();
            var config = SystemAssistantTestSupport.config(List.of(new SystemAssistantConfiguration.ApiDefinition("crm", "CRM", selected.yaml())), List.of());
            service.save(config);
            var restored = new SystemAssistantConfigurationService(importer, store, new OpenApiToolCatalog()); restored.load();
            assertThat(restored.current()).isEqualTo(config);
            var calls = new AtomicInteger();
            var callbacks = new OpenApiToolSetFactory((execution, args) -> {
                calls.incrementAndGet();
                assertThat(execution.operation().name()).isEqualTo(service.snapshot().forProfile("test_profile").executions().get(0).operation().name());
                assertThat(execution.operation().method()).isEqualTo("GET");
                assertThat(execution.operation().path()).isEqualTo("/tickets/{id}");
                return new OpenApiResult("RESPONSE_RECEIVED", 200, mapper.createObjectNode(), "test");
            }, mapper).create(restored.snapshot().forProfile("test_profile").executions());
            var sdk = new CopilotSdkToolFactory(List.of(), mapper, List.of(), null).createToolDefinitions(
                    new CopilotToolSessionContext("names", "names-session", Map.of()), CopilotToolDescriptionContext.profile("system-assistant"), callbacks);
            assertThat(sdk).hasSize(1);
            assertThat(sdk.get(0).name()).hasSize(64).isEqualTo(callbacks.get(0).getToolDefinition().name());
            assertThat(sdk.get(0).description()).contains("operationId: " + operationId);
            assertThat(callbacks.get(0).call("{\"path\":{\"id\":\"7\"}}")).contains("RESPONSE_RECEIVED");
            assertThat(calls).hasValue(1);
        }
    }

    @Test void persistsAndReloadsALargeV1SelectionAndPreservesTheSdkParameterContract() throws Exception {
        var importer = new OpenApiImporter(mapper);
        var document = (com.fasterxml.jackson.databind.node.ObjectNode) new OpenApiSelectionService(mapper, importer)
                .document("products", Files.readString(Path.of("src/test/resources/openapi/composition-api.yaml"))).specification();
        ((com.fasterxml.jackson.databind.node.ObjectNode) document.path("info")).put("description", "synthetic padding ".repeat(7_000));
        var source = document.toString();
        var selection = new OpenApiSelectionService(mapper, importer);
        var selected = selection.select("products", source, List.of(
                new OpenApiSelection.OperationKey("POST", "/items/{batchId}"),
                new OpenApiSelection.OperationKey("GET", "/collections")));
        assertThat(selected.yaml()).hasSizeGreaterThan(100_000);
        var properties = new LocalWorkspaceProperties(); properties.setDirectory(directory.toString());
        try (var validation = Validation.buildDefaultValidatorFactory()) {
            var store = new SystemAssistantConfigurationStore(new LocalWorkspacePaths(properties), new LocalWorkspaceJsonFileStore(mapper), validation.getValidator());
            var service = new SystemAssistantConfigurationService(importer, store, new OpenApiToolCatalog()); service.load();
            var configuration = SystemAssistantTestSupport.config(List.of(new SystemAssistantConfiguration.ApiDefinition("products", "Products", selected.yaml())), List.of());
            assertThat(validation.getValidator().validate(configuration)).isEmpty();
            service.save(configuration);
            var restored = new SystemAssistantConfigurationService(importer, store, new OpenApiToolCatalog()); restored.load();
            assertThat(restored.current()).isEqualTo(configuration);
            assertThat(mapper.readTree(Files.readString(directory.resolve("system-assistant/configuration.json"))).path("version").asInt()).isEqualTo(1);
            var factory = new OpenApiToolSetFactory((execution, args) -> new OpenApiResult("RESPONSE_RECEIVED", 200, mapper.createObjectNode(), "test"), mapper);
            var callbacks = factory.create(restored.snapshot().forProfile("test_profile").executions());
            var sdk = new CopilotSdkToolFactory(List.of(), mapper, List.of(), null).createToolDefinitions(
                    new CopilotToolSessionContext("test", "test-session", Map.of()), CopilotToolDescriptionContext.profile("system-assistant"), callbacks);
            var tool = sdk.stream().filter(item -> item.name().equals("oa_products_createItem")).findFirst().orElseThrow();
            var parameters = mapper.valueToTree(tool.parameters());
            assertThat(parameters.path("properties").path("body").path("oneOf")).hasSize(2);
            assertThat(parameters.toString()).contains("allOf", "Discriminator").doesNotContain("Authorization");
            assertThat(mapper.readTree(mapper.writeValueAsString(tool)).path("parameters")).isEqualTo(parameters);
        }
    }

    @Test void publishesPatchArgumentsThroughTheSdkUsingTheSharedValidatedCallback() throws Exception {
        var importer = new OpenApiImporter(mapper);
        var source = Files.readString(Path.of("src/test/resources/openapi/patch-api.yaml")).replace("\r\n", "\n")
                .replace("parameters:\n      - {name: id", "parameters:\n      - {name: correlationId, in: header}\n      - {name: id");
        var operation = importer.parse("items", source).stream().filter(op -> op.method().equals("PATCH") && op.path().equals("/items/{id}")).findFirst().orElseThrow();
        var executor = new pl.mkn.tdw.integrations.openapi.adapter.rest.OpenApiRestExecutor(IntegrationRestClientBuilderFactoryTestCreator.create(org.springframework.web.client.RestClient.builder()), mapper, profile -> null);
        var callbacks = new OpenApiToolSetFactory(executor, mapper).create(List.of(new OpenApiExecution(operation, null)));
        var sdk = new CopilotSdkToolFactory(List.of(), mapper, List.of(), null).createToolDefinitions(
                new CopilotToolSessionContext("patch", "patch-session", Map.of()), CopilotToolDescriptionContext.profile("system-assistant"), callbacks);
        assertThat(sdk).extracting(com.github.copilot.rpc.ToolDefinition::name).containsExactly("oa_items_patchItem");
        var parameters = mapper.valueToTree(sdk.get(0).parameters());
        assertThat(parameters.path("required").toString()).contains("body", "path");
        assertThat(parameters.path("properties").path("body").path("required").toString()).contains("note");
        assertThat(parameters.toString()).doesNotContain("correlationId", "headers");
        assertThatThrownBy(() -> callbacks.get(0).call("{\"path\":{\"id\":7},\"body\":{}}"))
                .isInstanceOf(pl.mkn.tdw.integrations.openapi.contract.OpenApiException.class);
        assertThatThrownBy(() -> callbacks.get(0).call("{\"path\":{\"id\":7},\"body\":{\"note\":\"ok\"},\"headers\":{\"correlationId\":\"model-value\"}}"))
                .isInstanceOf(pl.mkn.tdw.integrations.openapi.contract.OpenApiException.class);
    }

    @Test void uncertainGetBlocksFurtherPotentialWritesButAllowsExplicitReadonlyInTheSameTurn() throws Exception {
        var source = Files.readString(Path.of("src/test/resources/openapi/composition-api.yaml"));
        var importer = new OpenApiImporter(mapper);
        var operations = new ArrayList<>(importer.parse("products", source));
        operations.add(importer.parse("read", source.replace("operationId: getOrCreateCollection", "operationId: getOrders\n      x-tdw-operation-effect: READ_ONLY")).get(1));
        var calls = new AtomicInteger();
        var factory = new OpenApiToolSetFactory((execution, args) -> {
            calls.incrementAndGet(); return new OpenApiResult(execution.operation().mutation() ? "OUTCOME_UNKNOWN" : "RESPONSE_RECEIVED", 200, mapper.createObjectNode(), "test");
        }, mapper);
        var callbacks = factory.create(operations.stream().map(op -> new OpenApiExecution(op, null)).toList());
        var uncertain = callbacks.stream().filter(tool -> tool.getToolDefinition().name().equals("oa_products_getOrCreateCollection")).findFirst().orElseThrow();
        var readonly = callbacks.stream().filter(tool -> tool.getToolDefinition().name().equals("oa_read_getOrders")).findFirst().orElseThrow();
        assertThat(uncertain.call("{\"query\":{\"ownerKey\":\"test\"}}")).contains("OUTCOME_UNKNOWN");
        assertThat(uncertain.call("{\"query\":{\"ownerKey\":\"test\"}}")).contains("WRITE_BLOCKED");
        assertThat(readonly.call("{\"query\":{\"ownerKey\":\"test\"}}")).contains("RESPONSE_RECEIVED");
        assertThat(calls).hasValue(2);
        assertThat(factory.create(List.of(new OpenApiExecution(operations.get(1), null))).get(0).call("{\"query\":{\"ownerKey\":\"test\"}}")).contains("OUTCOME_UNKNOWN");
    }
}
