package pl.mkn.tdw.features.changeverification.source;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepository;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchRepository;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchScope;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextQuery;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Component
@Slf4j
@RequiredArgsConstructor
public class ChangeVerificationOperationalContextMatcher {

    private final OperationalContextPort operationalContextPort;

    public List<ChangeVerificationRepositorySnapshot> enrich(List<ChangeVerificationRepositorySnapshot> repositories) {
        if (repositories == null || repositories.isEmpty()) {
            return List.of();
        }

        OperationalContextCatalog catalog;
        try {
            catalog = operationalContextPort.loadContext(OperationalContextQuery.all());
        } catch (RuntimeException exception) {
            log.warn("Change Verification operational context discovery failed reason={}", safeMessage(exception));
            return List.copyOf(repositories);
        }

        if (catalog == null || catalog.repositories().isEmpty() || catalog.codeSearchScopes().isEmpty()) {
            return List.copyOf(repositories);
        }

        return repositories.stream()
                .map(repository -> repository.withOperationalContextMatches(matches(repository, catalog)))
                .toList();
    }

    private List<ChangeVerificationOperationalContextMatch> matches(
            ChangeVerificationRepositorySnapshot repository,
            OperationalContextCatalog catalog
    ) {
        var repositoryIds = catalog.repositories().stream()
                .filter(contextRepository -> repositoryMatches(repository, contextRepository))
                .map(OperationalContextRepository::id)
                .filter(StringUtils::hasText)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        if (repositoryIds.isEmpty()) {
            return List.of();
        }

        var keys = new LinkedHashSet<String>();
        return catalog.codeSearchScopes().stream()
                .flatMap(scope -> scope.repositories().stream()
                        .filter(scopeRepository -> repositoryIds.contains(scopeRepository.repoId()))
                        .map(scopeRepository -> match(scope, scopeRepository)))
                .filter(match -> keys.add(key(match)))
                .toList();
    }

    private boolean repositoryMatches(
            ChangeVerificationRepositorySnapshot repository,
            OperationalContextRepository contextRepository
    ) {
        var signals = new LinkedHashSet<String>();
        signals.add(contextRepository.id());
        signals.add(contextRepository.name());
        signals.add(contextRepository.shortName());
        signals.add(contextRepository.git().projectPath());
        signals.add(contextRepository.git().project());
        signals.addAll(contextRepository.genericSignals());

        return contains(signals, repository.projectPath())
                || contains(signals, repository.repositoryKey())
                || contains(signals, repository.repositoryName())
                || contains(signals, repository.projectName());
    }

    private ChangeVerificationOperationalContextMatch match(
            OperationalContextRepositorySearchScope scope,
            OperationalContextRepositorySearchRepository scopeRepository
    ) {
        return new ChangeVerificationOperationalContextMatch(
                scopeRepository.repoId(),
                scope.id(),
                scope.name(),
                scope.scopeType(),
                scope.target().type(),
                scope.target().id(),
                scopeRepository.role(),
                scopeRepository.priority(),
                scopeRepository.reason(),
                scopeRepository.readFor(),
                scopeRepository.searchMode(),
                scopeRepository.pathPrefixes(),
                scope.limitations()
        );
    }

    private boolean contains(LinkedHashSet<String> values, String expected) {
        if (!StringUtils.hasText(expected)) {
            return false;
        }
        var normalizedExpected = normalize(expected);
        return values.stream()
                .filter(StringUtils::hasText)
                .map(this::normalize)
                .anyMatch(normalizedExpected::equals);
    }

    private String key(ChangeVerificationOperationalContextMatch match) {
        return normalize(match.repositoryId()) + "|" + normalize(match.codeSearchScopeId()) + "|"
                + normalize(match.repositoryRole());
    }

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().replace('\\', '/').toLowerCase(Locale.ROOT)
                : "";
    }

    private String safeMessage(RuntimeException exception) {
        return StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
    }
}
