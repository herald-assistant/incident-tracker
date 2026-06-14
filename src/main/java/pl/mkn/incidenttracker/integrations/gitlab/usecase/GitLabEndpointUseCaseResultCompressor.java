package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Component
public class GitLabEndpointUseCaseResultCompressor {

    public GitLabEndpointUseCaseContextResult compress(GitLabEndpointUseCaseContextResult result) {
        if (result == null) {
            return new GitLabEndpointUseCaseContextResult(
                    null,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of("Endpoint use case context result is required for compression."),
                    List.of(),
                    GitLabEndpointUseCaseLimits.defaults(),
                    GitLabEndpointUseCaseConfidence.LOW
            );
        }

        var mergedFiles = mergeFiles(result.files()).stream()
                .sorted(fileComparator())
                .toList();
        var maxFiles = result.limits().maxFiles();
        var truncated = mergedFiles.size() > maxFiles;
        var files = truncated
                ? mergedFiles.stream().limit(maxFiles).toList()
                : mergedFiles;
        var limitations = new ArrayList<>(deduplicate(result.limitations()));
        if (truncated) {
            limitations.add("Result file list was truncated from %d to maxFiles=%d after priority sorting."
                    .formatted(mergedFiles.size(), maxFiles));
        }
        var limits = new GitLabEndpointUseCaseLimits(
                result.limits().maxDepth(),
                maxFiles,
                result.limits().maxReadFiles(),
                result.limits().maxDepthReached(),
                result.limits().maxFilesReached() || truncated,
                result.limits().readFileCount(),
                result.limits().readFileLimitReached()
        );

        return new GitLabEndpointUseCaseContextResult(
                result.repository(),
                result.endpoint(),
                files,
                deduplicateRelations(result.relations()),
                deduplicateUnresolved(result.unresolved()),
                limitations,
                suggestedNextReads(result.repository(), files),
                limits,
                globalConfidence(files, result.unresolved(), limits)
        );
    }

    private List<GitLabEndpointUseCaseFileCandidate> mergeFiles(List<GitLabEndpointUseCaseFileCandidate> files) {
        var merged = new LinkedHashMap<String, MutableFile>();
        for (var file : files) {
            if (file == null || file.path() == null) {
                continue;
            }
            merged.computeIfAbsent(file.path(), ignored -> new MutableFile(file.path()))
                    .merge(file);
        }
        return merged.values().stream()
                .map(MutableFile::toCandidate)
                .toList();
    }

    private List<GitLabEndpointUseCaseRelation> deduplicateRelations(List<GitLabEndpointUseCaseRelation> relations) {
        var merged = new LinkedHashMap<String, GitLabEndpointUseCaseRelation>();
        for (var relation : relations) {
            if (relation == null || relation.from() == null || relation.to() == null) {
                continue;
            }
            merged.putIfAbsent(
                    relation.from() + "|" + relation.to() + "|" + relation.kind(),
                    relation
            );
        }
        return List.copyOf(merged.values());
    }

    private List<GitLabEndpointUseCaseUnresolvedReference> deduplicateUnresolved(
            List<GitLabEndpointUseCaseUnresolvedReference> unresolved
    ) {
        var merged = new LinkedHashMap<String, GitLabEndpointUseCaseUnresolvedReference>();
        for (var reference : unresolved) {
            if (reference == null) {
                continue;
            }
            merged.putIfAbsent(
                    reference.symbol() + "|" + reference.ownerPath() + "|" + reference.reason(),
                    reference
            );
        }
        return List.copyOf(merged.values());
    }

    private List<String> suggestedNextReads(
            GitLabEndpointUseCaseRepositoryContext repository,
            List<GitLabEndpointUseCaseFileCandidate> files
    ) {
        var projectName = repository != null && repository.projectName() != null
                ? repository.projectName()
                : "<project>";
        return files.stream()
                .map(file -> {
                    var symbols = file.symbols().isEmpty()
                            ? ""
                            : " symbols: " + String.join(", ", file.symbols().stream().limit(4).toList());
                    return "%s:%s via gitlab_read_repository_file_outline%s".formatted(
                            projectName,
                            file.path(),
                            symbols
                    );
                })
                .toList();
    }

    private GitLabEndpointUseCaseConfidence globalConfidence(
            List<GitLabEndpointUseCaseFileCandidate> files,
            List<GitLabEndpointUseCaseUnresolvedReference> unresolved,
            GitLabEndpointUseCaseLimits limits
    ) {
        if (files.isEmpty()) {
            return GitLabEndpointUseCaseConfidence.LOW;
        }
        if (files.stream().anyMatch(file -> file.confidence() == GitLabEndpointUseCaseConfidence.LOW)) {
            return GitLabEndpointUseCaseConfidence.LOW;
        }
        if (!unresolved.isEmpty()
                || limits.maxDepthReached()
                || limits.maxFilesReached()
                || limits.readFileLimitReached()
                || files.stream().anyMatch(file -> file.confidence() == GitLabEndpointUseCaseConfidence.MEDIUM)) {
            return GitLabEndpointUseCaseConfidence.MEDIUM;
        }
        return GitLabEndpointUseCaseConfidence.HIGH;
    }

    private Comparator<GitLabEndpointUseCaseFileCandidate> fileComparator() {
        return Comparator
                .comparingInt(GitLabEndpointUseCaseFileCandidate::priority)
                .thenComparing((GitLabEndpointUseCaseFileCandidate file) -> confidenceRank(file.confidence()),
                        Comparator.reverseOrder())
                .thenComparing(GitLabEndpointUseCaseFileCandidate::path);
    }

    private int confidenceRank(GitLabEndpointUseCaseConfidence confidence) {
        return switch (confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
        };
    }

    private int rolePriority(GitLabEndpointUseCaseFileRole role) {
        return switch (role != null ? role : GitLabEndpointUseCaseFileRole.UNKNOWN) {
            case CONTROLLER, OPENAPI_CONTRACT, API_INTERFACE -> 1;
            case USE_CASE_PORT, USE_CASE_SERVICE -> 2;
            case REPOSITORY_PORT, REPOSITORY_IMPLEMENTATION, SPRING_DATA_REPOSITORY -> 4;
            case MAPPER -> 5;
            case DOMAIN_MODEL -> 6;
            case WEB_MODEL, PROJECTION -> 7;
            case CONFIGURATION, EXTERNAL_CLIENT -> 8;
            case UNKNOWN -> 9;
        };
    }

    private List<String> deduplicate(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(GitLabEndpointUseCaseModelSupport.copyStrings(values)));
    }

    private final class MutableFile {

        private final String path;
        private final LinkedHashSet<String> symbols = new LinkedHashSet<>();
        private final LinkedHashSet<String> reasons = new LinkedHashSet<>();
        private GitLabEndpointUseCaseFileRole role = GitLabEndpointUseCaseFileRole.UNKNOWN;
        private int priority = rolePriority(GitLabEndpointUseCaseFileRole.UNKNOWN);
        private GitLabEndpointUseCaseConfidence confidence = GitLabEndpointUseCaseConfidence.LOW;

        private MutableFile(String path) {
            this.path = path;
        }

        private void merge(GitLabEndpointUseCaseFileCandidate file) {
            if (file.priority() < priority) {
                role = file.role();
                priority = file.priority();
            } else if (file.priority() == priority && role == GitLabEndpointUseCaseFileRole.UNKNOWN) {
                role = file.role();
            }
            symbols.addAll(file.symbols());
            if (file.reason() != null) {
                reasons.add(file.reason());
            }
            if (confidenceRank(file.confidence()) > confidenceRank(confidence)) {
                confidence = file.confidence();
            }
        }

        private GitLabEndpointUseCaseFileCandidate toCandidate() {
            return new GitLabEndpointUseCaseFileCandidate(
                    path,
                    role,
                    priority,
                    symbols.stream().toList(),
                    String.join(" ", reasons),
                    confidence
            );
        }
    }
}
