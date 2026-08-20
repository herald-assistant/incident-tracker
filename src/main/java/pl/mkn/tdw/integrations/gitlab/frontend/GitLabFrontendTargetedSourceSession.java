package pl.mkn.tdw.integrations.gitlab.frontend;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class GitLabFrontendTargetedSourceSession {

    private final GitLabRepositoryPort repositoryPort;
    private final GitLabFrontendRepositoryScope scope;
    private final GitLabFrontendGraphLimits limits;
    private final boolean enforceTraversalBudget;
    private final Map<String, String> sourceCache = new LinkedHashMap<>();
    private final Set<String> missingSources = new LinkedHashSet<>();
    private final Set<String> routeFiles = new LinkedHashSet<>();
    private final Set<String> contextFiles = new LinkedHashSet<>();
    private final Set<String> diagnosticKeys = new LinkedHashSet<>();
    private final List<GitLabFrontendGraphDiagnostic> diagnostics = new ArrayList<>();
    private int sourceReadCount;
    private int aliasResolutionCount;
    private int totalCharacters;
    private boolean limitReached;
    private boolean totalCharacterBudgetExhausted;

    GitLabFrontendTargetedSourceSession(
            GitLabRepositoryPort repositoryPort,
            GitLabFrontendRepositoryScope scope,
            GitLabFrontendGraphLimits limits
    ) {
        this(repositoryPort, scope, limits, true);
    }

    GitLabFrontendTargetedSourceSession(
            GitLabRepositoryPort repositoryPort,
            GitLabFrontendRepositoryScope scope,
            GitLabFrontendGraphLimits limits,
            boolean enforceTraversalBudget
    ) {
        this.repositoryPort = repositoryPort;
        this.scope = scope;
        this.limits = limits;
        this.enforceTraversalBudget = enforceTraversalBudget;
    }

    String readOptional(String rawPath) {
        var path = normalize(rawPath);
        if (!inScope(path)) {
            diagnostic(
                    GitLabFrontendDiagnosticSeverity.WARNING,
                    GitLabFrontendGraphDiagnosticCode.IMPORT_TARGET_OUT_OF_SCOPE,
                    "Targeted source path is outside the configured code-search scope.",
                    path
            );
            return null;
        }
        if (sourceCache.containsKey(path)) {
            return sourceCache.get(path);
        }
        if (missingSources.contains(path) || enforceTraversalBudget && (totalCharacterBudgetExhausted
                || limitReached && sourceReadCount >= limits.maxSourceReads())) {
            return null;
        }
        if (enforceTraversalBudget && sourceReadCount >= limits.maxSourceReads()) {
            limitReached = true;
            diagnostic(
                    GitLabFrontendDiagnosticSeverity.WARNING,
                    GitLabFrontendGraphDiagnosticCode.SOURCE_READ_LIMIT_REACHED,
                    "Targeted traversal reached maxSourceReads=" + limits.maxSourceReads() + ".",
                    path
            );
            return null;
        }
        sourceReadCount++;
        try {
            var file = repositoryPort.readFile(
                    scope.group(),
                    scope.projectName(),
                    scope.ref(),
                    path,
                    limits.maxFileCharacters()
            );
            if (file == null || file.content() == null) {
                missingSources.add(path);
                return null;
            }
            if (file.truncated() || file.content().length() > limits.maxFileCharacters()) {
                limitReached = true;
                missingSources.add(path);
                diagnostic(
                        GitLabFrontendDiagnosticSeverity.WARNING,
                        GitLabFrontendGraphDiagnosticCode.FILE_CHARACTER_LIMIT_REACHED,
                        "Targeted source exceeded maxFileCharacters=" + limits.maxFileCharacters() + ".",
                        path
                );
                return null;
            }
            if (enforceTraversalBudget && totalCharacters + file.content().length() > limits.maxTotalCharacters()) {
                limitReached = true;
                totalCharacterBudgetExhausted = true;
                missingSources.add(path);
                diagnostic(
                        GitLabFrontendDiagnosticSeverity.WARNING,
                        GitLabFrontendGraphDiagnosticCode.TOTAL_CHARACTER_LIMIT_REACHED,
                        "Targeted traversal reached maxTotalCharacters=" + limits.maxTotalCharacters() + ".",
                        path
                );
                return null;
            }
            totalCharacters += file.content().length();
            sourceCache.put(path, file.content());
            return file.content();
        } catch (RuntimeException exception) {
            missingSources.add(path);
            return null;
        }
    }

    String readRequired(String path) {
        var source = readOptional(path);
        if (source == null && !sourceReadBlockedByLimit()) {
            diagnostic(
                    GitLabFrontendDiagnosticSeverity.WARNING,
                    GitLabFrontendGraphDiagnosticCode.SOURCE_READ_FAILED,
                    "Required targeted source could not be read.",
                    normalize(path)
            );
        }
        return source;
    }

    private boolean sourceReadBlockedByLimit() {
        return totalCharacterBudgetExhausted;
    }

    boolean markRouteFile(String path) {
        var normalized = normalize(path);
        if (routeFiles.contains(normalized)) {
            return true;
        }
        if (routeFiles.size() >= limits.maxRouteFiles()) {
            limitReached = true;
            diagnostic(
                    GitLabFrontendDiagnosticSeverity.WARNING,
                    GitLabFrontendGraphDiagnosticCode.ROUTE_FILE_LIMIT_REACHED,
                    "Targeted traversal reached maxRouteFiles=" + limits.maxRouteFiles() + ".",
                    normalized
            );
            return false;
        }
        routeFiles.add(normalized);
        return true;
    }

    boolean markContextFile(String path) {
        var normalized = normalize(path);
        if (contextFiles.contains(normalized)) {
            return true;
        }
        if (contextFiles.size() >= limits.maxContextFiles()) {
            limitReached = true;
            diagnostic(
                    GitLabFrontendDiagnosticSeverity.WARNING,
                    GitLabFrontendGraphDiagnosticCode.CONTEXT_FILE_LIMIT_REACHED,
                    "Selected screen context reached maxContextFiles=" + limits.maxContextFiles() + ".",
                    normalized
            );
            return false;
        }
        contextFiles.add(normalized);
        return true;
    }

    boolean nextAliasResolution(String sourcePath) {
        if (enforceTraversalBudget && aliasResolutionCount >= limits.maxAliasResolutions()) {
            limitReached = true;
            diagnostic(
                    GitLabFrontendDiagnosticSeverity.WARNING,
                    GitLabFrontendGraphDiagnosticCode.ALIAS_RESOLUTION_LIMIT_REACHED,
                    "Targeted traversal reached maxAliasResolutions=" + limits.maxAliasResolutions() + ".",
                    sourcePath
            );
            return false;
        }
        aliasResolutionCount++;
        return true;
    }

    boolean withinImportDepth(int depth, String path) {
        if (depth <= limits.maxImportDepth()) {
            return true;
        }
        limitReached = true;
        diagnostic(
                GitLabFrontendDiagnosticSeverity.WARNING,
                GitLabFrontendGraphDiagnosticCode.IMPORT_DEPTH_LIMIT_REACHED,
                "Targeted traversal reached maxImportDepth=" + limits.maxImportDepth() + ".",
                path
        );
        return false;
    }

    void diagnostic(
            GitLabFrontendDiagnosticSeverity severity,
            GitLabFrontendGraphDiagnosticCode code,
            String message,
            String path
    ) {
        if (isLimit(code)) {
            limitReached = true;
        }
        var normalized = normalize(path);
        var key = isLimit(code)
                ? code + "|" + message
                : code + "|" + normalized + "|" + message;
        if (!diagnosticKeys.add(key)) {
            return;
        }
        diagnostics.add(new GitLabFrontendGraphDiagnostic(
                severity,
                code,
                message,
                null,
                null,
                StringUtils.hasText(normalized)
                        ? new GitLabFrontendSourceReference(normalized, null, null, null)
                        : null
        ));
    }

    private boolean isLimit(GitLabFrontendGraphDiagnosticCode code) {
        return switch (code) {
            case ROOT_CANDIDATE_LIMIT_REACHED,
                    ROUTE_NODE_LIMIT_REACHED,
                    ROUTE_FILE_LIMIT_REACHED,
                    SOURCE_READ_LIMIT_REACHED,
                    ALIAS_RESOLUTION_LIMIT_REACHED,
                    IMPORT_DEPTH_LIMIT_REACHED,
                    COMPONENT_DEPTH_LIMIT_REACHED,
                    CONTEXT_FILE_LIMIT_REACHED,
                    FILE_CHARACTER_LIMIT_REACHED,
                    TOTAL_CHARACTER_LIMIT_REACHED -> true;
            default -> false;
        };
    }

    List<GitLabFrontendGraphDiagnostic> diagnostics() {
        return List.copyOf(diagnostics);
    }

    int sourceReadCount() {
        return sourceReadCount;
    }

    boolean sourceReadBudgetExhausted() {
        return totalCharacterBudgetExhausted
                || limitReached && sourceReadCount >= limits.maxSourceReads();
    }

    int aliasResolutionCount() {
        return aliasResolutionCount;
    }

    int routeFileCount() {
        return routeFiles.size();
    }

    boolean limitReached() {
        return limitReached;
    }

    boolean allows(String rawPath) {
        return inScope(normalize(rawPath));
    }

    private boolean inScope(String path) {
        if (!StringUtils.hasText(path) || path.contains("..") || path.contains("//")) {
            return false;
        }
        if (path.matches("(?:^|.*/)tsconfig(?:\\.[A-Za-z0-9_-]+)?\\.json$")) {
            return true;
        }
        return scope.pathPrefixes().isEmpty()
                || scope.pathPrefixes().stream()
                .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    static String normalize(String rawPath) {
        if (!StringUtils.hasText(rawPath)) {
            return "";
        }
        var segments = new ArrayList<String>();
        for (var segment : rawPath.trim().replace('\\', '/').split("/")) {
            if (!StringUtils.hasText(segment) || ".".equals(segment)) {
                continue;
            }
            if ("..".equals(segment)) {
                if (segments.isEmpty()) {
                    return "";
                }
                segments.remove(segments.size() - 1);
            } else {
                segments.add(segment);
            }
        }
        return String.join("/", segments);
    }
}
