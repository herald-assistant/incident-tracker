package pl.mkn.tdw.integrations.gitlab.usecase;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

        var filesWithPromotedCandidates = filesWithPromotedUnresolvedCandidates(result.files(), result.unresolved());
        var mergedFiles = mergeFiles(filesWithPromotedCandidates).stream()
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

    private List<GitLabEndpointUseCaseFileCandidate> filesWithPromotedUnresolvedCandidates(
            List<GitLabEndpointUseCaseFileCandidate> files,
            List<GitLabEndpointUseCaseUnresolvedReference> unresolved
    ) {
        var result = new ArrayList<>(GitLabEndpointUseCaseModelSupport.copy(files));
        var existingPaths = new LinkedHashSet<String>();
        result.stream()
                .map(GitLabEndpointUseCaseFileCandidate::path)
                .filter(path -> path != null)
                .forEach(existingPaths::add);
        for (var reference : GitLabEndpointUseCaseModelSupport.copy(unresolved)) {
            promotedFile(reference, existingPaths).ifPresent(file -> {
                result.add(file);
                existingPaths.add(file.path());
            });
        }
        return result;
    }

    private Optional<GitLabEndpointUseCaseFileCandidate> promotedFile(
            GitLabEndpointUseCaseUnresolvedReference reference,
            Set<String> existingPaths
    ) {
        if (reference == null) {
            return Optional.empty();
        }
        var candidatePaths = GitLabEndpointUseCaseModelSupport.copyStrings(reference.candidates()).stream()
                .map(GitLabEndpointUseCaseModelSupport::normalizeFilePath)
                .filter(this::isPromotableSourceCandidate)
                .distinct()
                .toList();
        if (candidatePaths.size() != 1) {
            return java.util.Optional.empty();
        }
        var path = candidatePaths.get(0);
        if (existingPaths.contains(path)) {
            return Optional.empty();
        }
        var role = roleForPromotedCandidate(path, reference.symbol());
        return Optional.of(new GitLabEndpointUseCaseFileCandidate(
                path,
                role,
                rolePriority(role),
                reference.symbol() != null ? List.of(reference.symbol()) : List.of(),
                promotedReason(reference.reason()),
                GitLabEndpointUseCaseConfidence.MEDIUM
        ));
    }

    private String promotedReason(String unresolvedReason) {
        var reason = GitLabEndpointUseCaseModelSupport.trimToNull(unresolvedReason);
        return reason != null
                ? "Exact source candidate was found but not read before traversal completed. " + reason
                : "Exact source candidate was found but not read before traversal completed.";
    }

    private boolean isPromotableSourceCandidate(String path) {
        if (path == null) {
            return false;
        }
        var normalized = path.toLowerCase();
        return normalized.endsWith(".java")
                && !normalized.contains("/src/test/")
                && (normalized.startsWith("src/main/java/") || normalized.contains("/src/main/java/"));
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

    private GitLabEndpointUseCaseFileRole roleForPromotedCandidate(String path, String symbol) {
        var fileName = simpleFileNameWithoutExtension(path);
        var symbolName = simpleTypeName(symbol);
        var simpleName = fileName != null ? fileName : symbolName;
        var lowerPath = path != null ? path.toLowerCase() : "";
        if (simpleName == null) {
            return GitLabEndpointUseCaseFileRole.UNKNOWN;
        }
        if (simpleName.endsWith("Mapper") || simpleName.endsWith("Mapping")) {
            return GitLabEndpointUseCaseFileRole.MAPPER;
        }
        if (simpleName.endsWith("Api")) {
            return GitLabEndpointUseCaseFileRole.API_INTERFACE;
        }
        if (simpleName.contains("RepositoryPort")) {
            return GitLabEndpointUseCaseFileRole.REPOSITORY_PORT;
        }
        if (simpleName.endsWith("Port")) {
            return GitLabEndpointUseCaseFileRole.USE_CASE_PORT;
        }
        if (simpleName.endsWith("Service") || simpleName.endsWith("UseCase")) {
            return GitLabEndpointUseCaseFileRole.USE_CASE_SERVICE;
        }
        if (simpleName.contains("Repository")) {
            return GitLabEndpointUseCaseFileRole.REPOSITORY_IMPLEMENTATION;
        }
        if (simpleName.endsWith("Client")) {
            return GitLabEndpointUseCaseFileRole.EXTERNAL_CLIENT;
        }
        if (simpleName.contains("WebModel") || simpleName.endsWith("Dto")
                || simpleName.endsWith("Request") || simpleName.endsWith("Response")) {
            return GitLabEndpointUseCaseFileRole.WEB_MODEL;
        }
        if (simpleName.contains("FormView") || simpleName.endsWith("Projection") || simpleName.endsWith("View")) {
            return GitLabEndpointUseCaseFileRole.PROJECTION;
        }
        if (lowerPath.contains("/config/") || lowerPath.contains("/configuration/")) {
            return GitLabEndpointUseCaseFileRole.CONFIGURATION;
        }
        if (symbolName != null && symbolName.endsWith("Mapper")) {
            return GitLabEndpointUseCaseFileRole.MAPPER;
        }
        return GitLabEndpointUseCaseFileRole.DOMAIN_MODEL;
    }

    private String simpleFileNameWithoutExtension(String path) {
        var normalized = GitLabEndpointUseCaseModelSupport.normalizeFilePath(path);
        if (normalized == null) {
            return null;
        }
        var slashIndex = normalized.lastIndexOf('/');
        var fileName = slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
        return fileName.endsWith(".java")
                ? fileName.substring(0, fileName.length() - ".java".length())
                : fileName;
    }

    private String simpleTypeName(String symbol) {
        var normalized = GitLabEndpointUseCaseModelSupport.trimToNull(symbol);
        if (normalized == null) {
            return null;
        }
        var hashIndex = normalized.indexOf('#');
        if (hashIndex >= 0) {
            normalized = normalized.substring(0, hashIndex);
        }
        var dotIndex = normalized.lastIndexOf('.');
        return dotIndex >= 0 ? normalized.substring(dotIndex + 1) : normalized;
    }

    private List<String> deduplicate(List<String> values) {
        return List.copyOf(new LinkedHashSet<>(GitLabEndpointUseCaseModelSupport.copyStrings(values)));
    }

    private final class MutableFile {

        private final String path;
        private final LinkedHashSet<String> symbols = new LinkedHashSet<>();
        private final LinkedHashMap<String, GitLabEndpointUseCaseMethodCandidate> methods = new LinkedHashMap<>();
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
            file.methods().stream()
                    .filter(method -> method.methodName() != null)
                    .forEach(method -> {
                        methods.putIfAbsent(method.deduplicationKey(), method);
                        symbols.add(method.methodName());
                    });
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
                    methods.values().stream()
                            .sorted(Comparator
                                    .comparingInt(GitLabEndpointUseCaseMethodCandidate::depth)
                                    .thenComparingInt(GitLabEndpointUseCaseMethodCandidate::lineStart)
                                    .thenComparing(
                                            GitLabEndpointUseCaseMethodCandidate::methodName,
                                            Comparator.nullsLast(String::compareTo)))
                            .toList(),
                    String.join(" ", reasons),
                    confidence
            );
        }
    }
}
