package pl.mkn.tdw.features.runtimeconfigurationverification.deep;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationCodeGrounding;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationCodeUsageKind;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepPreflight;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepRepositoryScope;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationGroundingConfidence;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.integrations.gitlab.GitLabProperties;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositorySearchQuery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
class RuntimeConfigurationCodeUsageSearchService {

    private static final int MAX_CHANGED_KEYS = 50;
    private static final int MAX_FILES_PER_REPOSITORY = 20;
    private static final int MAX_FILE_CHARACTERS = 250_000;
    private static final Pattern CONFIGURATION_PROPERTIES = Pattern.compile(
            "@ConfigurationProperties\\s*\\(\\s*(?:prefix\\s*=\\s*)?[\"']([^\"']+)[\"']"
    );
    private static final Pattern SYMBOL = Pattern.compile(
            "\\b(?:class|interface|record|enum)\\s+([A-Za-z_$][A-Za-z0-9_$]*)"
    );

    private final GitLabProperties gitLabProperties;
    private final GitLabRepositoryPort gitLabRepositoryPort;

    RuntimeConfigurationCodeSearchResult search(
            RuntimeConfigurationDeepPreflight preflight,
            RuntimeConfigurationDeterministicContext deterministicContext
    ) {
        var targets = propertyTargets(deterministicContext);
        var searchTerms = targets.values().stream()
                .flatMap(target -> java.util.stream.Stream.concat(
                        target.variants().stream(),
                        configurationPrefixes(target.path()).stream()
                ))
                .distinct()
                .toList();
        var groundings = new ArrayList<RuntimeConfigurationCodeGrounding>();
        var visibilityLimits = new LinkedHashSet<String>();
        var filesInspected = 0;
        var repositoriesSearched = 0;

        for (var repository : preflight.repositories()) {
            if (!repository.ready()) {
                continue;
            }
            repositoriesSearched++;
            List<GitLabRepositoryFileCandidate> candidates;
            try {
                candidates = gitLabRepositoryPort.searchCandidateFiles(
                        new GitLabRepositorySearchQuery(
                                null,
                                gitLabProperties.getGroup(),
                                repository.usedRef(),
                                List.of(repository.projectName()),
                                List.of(),
                                searchTerms,
                                searchPathPrefixes(repository)
                        )
                );
            } catch (RuntimeException exception) {
                visibilityLimits.add("Code search failed for repository `"
                        + repository.repositoryId() + "`.");
                continue;
            }

            for (var candidate : candidates.stream()
                    .filter(candidate -> withinRepository(candidate, repository))
                    .filter(candidate -> withinPathBoundary(candidate.filePath(), repository))
                    .sorted(Comparator
                            .comparingInt(GitLabRepositoryFileCandidate::matchScore)
                            .reversed()
                            .thenComparing(GitLabRepositoryFileCandidate::filePath))
                    .limit(MAX_FILES_PER_REPOSITORY)
                    .toList()) {
                try {
                    var file = gitLabRepositoryPort.readFile(
                            gitLabProperties.getGroup(),
                            repository.projectName(),
                            repository.usedRef(),
                            candidate.filePath(),
                            MAX_FILE_CHARACTERS
                    );
                    filesInspected++;
                    if (file.truncated()) {
                        visibilityLimits.add("Code file `" + candidate.filePath()
                                + "` was truncated during deterministic inspection.");
                    }
                    inspectFile(repository, candidate.filePath(), file.content(), targets, groundings);
                } catch (RuntimeException exception) {
                    visibilityLimits.add("Code file `" + candidate.filePath()
                            + "` could not be read from repository `"
                            + repository.repositoryId() + "`.");
                }
            }
        }

        return new RuntimeConfigurationCodeSearchResult(
                assignIds(groundings),
                repositoriesSearched,
                targets.size(),
                filesInspected,
                List.copyOf(visibilityLimits)
        );
    }

    private Map<String, PropertyTarget> propertyTargets(
            RuntimeConfigurationDeterministicContext deterministicContext
    ) {
        var result = new LinkedHashMap<String, PropertyTarget>();
        deterministicContext.differences().stream()
                .filter(difference -> StringUtils.hasText(difference.path()))
                .limit(MAX_CHANGED_KEYS)
                .forEach(difference -> result.putIfAbsent(
                        difference.path(),
                        new PropertyTarget(
                                difference.path(),
                                difference.differenceId(),
                                variants(difference.path())
                        )
                ));
        return result;
    }

    private List<String> variants(String path) {
        var values = new LinkedHashSet<String>();
        values.add(path);
        var kebab = path.replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .replace('_', '-')
                .toLowerCase(Locale.ROOT);
        values.add(kebab);
        values.add(kebab.replace('-', '_'));
        values.add(kebab.replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT));
        return values.stream()
                .filter(StringUtils::hasText)
                .filter(value -> value.length() >= 3)
                .toList();
    }

    private List<String> configurationPrefixes(String path) {
        var values = new ArrayList<String>();
        var kebab = path.replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .replace('_', '-')
                .toLowerCase(Locale.ROOT);
        var segments = kebab.split("\\.");
        var prefix = new StringBuilder();
        for (var index = 0; index < segments.length - 1; index++) {
            if (!prefix.isEmpty()) {
                prefix.append('.');
            }
            prefix.append(segments[index]);
            if (prefix.toString().contains(".")) {
                values.add(prefix.toString());
            }
        }
        return List.copyOf(values);
    }

    private void inspectFile(
            RuntimeConfigurationDeepRepositoryScope repository,
            String filePath,
            String content,
            Map<String, PropertyTarget> targets,
            List<RuntimeConfigurationCodeGrounding> groundings
    ) {
        var lines = (content != null ? content : "").split("\\R", -1);
        for (var lineIndex = 0; lineIndex < lines.length; lineIndex++) {
            var line = lines[lineIndex];
            var configurationProperties = CONFIGURATION_PROPERTIES.matcher(line);
            while (configurationProperties.find()) {
                var prefix = canonical(configurationProperties.group(1));
                for (var target : targets.values()) {
                    if (canonical(target.path()).startsWith(prefix + ".")) {
                        addGrounding(
                                groundings,
                                repository,
                                filePath,
                                lines,
                                lineIndex,
                                target,
                                RuntimeConfigurationCodeUsageKind.CONFIGURATION_PROPERTIES,
                                containsBindingLeaf(lines, target.path())
                                        ? RuntimeConfigurationGroundingConfidence.HIGH
                                        : RuntimeConfigurationGroundingConfidence.MEDIUM
                        );
                    }
                }
            }

            for (var target : targets.values()) {
                for (var variant : target.variants()) {
                    if (!containsTerm(line, variant)) {
                        continue;
                    }
                    var valueAnnotation = line.contains("@Value") && line.contains("${");
                    addGrounding(
                            groundings,
                            repository,
                            filePath,
                            lines,
                            lineIndex,
                            target,
                            valueAnnotation
                                    ? RuntimeConfigurationCodeUsageKind.VALUE_ANNOTATION
                                    : variant.equals(target.path())
                                    ? RuntimeConfigurationCodeUsageKind.EXACT_PROPERTY
                                    : RuntimeConfigurationCodeUsageKind.RELAXED_BINDING,
                            valueAnnotation || variant.equals(target.path())
                                    ? RuntimeConfigurationGroundingConfidence.HIGH
                                    : RuntimeConfigurationGroundingConfidence.MEDIUM
                    );
                    break;
                }
            }
        }
    }

    private void addGrounding(
            List<RuntimeConfigurationCodeGrounding> groundings,
            RuntimeConfigurationDeepRepositoryScope repository,
            String filePath,
            String[] lines,
            int lineIndex,
            PropertyTarget target,
            RuntimeConfigurationCodeUsageKind kind,
            RuntimeConfigurationGroundingConfidence confidence
    ) {
        var key = repository.repositoryId() + "|" + filePath + "|" + lineIndex
                + "|" + target.differenceId() + "|" + kind;
        if (groundings.stream().anyMatch(existing ->
                groundingKey(existing).equals(key))) {
            return;
        }
        groundings.add(new RuntimeConfigurationCodeGrounding(
                null,
                repository.scopeId(),
                repository.repositoryId(),
                repository.projectPath(),
                repository.usedRef(),
                filePath,
                lineIndex + 1,
                symbol(lines, lineIndex),
                target.path(),
                target.differenceId(),
                kind,
                confidence
        ));
    }

    private List<RuntimeConfigurationCodeGrounding> assignIds(
            List<RuntimeConfigurationCodeGrounding> groundings
    ) {
        var result = new ArrayList<RuntimeConfigurationCodeGrounding>();
        for (var index = 0; index < groundings.size(); index++) {
            var grounding = groundings.get(index);
            result.add(new RuntimeConfigurationCodeGrounding(
                    "code-grounding-" + String.format("%03d", index + 1),
                    grounding.scopeId(),
                    grounding.repositoryId(),
                    grounding.projectPath(),
                    grounding.usedRef(),
                    grounding.filePath(),
                    grounding.lineNumber(),
                    grounding.symbol(),
                    grounding.matchedPropertyPath(),
                    grounding.differenceId(),
                    grounding.usageKind(),
                    grounding.confidence()
            ));
        }
        return List.copyOf(result);
    }

    private String groundingKey(RuntimeConfigurationCodeGrounding grounding) {
        return grounding.repositoryId() + "|" + grounding.filePath() + "|"
                + (grounding.lineNumber() - 1) + "|" + grounding.differenceId()
                + "|" + grounding.usageKind();
    }

    private String symbol(String[] lines, int lineIndex) {
        for (var index = lineIndex; index >= Math.max(0, lineIndex - 150); index--) {
            var matcher = SYMBOL.matcher(lines[index]);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        for (var index = lineIndex + 1; index < Math.min(lines.length, lineIndex + 6); index++) {
            var matcher = SYMBOL.matcher(lines[index]);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    private boolean containsTerm(String line, String term) {
        if (!StringUtils.hasText(line) || !StringUtils.hasText(term)) {
            return false;
        }
        return line.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT));
    }

    private boolean containsBindingLeaf(String[] lines, String propertyPath) {
        var separator = propertyPath.lastIndexOf('.');
        var leaf = separator >= 0 ? propertyPath.substring(separator + 1) : propertyPath;
        var kebab = leaf.replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .replace('_', '-')
                .toLowerCase(Locale.ROOT);
        var variants = new LinkedHashSet<String>();
        variants.add(leaf.toLowerCase(Locale.ROOT));
        variants.add(kebab);
        variants.add(kebab.replace('-', '_'));
        variants.add(kebab.replace("-", ""));
        return java.util.Arrays.stream(lines)
                .map(line -> line.toLowerCase(Locale.ROOT))
                .anyMatch(line -> variants.stream().anyMatch(line::contains));
    }

    private boolean withinRepository(
            GitLabRepositoryFileCandidate candidate,
            RuntimeConfigurationDeepRepositoryScope repository
    ) {
        return candidate != null
                && repository.projectName().equalsIgnoreCase(candidate.projectName())
                && repository.usedRef().equals(candidate.branch());
    }

    private boolean withinPathBoundary(
            String filePath,
            RuntimeConfigurationDeepRepositoryScope repository
    ) {
        if (!StringUtils.hasText(filePath)) {
            return false;
        }
        if ("whole-repository".equals(repository.searchMode())) {
            return true;
        }
        return repository.pathPrefixes().stream().anyMatch(prefix ->
                filePath.equals(prefix) || filePath.startsWith(prefix + "/"));
    }

    private List<String> searchPathPrefixes(RuntimeConfigurationDeepRepositoryScope repository) {
        return "path-prefixes".equals(repository.searchMode())
                ? repository.pathPrefixes()
                : List.of();
    }

    private String canonical(String value) {
        return value != null
                ? value.replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", ".")
                        .replaceAll("^\\.|\\.$", "")
                : "";
    }

    private record PropertyTarget(
            String path,
            String differenceId,
            List<String> variants
    ) {
    }
}
