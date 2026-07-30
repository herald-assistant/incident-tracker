package pl.mkn.tdw.features.runtimeconfigurationverification.scope;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.runtimeconfigurationverification.source.RuntimeConfigurationRepositoryCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextSystem;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextEntryType;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextQuery;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class RuntimeConfigurationScopeResolver {

    static final String INTERNAL_SYSTEM_KIND = "internal-system";
    static final String CONFIGURATION_DIRECTORIES_SIGNAL = "configurationDirectories";

    private static final Pattern SAFE_DIRECTORY =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{0,254}");

    private final RuntimeConfigurationRepositoryCatalog repositoryCatalog;
    private final OperationalContextPort operationalContextPort;

    public RuntimeConfigurationScope resolve(String repositoryId, String systemId) {
        var repository = repositoryCatalog.require(repositoryId);
        var system = systems().stream()
                .filter(candidate -> candidate.id().equals(systemId))
                .findFirst()
                .orElseThrow(() -> RuntimeConfigurationScopeException.systemNotFound(systemId));
        var configurationDirectory = resolveConfigurationDirectory(system);
        return new RuntimeConfigurationScope(
                repository.id(),
                repository.connectionId(),
                repository.projectPath(),
                system.id(),
                system.label(),
                configurationDirectory
        );
    }

    public List<RuntimeConfigurationSystemOption> availableSystems() {
        return systems().stream()
                .filter(this::isInternalSystem)
                .map(this::toAvailableOption)
                .filter(java.util.Objects::nonNull)
                .sorted((left, right) -> left.label().compareToIgnoreCase(right.label()))
                .toList();
    }

    String resolveConfigurationDirectory(OperationalContextSystem system) {
        if (!isInternalSystem(system)) {
            throw RuntimeConfigurationScopeException.systemNotInternal(system.id());
        }

        var candidates = new LinkedHashSet<String>();
        addAll(candidates, system.values("runtime.configurationDirectory"));
        addAll(candidates, system.values("deployment.configurationDirectory"));
        addAll(candidates, system.matchSignals().exact().values(CONFIGURATION_DIRECTORIES_SIGNAL));

        if (candidates.isEmpty()) {
            throw RuntimeConfigurationScopeException.configurationDirectoryMissing(system.id());
        }
        if (candidates.size() > 1) {
            throw RuntimeConfigurationScopeException.configurationDirectoryAmbiguous(system.id());
        }

        return safeDirectory(candidates.iterator().next(), system.id());
    }

    private List<OperationalContextSystem> systems() {
        return operationalContextPort.loadContext(new OperationalContextQuery(
                Set.of(OperationalContextEntryType.SYSTEM),
                List.of(),
                false
        )).systems();
    }

    private RuntimeConfigurationSystemOption toAvailableOption(OperationalContextSystem system) {
        try {
            return new RuntimeConfigurationSystemOption(
                    system.id(),
                    system.label(),
                    resolveConfigurationDirectory(system)
            );
        } catch (RuntimeConfigurationScopeException ignored) {
            return null;
        }
    }

    private boolean isInternalSystem(OperationalContextSystem system) {
        return system != null
                && StringUtils.hasText(system.id())
                && INTERNAL_SYSTEM_KIND.equalsIgnoreCase(system.kind());
    }

    private void addAll(LinkedHashSet<String> candidates, List<String> values) {
        for (var value : values != null ? values : List.<String>of()) {
            if (StringUtils.hasText(value)) {
                candidates.add(value.trim());
            }
        }
    }

    private String safeDirectory(String value, String systemId) {
        var normalized = value.trim().replace('\\', '/');
        if (!SAFE_DIRECTORY.matcher(normalized).matches()
                || normalized.startsWith("/")
                || normalized.endsWith("/")
                || normalized.contains("//")
                || normalized.contains("..")
                || normalized.contains("@{")) {
            throw RuntimeConfigurationScopeException.configurationDirectoryInvalid(systemId);
        }
        return normalized;
    }
}
