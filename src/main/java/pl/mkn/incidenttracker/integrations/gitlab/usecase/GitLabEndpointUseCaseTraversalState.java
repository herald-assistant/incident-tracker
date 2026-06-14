package pl.mkn.incidenttracker.integrations.gitlab.usecase;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class GitLabEndpointUseCaseTraversalState {

    private final GitLabEndpointUseCaseRepositoryContext repository;
    private final GitLabEndpointUseCaseEndpointContext endpoint;
    private final int maxDepth;
    private final int maxFiles;
    private final Map<String, MutableFileCandidate> files = new LinkedHashMap<>();
    private final Map<String, GitLabEndpointUseCaseRelation> relations = new LinkedHashMap<>();
    private final Map<String, GitLabEndpointUseCaseUnresolvedReference> unresolved = new LinkedHashMap<>();
    private final LinkedHashSet<String> limitations = new LinkedHashSet<>();
    private final ArrayDeque<GitLabEndpointUseCaseTraversalNode> queue = new ArrayDeque<>();
    private final Set<String> visited = new LinkedHashSet<>();
    private boolean maxDepthReached;

    GitLabEndpointUseCaseTraversalState(
            GitLabEndpointUseCaseRepositoryContext repository,
            GitLabEndpointUseCaseEndpointContext endpoint,
            int maxDepth,
            int maxFiles
    ) {
        this.repository = repository;
        this.endpoint = endpoint;
        this.maxDepth = Math.max(1, maxDepth);
        this.maxFiles = Math.max(1, maxFiles);
    }

    void addLimitation(String limitation) {
        var normalized = GitLabEndpointUseCaseModelSupport.trimToNull(limitation);
        if (normalized != null) {
            limitations.add(normalized);
        }
    }

    void addLimitations(List<String> values) {
        GitLabEndpointUseCaseModelSupport.copyStrings(values).forEach(this::addLimitation);
    }

    void addFile(
            String path,
            GitLabEndpointUseCaseFileRole role,
            String symbol,
            String reason,
            GitLabEndpointUseCaseConfidence confidence
    ) {
        var normalizedPath = GitLabEndpointUseCaseModelSupport.normalizeFilePath(path);
        if (normalizedPath == null) {
            return;
        }
        var existing = files.get(normalizedPath);
        if (existing == null) {
            existing = new MutableFileCandidate(
                    normalizedPath,
                    role != null ? role : GitLabEndpointUseCaseFileRole.UNKNOWN,
                    rolePriority(role),
                    confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW
            );
            files.put(normalizedPath, existing);
        }
        existing.mergeRole(role);
        existing.addSymbol(symbol);
        existing.addReason(reason);
        existing.upgradeConfidence(confidence);
    }

    void addRelation(
            String from,
            String to,
            GitLabEndpointUseCaseRelationKind kind,
            GitLabEndpointUseCaseConfidence confidence,
            String reason
    ) {
        var relation = new GitLabEndpointUseCaseRelation(from, to, kind, confidence, reason);
        if (relation.from() == null || relation.to() == null) {
            return;
        }
        relations.putIfAbsent(
                relation.from() + "|" + relation.to() + "|" + relation.kind(),
                relation
        );
    }

    void addUnresolved(
            String symbol,
            String ownerPath,
            String reason,
            List<String> searchedKeywords,
            List<String> candidates
    ) {
        var unresolvedReference = new GitLabEndpointUseCaseUnresolvedReference(
                symbol,
                ownerPath,
                reason,
                searchedKeywords,
                candidates
        );
        if (unresolvedReference.symbol() == null && unresolvedReference.reason() == null) {
            return;
        }
        unresolved.putIfAbsent(
                unresolvedReference.symbol() + "|" + unresolvedReference.ownerPath() + "|" + unresolvedReference.reason(),
                unresolvedReference
        );
    }

    void enqueue(GitLabEndpointUseCaseTraversalNode node) {
        if (node == null || node.filePath() == null || node.methodName() == null) {
            return;
        }
        if (node.depth() > maxDepth) {
            maxDepthReached = true;
            limitations.add("Traversal reached maxDepth limit before visiting " + node.typeName() + "#" + node.methodName() + ".");
            return;
        }
        if (!visited.contains(node.key())) {
            queue.addLast(node);
        }
    }

    GitLabEndpointUseCaseTraversalNode poll() {
        return queue.pollFirst();
    }

    boolean markVisited(GitLabEndpointUseCaseTraversalNode node) {
        return node != null && visited.add(node.key());
    }

    GitLabEndpointUseCaseContextResult toResult(GitLabEndpointUseCaseSourceSession session) {
        var sortedFiles = files.values().stream()
                .sorted(Comparator
                        .comparingInt(MutableFileCandidate::priority)
                        .thenComparing((MutableFileCandidate file) -> confidenceRank(file.confidence()), Comparator.reverseOrder())
                        .thenComparing(MutableFileCandidate::path))
                .map(MutableFileCandidate::toCandidate)
                .toList();
        var suggestedNextReads = sortedFiles.stream()
                .map(file -> "%s:%s via gitlab_read_repository_file_outline".formatted(
                        repository.projectName(),
                        file.path()
                ))
                .toList();
        var limits = new GitLabEndpointUseCaseLimits(
                maxDepth,
                maxFiles,
                session.maxReadFiles(),
                maxDepthReached,
                false,
                session.readFileCount(),
                session.readFileLimitReached()
        );
        return new GitLabEndpointUseCaseContextResult(
                repository,
                endpoint,
                sortedFiles,
                relations.values().stream().toList(),
                unresolved.values().stream().toList(),
                new ArrayList<>(limitations),
                suggestedNextReads,
                limits,
                globalConfidence(sortedFiles)
        );
    }

    private GitLabEndpointUseCaseConfidence globalConfidence(List<GitLabEndpointUseCaseFileCandidate> sortedFiles) {
        if (sortedFiles.isEmpty()) {
            return GitLabEndpointUseCaseConfidence.LOW;
        }
        if (!unresolved.isEmpty() || maxDepthReached) {
            return GitLabEndpointUseCaseConfidence.MEDIUM;
        }
        if (sortedFiles.stream().anyMatch(file -> file.confidence() == GitLabEndpointUseCaseConfidence.LOW)) {
            return GitLabEndpointUseCaseConfidence.LOW;
        }
        if (sortedFiles.stream().anyMatch(file -> file.confidence() == GitLabEndpointUseCaseConfidence.MEDIUM)) {
            return GitLabEndpointUseCaseConfidence.MEDIUM;
        }
        return GitLabEndpointUseCaseConfidence.HIGH;
    }

    private static int rolePriority(GitLabEndpointUseCaseFileRole role) {
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

    private static int confidenceRank(GitLabEndpointUseCaseConfidence confidence) {
        return switch (confidence != null ? confidence : GitLabEndpointUseCaseConfidence.LOW) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
        };
    }

    private static final class MutableFileCandidate {

        private final String path;
        private final LinkedHashSet<String> symbols = new LinkedHashSet<>();
        private final LinkedHashSet<String> reasons = new LinkedHashSet<>();
        private GitLabEndpointUseCaseFileRole role;
        private int priority;
        private GitLabEndpointUseCaseConfidence confidence;

        private MutableFileCandidate(
                String path,
                GitLabEndpointUseCaseFileRole role,
                int priority,
                GitLabEndpointUseCaseConfidence confidence
        ) {
            this.path = path;
            this.role = role;
            this.priority = priority;
            this.confidence = confidence;
        }

        private String path() {
            return path;
        }

        private int priority() {
            return priority;
        }

        private GitLabEndpointUseCaseConfidence confidence() {
            return confidence;
        }

        private void mergeRole(GitLabEndpointUseCaseFileRole nextRole) {
            var nextPriority = rolePriority(nextRole);
            if (nextPriority < priority) {
                role = nextRole != null ? nextRole : GitLabEndpointUseCaseFileRole.UNKNOWN;
                priority = nextPriority;
            }
        }

        private void addSymbol(String symbol) {
            var normalized = GitLabEndpointUseCaseModelSupport.trimToNull(symbol);
            if (normalized != null) {
                symbols.add(normalized);
            }
        }

        private void addReason(String reason) {
            var normalized = GitLabEndpointUseCaseModelSupport.trimToNull(reason);
            if (normalized != null) {
                reasons.add(normalized);
            }
        }

        private void upgradeConfidence(GitLabEndpointUseCaseConfidence nextConfidence) {
            if (confidenceRank(nextConfidence) > confidenceRank(confidence)) {
                confidence = nextConfidence;
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
