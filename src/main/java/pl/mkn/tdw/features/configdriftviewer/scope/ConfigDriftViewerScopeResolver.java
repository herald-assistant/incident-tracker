package pl.mkn.tdw.features.configdriftviewer.scope;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.configdriftviewer.source.ConfigDriftViewerRepositoryCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextSystem;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextEntryType;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextQuery;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class ConfigDriftViewerScopeResolver {

    static final String INTERNAL_SERVICE_KIND = "internal-service";
    static final String CONFIGURATION_DIRECTORIES_SIGNAL = "configurationDirectories";

    private static final Pattern SAFE_DIRECTORY =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/-]{0,254}");

    private final ConfigDriftViewerRepositoryCatalog repositoryCatalog;
    private final OperationalContextPort operationalContextPort;

    public ConfigDriftViewerScope resolve(String repositoryId, String systemId) {
        return resolve(repositoryId, systemId, catalog());
    }

    public ConfigDriftViewerScope resolve(
            String repositoryId,
            String systemId,
            OperationalContextCatalog catalog
    ) {
        var repository = repositoryCatalog.require(repositoryId);
        var system = catalog.systems().stream()
                .filter(candidate -> candidate.id().equals(systemId))
                .findFirst()
                .orElseThrow(() -> ConfigDriftViewerScopeException.systemNotFound(systemId));
        var configurationDirectory = resolveConfigurationDirectory(system);
        return new ConfigDriftViewerScope(
                repository.id(),
                repository.connectionId(),
                repository.projectPath(),
                system.id(),
                system.label(),
                configurationDirectory
        );
    }

    public List<ConfigDriftViewerSystemOption> availableSystems() {
        return systems().stream()
                .filter(this::isInternalService)
                .map(this::toAvailableOption)
                .filter(java.util.Objects::nonNull)
                .sorted((left, right) -> left.label().compareToIgnoreCase(right.label()))
                .toList();
    }

    String resolveConfigurationDirectory(OperationalContextSystem system) {
        if (!isInternalService(system)) {
            throw ConfigDriftViewerScopeException.systemNotInternalService(system.id());
        }

        var candidates = new LinkedHashSet<String>();
        addAll(candidates, system.values("runtime.configurationDirectory"));
        addAll(candidates, system.values("deployment.configurationDirectory"));
        addAll(candidates, system.matchSignals().exact().values(CONFIGURATION_DIRECTORIES_SIGNAL));

        if (candidates.isEmpty()) {
            throw ConfigDriftViewerScopeException.configurationDirectoryMissing(system.id());
        }
        if (candidates.size() > 1) {
            throw ConfigDriftViewerScopeException.configurationDirectoryAmbiguous(system.id());
        }

        return safeDirectory(candidates.iterator().next(), system.id());
    }

    private List<OperationalContextSystem> systems() {
        return catalog().systems();
    }

    private OperationalContextCatalog catalog() {
        return operationalContextPort.capture().query(new OperationalContextQuery(
                Set.of(OperationalContextEntryType.SYSTEM),
                List.of(),
                false
        ));
    }

    private ConfigDriftViewerSystemOption toAvailableOption(OperationalContextSystem system) {
        try {
            return new ConfigDriftViewerSystemOption(
                    system.id(),
                    system.label(),
                    resolveConfigurationDirectory(system)
            );
        } catch (ConfigDriftViewerScopeException ignored) {
            return null;
        }
    }

    private boolean isInternalService(OperationalContextSystem system) {
        return system != null
                && StringUtils.hasText(system.id())
                && INTERNAL_SERVICE_KIND.equalsIgnoreCase(system.kind());
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
            throw ConfigDriftViewerScopeException.configurationDirectoryInvalid(systemId);
        }
        return normalized;
    }
}
