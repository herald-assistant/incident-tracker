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
    private Snapshot snapshot = new Snapshot(SystemAssistantConfiguration.empty(), List.of(), Map.of(), Map.of());
    private boolean unavailable;

    public record Snapshot(SystemAssistantConfiguration configuration, List<OpenApiOperation> operations,
                           Map<String, String> operationApiIds, Map<String, SystemAssistantProfileResources> profiles) {
        public Snapshot {
            operations = List.copyOf(operations);
            operationApiIds = Map.copyOf(operationApiIds);
            profiles = Map.copyOf(profiles);
        }

        public SystemAssistantProfileResources forProfile(String profileId) {
            var profile = profileId == null ? null : profiles.get(profileId);
            if (profile == null) throw new SystemAssistantException("PROFILE_NOT_FOUND", UserFacingErrorType.BAD_REQUEST,
                    "Wybierz istniejący profil przed wysłaniem wiadomości.");
            return profile;
        }

        Map<String, List<OpenApiExecution>> executionsByProfile() {
            var result = new LinkedHashMap<String, List<OpenApiExecution>>();
            profiles.forEach((id, profile) -> result.put(id, profile.executions()));
            return result;
        }
    }

    @jakarta.annotation.PostConstruct
    public synchronized void load() {
        try {
            var value = store.read();
            var prepared = prepare(value);
            catalog.replace(prepared.executionsByProfile(), () -> {});
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
        var authById = validateReferences(value);
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
        var profiles = resolveProfiles(value, operations, operationApiIds, authById);
        return new Snapshot(value, operations, operationApiIds, profiles);
    }

    private Map<String, HttpAuthProfile> validateReferences(SystemAssistantConfiguration value) {
        var authById = new HashMap<String, HttpAuthProfile>();
        for (var auth : value.authProfiles()) {
            auth.validate();
            if (authById.put(auth.id(), auth) != null) duplicate();
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
            if (binding.authProfileId() != null && !authById.containsKey(binding.authProfileId()))
                throw new SystemAssistantException("AUTH_PROFILE_NOT_FOUND", UserFacingErrorType.BAD_REQUEST,
                        "Profil „" + profile.name() + "”, API „" + binding.apiId() + "” wskazuje nieistniejące uwierzytelnianie. Wybierz Auth albo No Auth.");
        return authById;
    }

    private Map<String, SystemAssistantProfileResources> resolveProfiles(SystemAssistantConfiguration value,
            List<OpenApiOperation> operations, Map<String, String> operationApiIds, Map<String, HttpAuthProfile> authById) {
        var profiles = new LinkedHashMap<String, SystemAssistantProfileResources>();
        for (var profile : value.profiles()) {
            var bindings = new HashMap<String, SystemAssistantConfiguration.ApiBinding>();
            profile.apiBindings().forEach(binding -> bindings.put(binding.apiId(), binding));
            var executions = new ArrayList<OpenApiExecution>();
            for (var operation : operations) {
                var binding = bindings.get(operationApiIds.get(operation.name()));
                if (binding != null) executions.add(new OpenApiExecution(operation, authById.get(binding.authProfileId())));
            }
            var skills = value.skills().stream().filter(skill -> profile.skillIds().contains(skill.id())).toList();
            profiles.put(profile.id(), new SystemAssistantProfileResources(skills, executions));
        }
        return profiles;
    }

    private void duplicate() {
        throw new SystemAssistantException("DUPLICATE_ID", UserFacingErrorType.BAD_REQUEST,
                "ID elementów w kolekcji muszą być unikalne.");
    }
}
