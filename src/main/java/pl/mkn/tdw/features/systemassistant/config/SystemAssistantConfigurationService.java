package pl.mkn.tdw.features.systemassistant.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.agenttools.openapi.OpenApiToolCatalog;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantConfiguration;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantException;
import pl.mkn.tdw.integrations.httpauth.contract.HttpAuthProfile;
import pl.mkn.tdw.integrations.openapi.OpenApiImportPort;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiExecution;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiOperation;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiSelection;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

import java.util.*;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "system-assistant.enabled", havingValue = "true")
public class SystemAssistantConfigurationService {
    private final OpenApiImportPort importer;
    private final SystemAssistantConfigurationStore store;
    private final OpenApiToolCatalog catalog;
    private Snapshot snapshot = new Snapshot(SystemAssistantConfiguration.empty(), List.of(), Map.of());
    private boolean unavailable;

    public record Snapshot(SystemAssistantConfiguration configuration, List<OpenApiOperation> operations,
                           Map<String, String> operationApiIds) {
        public Snapshot {
            operations = List.copyOf(operations);
            operationApiIds = Map.copyOf(operationApiIds);
        }

        public List<OpenApiExecution> executions() {
            if (configuration.profiles().size() != 1) throw new IllegalStateException("Wykonanie wymaga jednego wybranego profilu.");
            var auth = new HashMap<String, HttpAuthProfile>();
            configuration.authProfiles().forEach(profile -> auth.put(profile.id(), profile));
            var bindings = new HashMap<String, SystemAssistantConfiguration.ApiBinding>();
            configuration.profiles().get(0).apiBindings().forEach(binding -> bindings.put(binding.apiId(), binding));
            return operations.stream().filter(operation -> bindings.containsKey(operationApiIds.get(operation.name())))
                    .map(operation -> new OpenApiExecution(operation,
                            auth.get(bindings.get(operationApiIds.get(operation.name())).authProfileId()))).toList();
        }

        public Map<String, List<OpenApiExecution>> executionsByProfile() {
            var result = new LinkedHashMap<String, List<OpenApiExecution>>();
            configuration.profiles().forEach(profile -> result.put(profile.id(), forProfile(profile.id()).executions()));
            return Map.copyOf(result);
        }

        public Snapshot forProfile(String profileId) {
            var profile = configuration.profiles().stream().filter(item -> item.id().equals(profileId)).findFirst()
                    .orElseThrow(() -> new SystemAssistantException("PROFILE_NOT_FOUND", UserFacingErrorType.BAD_REQUEST,
                            "Wybierz istniejący profil przed wysłaniem wiadomości."));
            var apis = configuration.apis().stream().filter(item -> profile.apiBindings().stream().anyMatch(binding -> binding.apiId().equals(item.id()))).toList();
            var skills = configuration.skills().stream().filter(item -> profile.skillIds().contains(item.id())).toList();
            var apiIds = apis.stream().map(SystemAssistantConfiguration.ApiDefinition::id).collect(java.util.stream.Collectors.toSet());
            var selected = operations.stream().filter(item -> apiIds.contains(operationApiIds.get(item.name()))).toList();
            var mapping = new HashMap<String, String>();
            selected.forEach(item -> mapping.put(item.name(), operationApiIds.get(item.name())));
            var authIds = profile.apiBindings().stream().map(SystemAssistantConfiguration.ApiBinding::authProfileId).filter(Objects::nonNull).toList();
            var auth = configuration.authProfiles().stream().filter(item -> authIds.contains(item.id())).toList();
            return new Snapshot(new SystemAssistantConfiguration(apis, skills, List.of(profile), auth), selected, mapping);
        }
    }

    @jakarta.annotation.PostConstruct
    public synchronized void load() {
        try {
            var value = store.read();
            var prepared = prepare(value);
            catalog.replace(prepared.executionsByProfile(), () -> {
            });
            snapshot = prepared;
            unavailable = false;
        } catch (RuntimeException failure) {
            unavailable = true;
        }
    }

    public synchronized SystemAssistantConfiguration current() {
        return snapshot().configuration();
    }

    public synchronized Snapshot snapshot() {
        if (unavailable)
            throw new SystemAssistantException("CONFIGURATION_UNAVAILABLE", UserFacingErrorType.SERVICE_UNAVAILABLE,
                    "Nie można odczytać bieżącej konfiguracji. Popraw konfigurację w edytorze i zapisz cały zestaw; chat jest zablokowany.");
        return snapshot;
    }

    public synchronized SystemAssistantConfiguration save(SystemAssistantConfiguration value) {
        var prepared = prepare(value);
        catalog.replace(prepared.executionsByProfile(), () -> store.write(value));
        snapshot = prepared;
        unavailable = false;
        return snapshot.configuration();
    }

    private Snapshot prepare(SystemAssistantConfiguration value) {
        if (value == null || value.authProfiles() == null)
            throw new SystemAssistantException("CONFIGURATION_INVALID", UserFacingErrorType.BAD_REQUEST,
                    "Wymagana bieżąca konfiguracja z authProfiles.");
        var authIds = new HashSet<String>();
        for (var auth : value.authProfiles()) {
            auth.validate();
            if (!authIds.add(auth.id())) duplicate();
        }
        var ids = new HashSet<String>();
        for (var api : value.apis()) if (!ids.add(api.id())) duplicate();
        ids.clear();
        for (var skill : value.skills()) if (!ids.add(skill.id())) duplicate();
        ids.clear();
        for (var profile : value.profiles()) {
            if (!ids.add(profile.id())) duplicate();
            if (profile.apiBindings() == null) throw new SystemAssistantException("CONFIGURATION_INVALID", UserFacingErrorType.BAD_REQUEST, "Wymagane przypisania apiBindings w profilu.");
            if (profile.apiBindings().stream().map(SystemAssistantConfiguration.ApiBinding::apiId).distinct().count() != profile.apiBindings().size()
                    || new HashSet<>(profile.skillIds()).size() != profile.skillIds().size())
                throw new SystemAssistantException("DUPLICATE_REFERENCE", UserFacingErrorType.BAD_REQUEST,
                        "Przypisania API i skilli w profilu nie mogą się powtarzać.");
        }
        for (var profile : value.profiles()) for (var binding : profile.apiBindings())
            if (binding.authProfileId() != null && !authIds.contains(binding.authProfileId()))
                throw new SystemAssistantException("AUTH_PROFILE_NOT_FOUND", UserFacingErrorType.BAD_REQUEST,
                        "Profil „" + profile.name() + "”, API „" + binding.apiId() + "” wskazuje nieistniejące uwierzytelnianie. Wybierz Auth albo No Auth.");
        var operations = new ArrayList<OpenApiOperation>();
        var operationApiIds = new HashMap<String, String>();
        for (var api : value.apis()) {
            var parsed = importer.parse(api.id(), api.yaml());
            operations.addAll(parsed);
            parsed.forEach(operation -> operationApiIds.put(operation.name(), api.id()));
        }
        if (operations.stream().map(OpenApiOperation::name).distinct().count() != operations.size())
            throw new SystemAssistantException("DUPLICATE_TOOL", UserFacingErrorType.BAD_REQUEST,
                    "Zestaw API generuje powtórzone nazwy narzędzi. Zmień ID API lub operationId.");
        var schemaCharacters = operations.stream().mapToLong(operation -> operation.inputSchema().toString().length()).sum();
        if (schemaCharacters > OpenApiSelection.MAX_CATALOG_SCHEMA_CHARACTERS)
            throw new SystemAssistantException("TOOL_SCHEMAS_TOO_LARGE", UserFacingErrorType.BAD_REQUEST,
                    "Schematy argumentów zestawu przekraczają 1000000 znaków. Zmniejsz wybór operacji.");
        if (operations.size() > 100)
            throw new SystemAssistantException("TOO_MANY_TOOLS", UserFacingErrorType.BAD_REQUEST,
                    "Maksymalnie 100 narzędzi w zestawie.");
        return new Snapshot(value, operations, operationApiIds);
    }

    private void duplicate() {
        throw new SystemAssistantException("DUPLICATE_ID", UserFacingErrorType.BAD_REQUEST,
                "ID elementów w kolekcji muszą być unikalne.");
    }
}
