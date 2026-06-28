package pl.mkn.tdw.integrations.gitlab.usecase;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Component
public class GitLabJavaMethodUseCaseEntryResolver {

    private static final int MAX_CLASS_CANDIDATE_FILES = 30;

    private final GitLabJavaSourceResolver sourceResolver;
    private final GitLabJavaMethodLocator methodLocator;

    public GitLabJavaMethodUseCaseEntryResolver(
            GitLabJavaSourceResolver sourceResolver,
            GitLabJavaMethodLocator methodLocator
    ) {
        this.sourceResolver = sourceResolver;
        this.methodLocator = methodLocator;
    }

    GitLabJavaMethodUseCaseEntryResolver() {
        this(new GitLabJavaSourceResolver(), new GitLabJavaMethodLocator());
    }

    public GitLabJavaMethodUseCaseEntryMethod resolve(
            GitLabEndpointUseCaseSourceSession session,
            GitLabJavaMethodUseCaseContextRequest request
    ) {
        if (request == null) {
            return GitLabJavaMethodUseCaseEntryMethod.unresolved(
                    GitLabJavaMethodUseCaseEntryStatus.INVALID_REQUEST,
                    null,
                    null,
                    List.of(),
                    List.of("Java method use-case context request is required.")
            );
        }
        if (session == null) {
            return GitLabJavaMethodUseCaseEntryMethod.unresolved(
                    GitLabJavaMethodUseCaseEntryStatus.INVALID_REQUEST,
                    request.className(),
                    request.methodName(),
                    List.of(),
                    List.of("Source session is required.")
            );
        }
        if (!StringUtils.hasText(request.className())) {
            return invalid(request, "className is required.");
        }
        if (!StringUtils.hasText(request.methodName())) {
            return invalid(request, "methodName is required.");
        }

        var limitations = new ArrayList<String>();
        var candidatePaths = candidatePaths(session, request, limitations);
        if (candidatePaths.isEmpty()) {
            return GitLabJavaMethodUseCaseEntryMethod.unresolved(
                    GitLabJavaMethodUseCaseEntryStatus.NOT_FOUND,
                    request.className(),
                    request.methodName(),
                    List.of(),
                    limitationsWith(limitations, "No source file candidate matched className " + request.className() + ".")
            );
        }

        var resolvedMethods = new ArrayList<GitLabJavaMethodMatch>();
        var candidates = new ArrayList<GitLabJavaMethodUseCaseEntryCandidate>();
        var readFailureCount = 0;
        var parseFailureCount = 0;
        var parsedFileCount = 0;
        var ambiguousMethod = false;

        for (var candidatePath : candidatePaths) {
            var sourceFile = session.readFile(candidatePath);
            if (!sourceFile.readSuccessful()) {
                readFailureCount++;
                limitations.addAll(sourceFile.limitations());
                candidates.add(GitLabJavaMethodUseCaseEntryCandidate.sourceFileCandidate(
                        candidatePath,
                        request.className(),
                        "Source file could not be read."
                ));
                continue;
            }

            var astFile = sourceResolver.astFile(session, candidatePath);
            if (!astFile.parsed()) {
                parseFailureCount++;
                limitations.addAll(astFile.limitations());
                candidates.add(GitLabJavaMethodUseCaseEntryCandidate.sourceFileCandidate(
                        candidatePath,
                        request.className(),
                        "Source file could not be parsed."
                ));
                continue;
            }

            parsedFileCount++;
            var methodResolution = methodLocator.resolveMethodAtLine(
                    astFile,
                    request.className(),
                    request.methodName(),
                    request.lineNumber(),
                    request.parameterCount(),
                    request.parameterTypes()
            );
            limitations.addAll(methodResolution.limitations());
            if (methodResolution.status() == GitLabJavaMethodResolutionStatus.RESOLVED) {
                resolvedMethods.add(methodResolution.method());
                continue;
            }
            if (methodResolution.status() == GitLabJavaMethodResolutionStatus.AMBIGUOUS) {
                ambiguousMethod = true;
            }
            methodResolution.candidates().stream()
                    .map(candidate -> GitLabJavaMethodUseCaseEntryCandidate.from(candidate,
                            "Candidate returned by method resolution: " + methodResolution.status() + "."))
                    .forEach(candidates::add);
        }

        if (resolvedMethods.size() == 1) {
            return GitLabJavaMethodUseCaseEntryMethod.resolved(
                    request.className(),
                    request.methodName(),
                    resolvedMethods.get(0),
                    distinct(limitations)
            );
        }

        if (resolvedMethods.size() > 1) {
            resolvedMethods.stream()
                    .map(method -> GitLabJavaMethodUseCaseEntryCandidate.from(method,
                            "More than one entry method matched the request."))
                    .forEach(candidates::add);
            return unresolved(
                    GitLabJavaMethodUseCaseEntryStatus.AMBIGUOUS,
                    request,
                    candidates,
                    limitationsWith(limitations, "More than one entry method matched the request.")
            );
        }

        if (ambiguousMethod || candidatePaths.size() > 1 && !candidates.isEmpty()) {
            return unresolved(
                    GitLabJavaMethodUseCaseEntryStatus.AMBIGUOUS,
                    request,
                    candidates,
                    limitationsWith(limitations, "Entry method could not be selected unambiguously.")
            );
        }
        if (readFailureCount == candidatePaths.size()) {
            return unresolved(
                    GitLabJavaMethodUseCaseEntryStatus.READ_FAILED,
                    request,
                    candidates,
                    distinct(limitations)
            );
        }
        if (parseFailureCount > 0 && parsedFileCount == 0) {
            return unresolved(
                    GitLabJavaMethodUseCaseEntryStatus.PARSE_FAILED,
                    request,
                    candidates,
                    distinct(limitations)
            );
        }
        return unresolved(
                GitLabJavaMethodUseCaseEntryStatus.NOT_FOUND,
                request,
                candidates,
                limitationsWith(limitations, "Method " + request.methodName() + " was not resolved for className "
                        + request.className() + ".")
        );
    }

    private GitLabJavaMethodUseCaseEntryMethod invalid(
            GitLabJavaMethodUseCaseContextRequest request,
            String limitation
    ) {
        return GitLabJavaMethodUseCaseEntryMethod.unresolved(
                GitLabJavaMethodUseCaseEntryStatus.INVALID_REQUEST,
                request.className(),
                request.methodName(),
                List.of(),
                List.of(limitation)
        );
    }

    private GitLabJavaMethodUseCaseEntryMethod unresolved(
            GitLabJavaMethodUseCaseEntryStatus status,
            GitLabJavaMethodUseCaseContextRequest request,
            List<GitLabJavaMethodUseCaseEntryCandidate> candidates,
            List<String> limitations
    ) {
        return GitLabJavaMethodUseCaseEntryMethod.unresolved(
                status,
                request.className(),
                request.methodName(),
                limitCandidates(candidates, request.maxResults()),
                distinct(limitations)
        );
    }

    private List<String> candidatePaths(
            GitLabEndpointUseCaseSourceSession session,
            GitLabJavaMethodUseCaseContextRequest request,
            List<String> limitations
    ) {
        if (StringUtils.hasText(request.filePath())) {
            return List.of(request.filePath());
        }

        var topLevelName = topLevelTypeName(request.className());
        if (!StringUtils.hasText(topLevelName)) {
            return List.of();
        }
        var qualifiedRelativePath = qualifiedTopLevelPath(request.className(), topLevelName);
        List<String> matches;
        try {
            matches = session.listRepositoryFiles().stream()
                    .map(GitLabRepositoryFile::filePath)
                    .map(GitLabEndpointUseCaseModelSupport::normalizeFilePath)
                    .filter(StringUtils::hasText)
                    .filter(path -> matchesTypePath(path, topLevelName, qualifiedRelativePath))
                    .distinct()
                    .sorted()
                    .toList();
        } catch (RuntimeException exception) {
            limitations.add("Could not list repository files before resolving className "
                    + request.className() + ": " + safeMessage(exception));
            return List.of();
        }

        if (matches.size() > MAX_CLASS_CANDIDATE_FILES) {
            limitations.add("Class lookup matched " + matches.size()
                    + " source files; only first " + MAX_CLASS_CANDIDATE_FILES + " candidates are inspected.");
            return matches.stream()
                    .limit(MAX_CLASS_CANDIDATE_FILES)
                    .toList();
        }
        return matches;
    }

    private boolean matchesTypePath(String path, String topLevelName, String qualifiedRelativePath) {
        if (qualifiedRelativePath != null) {
            return path.equals(qualifiedRelativePath) || path.endsWith("/" + qualifiedRelativePath);
        }
        var fileName = topLevelName + ".java";
        return path.equals(fileName) || path.endsWith("/" + fileName);
    }

    private String qualifiedTopLevelPath(String className, String topLevelName) {
        var parts = typeNameParts(className);
        var topLevelIndex = firstTypeSegmentIndex(parts);
        if (topLevelIndex <= 0) {
            return null;
        }
        return String.join("/", parts.subList(0, topLevelIndex)) + "/" + topLevelName + ".java";
    }

    private String topLevelTypeName(String className) {
        var parts = typeNameParts(className);
        if (parts.isEmpty()) {
            return null;
        }
        var topLevelIndex = firstTypeSegmentIndex(parts);
        return topLevelIndex >= 0 ? parts.get(topLevelIndex) : parts.get(parts.size() - 1);
    }

    private int firstTypeSegmentIndex(List<String> parts) {
        for (var index = 0; index < parts.size(); index++) {
            var part = parts.get(index);
            if (StringUtils.hasText(part) && Character.isUpperCase(part.charAt(0))) {
                return index;
            }
        }
        return -1;
    }

    private List<String> typeNameParts(String className) {
        var normalized = GitLabEndpointUseCaseModelSupport.trimToNull(className);
        if (normalized == null) {
            return List.of();
        }
        if (normalized.endsWith(".class")) {
            normalized = normalized.substring(0, normalized.length() - ".class".length());
        }
        var genericIndex = normalized.indexOf('<');
        if (genericIndex >= 0) {
            normalized = normalized.substring(0, genericIndex);
        }
        while (normalized.endsWith("[]")) {
            normalized = normalized.substring(0, normalized.length() - 2);
        }
        normalized = normalized.replace('$', '.').trim();
        if (!StringUtils.hasText(normalized)) {
            return List.of();
        }
        return List.of(normalized.split("\\.")).stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
    }

    private List<GitLabJavaMethodUseCaseEntryCandidate> limitCandidates(
            List<GitLabJavaMethodUseCaseEntryCandidate> candidates,
            int maxResults
    ) {
        return distinctCandidates(candidates).stream()
                .sorted(Comparator
                        .comparing(GitLabJavaMethodUseCaseEntryCandidate::filePath, Comparator.nullsLast(String::compareTo))
                        .thenComparingInt(GitLabJavaMethodUseCaseEntryCandidate::lineStart)
                        .thenComparing(candidate -> candidate.signature() != null ? candidate.signature() : ""))
                .limit(maxResults)
                .toList();
    }

    private List<GitLabJavaMethodUseCaseEntryCandidate> distinctCandidates(
            List<GitLabJavaMethodUseCaseEntryCandidate> candidates
    ) {
        var distinct = new LinkedHashMap<String, GitLabJavaMethodUseCaseEntryCandidate>();
        for (var candidate : candidates != null ? candidates : List.<GitLabJavaMethodUseCaseEntryCandidate>of()) {
            if (candidate == null) {
                continue;
            }
            distinct.putIfAbsent(candidateKey(candidate), candidate);
        }
        return List.copyOf(distinct.values());
    }

    private String candidateKey(GitLabJavaMethodUseCaseEntryCandidate candidate) {
        return "%s|%s|%s|%d|%d|%s".formatted(
                candidate.filePath(),
                candidate.declaringTypeQualifiedName(),
                candidate.methodName(),
                candidate.lineStart(),
                candidate.lineEnd(),
                String.join(",", candidate.parameterTypes())
        );
    }

    private List<String> limitationsWith(List<String> limitations, String limitation) {
        var values = new ArrayList<>(limitations != null ? limitations : List.<String>of());
        values.add(limitation);
        return distinct(values);
    }

    private List<String> distinct(List<String> values) {
        var distinct = new LinkedHashMap<String, String>();
        for (var value : values != null ? values : List.<String>of()) {
            var normalized = GitLabEndpointUseCaseModelSupport.trimToNull(value);
            if (normalized != null) {
                distinct.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
            }
        }
        return List.copyOf(distinct.values());
    }

    private String safeMessage(RuntimeException exception) {
        return StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
    }
}
