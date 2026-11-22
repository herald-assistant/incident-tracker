package pl.mkn.tdw.features.systemassistant.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantConfiguration;
import pl.mkn.tdw.features.systemassistant.contract.SystemAssistantException;
import pl.mkn.tdw.integrations.openapi.OpenApiImportPort;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiOperation;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

import java.util.*;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "system-assistant.enabled", havingValue = "true")
public class SystemAssistantConfigurationService {
    private final OpenApiImportPort importer;
    private final SystemAssistantConfigurationStore store;
    private final pl.mkn.tdw.agenttools.openapi.OpenApiToolCatalog catalog;
    private Snapshot snapshot = new Snapshot(SystemAssistantConfiguration.empty(), List.of(), Map.of());
    private boolean unavailable;

    public record Snapshot(SystemAssistantConfiguration configuration, List<OpenApiOperation> operations,
                           Map<String, String> operationApiIds) {
        public Snapshot {
            operations = List.copyOf(operations);
            operationApiIds = Map.copyOf(operationApiIds);
        }

        public Snapshot forProfile(String profileId) {
            var profile = configuration.profiles().stream().filter(item -> item.id().equals(profileId)).findFirst()
                    .orElseThrow(() -> new SystemAssistantException("PROFILE_NOT_FOUND", UserFacingErrorType.BAD_REQUEST,
                            "Wybierz istniejący profil przed wysłaniem wiadomości."));
            var apis = configuration.apis().stream().filter(item -> profile.apiIds().contains(item.id())).toList();
            var skills = configuration.skills().stream().filter(item -> profile.skillIds().contains(item.id())).toList();
            var apiIds = apis.stream().map(SystemAssistantConfiguration.ApiDefinition::id).collect(java.util.stream.Collectors.toSet());
            var selected = operations.stream().filter(item -> apiIds.contains(operationApiIds.get(item.name()))).toList();
            var mapping = new HashMap<String, String>();
            selected.forEach(item -> mapping.put(item.name(), operationApiIds.get(item.name())));
            return new Snapshot(new SystemAssistantConfiguration(apis, skills, List.of(profile)), selected, mapping);
        }
    }

    @jakarta.annotation.PostConstruct
    public synchronized void load() {
        try {
            var value = store.read();
            var prepared = prepare(value);
            catalog.replace(prepared.operations(), () -> {
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
        catalog.replace(prepared.operations(), () -> store.write(value));
        snapshot = prepared;
        unavailable = false;
        return snapshot.configuration();
    }

    private Snapshot prepare(SystemAssistantConfiguration value) {
        var ids = new HashSet<String>();
        for (var api : value.apis()) if (!ids.add(api.id())) duplicate();
        ids.clear();
        for (var skill : value.skills()) if (!ids.add(skill.id())) duplicate();
        ids.clear();
        for (var profile : value.profiles()) {
            if (!ids.add(profile.id())) duplicate();
            if (new HashSet<>(profile.apiIds()).size() != profile.apiIds().size()
                    || new HashSet<>(profile.skillIds()).size() != profile.skillIds().size())
                throw new SystemAssistantException("DUPLICATE_REFERENCE", UserFacingErrorType.BAD_REQUEST,
                        "Przypisania API i skilli w profilu nie mogą się powtarzać.");
        }
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
