package pl.mkn.tdw.features.systemassistant.api;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;
import pl.mkn.tdw.features.systemassistant.config.SystemAssistantConfigurationService;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantConfiguration;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiOperation;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/system-assistant/configuration")
@ConditionalOnProperty(name = "system-assistant.enabled", havingValue = "true")
@RequiredArgsConstructor
public class SystemAssistantConfigurationController {
    private final SystemAssistantConfigurationService configurations;

    @GetMapping
    public SystemAssistantConfiguration current() {
        return configurations.current();
    }

    @PutMapping
    public SystemAssistantConfiguration save(@Valid @RequestBody SystemAssistantConfiguration request) {
        return configurations.save(request);
    }

    public record ProfileOption(String id, String name, String urlPattern) {}

    @GetMapping("/profiles")
    public List<ProfileOption> profiles() {
        return configurations.current().profiles().stream()
                .map(profile -> new ProfileOption(profile.id(), profile.name(), profile.urlPattern())).toList();
    }

    @GetMapping("/tools")
    public List<Tool> tools() {
        var snapshot = configurations.snapshot();
        return snapshot.operations().stream().map(operation -> new Tool(
                snapshot.operationApiIds().get(operation.name()), operation.name(), operation.description(),
                operation.method(), operation.server(), operation.path(), operation.parameters(), operation.inputSchema())).toList();
    }

    public record Tool(String apiId, String name, String description, String method, URI server,
                       String path, List<OpenApiOperation.Parameter> parameters, JsonNode inputSchema) {
    }
}
