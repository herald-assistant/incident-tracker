package pl.mkn.tdw.integrations.gitlab.usecase;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParseStart;
import com.github.javaparser.Providers;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFile;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositoryPort;
import pl.mkn.tdw.integrations.gitlab.GitLabRepositorySearchQuery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class GitLabEndpointUseCaseSourceSession {

    public static final int DEFAULT_MAX_CHARACTERS_PER_FILE = 120_000;

    private final GitLabRepositoryPort repositoryPort;
    private final GitLabEndpointUseCaseRepositoryContext repository;
    private final List<String> pathPrefixes;
    private final int maxReadFiles;
    private final int maxCharactersPerFile;
    private final JavaParser javaParser;
    private final Map<String, GitLabEndpointUseCaseSourceFile> sourceFiles = new HashMap<>();
    private final Map<String, GitLabEndpointUseCaseParsedSourceFile> parsedSourceFiles = new HashMap<>();
    private final Map<String, List<GitLabRepositoryFile>> repositoryFiles = new HashMap<>();
    private final Map<String, List<GitLabRepositoryFileCandidate>> repositorySearches = new HashMap<>();
    private int readFileCount;
    private boolean readFileLimitReached;

    public GitLabEndpointUseCaseSourceSession(
            GitLabRepositoryPort repositoryPort,
            GitLabEndpointUseCaseRepositoryContext repository
    ) {
        this(
                repositoryPort,
                repository,
                List.of(),
                GitLabEndpointUseCaseLimits.DEFAULT_MAX_READ_FILES,
                DEFAULT_MAX_CHARACTERS_PER_FILE
        );
    }

    public GitLabEndpointUseCaseSourceSession(
            GitLabRepositoryPort repositoryPort,
            GitLabEndpointUseCaseRepositoryContext repository,
            int maxReadFiles,
            int maxCharactersPerFile
    ) {
        this(repositoryPort, repository, List.of(), maxReadFiles, maxCharactersPerFile);
    }

    public GitLabEndpointUseCaseSourceSession(
            GitLabRepositoryPort repositoryPort,
            GitLabEndpointUseCaseRepositoryContext repository,
            List<String> pathPrefixes,
            int maxReadFiles,
            int maxCharactersPerFile
    ) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.pathPrefixes = normalizePathPrefixes(pathPrefixes);
        this.maxReadFiles = maxReadFiles < 1 ? GitLabEndpointUseCaseLimits.DEFAULT_MAX_READ_FILES : maxReadFiles;
        this.maxCharactersPerFile = maxCharactersPerFile < 1
                ? DEFAULT_MAX_CHARACTERS_PER_FILE
                : maxCharactersPerFile;
        this.javaParser = new JavaParser(new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                .setStoreTokens(true));
    }

    public List<GitLabRepositoryFile> listRepositoryFiles(String pathPrefix) {
        var normalizedPathPrefix = normalizeOptionalPath(pathPrefix);
        var cacheKey = cacheKey("tree", normalizedPathPrefix != null ? normalizedPathPrefix : "<root>");
        return repositoryFiles.computeIfAbsent(cacheKey, ignored -> {
            if (pathPrefixes.isEmpty()) {
                return listRepositoryFilesWithinPrefix(normalizedPathPrefix);
            }

            var files = new LinkedHashMap<String, GitLabRepositoryFile>();
            for (var effectivePrefix : effectiveListPrefixes(normalizedPathPrefix)) {
                listRepositoryFilesWithinPrefix(effectivePrefix).stream()
                        .filter(file -> file != null && pathWithinPrefixes(file.filePath()))
                        .forEach(file -> files.putIfAbsent(file.filePath(), file));
            }
            return List.copyOf(files.values());
        });
    }

    public List<GitLabRepositoryFile> listRepositoryFiles() {
        return listRepositoryFiles(null);
    }

    public List<GitLabRepositoryFileCandidate> searchCandidateFiles(List<String> keywords) {
        var normalizedKeywords = keywords != null
                ? keywords.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList()
                : List.<String>of();
        if (normalizedKeywords.isEmpty()) {
            return List.of();
        }

        var cacheKey = cacheKey("search", String.join("\u001F", normalizedKeywords));
        return repositorySearches.computeIfAbsent(cacheKey, ignored -> {
            var result = repositoryPort.searchCandidateFiles(new GitLabRepositorySearchQuery(
                    null,
                    repository.group(),
                    repository.branch(),
                    List.of(repository.projectName()),
                    List.of(),
                    normalizedKeywords,
                    pathPrefixes
            ));
            return result != null
                    ? result.stream()
                    .filter(candidate -> candidate != null && pathWithinPrefixes(candidate.filePath()))
                    .toList()
                    : List.of();
        });
    }

    public GitLabEndpointUseCaseSourceFile readFile(String path) {
        var normalizedPath = GitLabEndpointUseCaseModelSupport.normalizeFilePath(path);
        if (!StringUtils.hasText(normalizedPath)) {
            return new GitLabEndpointUseCaseSourceFile(
                    path,
                    "",
                    false,
                    false,
                    List.of("Source file path is required.")
            );
        }

        var cacheKey = cacheKey("source", normalizedPath, Integer.toString(maxCharactersPerFile));
        var cached = sourceFiles.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        if (readFileCount >= maxReadFiles) {
            readFileLimitReached = true;
            var limitReached = new GitLabEndpointUseCaseSourceFile(
                    normalizedPath,
                    "",
                    false,
                    false,
                    List.of("Source read file limit reached before reading " + normalizedPath + ".")
            );
            sourceFiles.put(cacheKey, limitReached);
            return limitReached;
        }

        readFileCount++;
        var limitations = new ArrayList<String>(focusedReadBoundaryLimitations(normalizedPath));
        try {
            var content = repositoryPort.readFile(
                    repository.group(),
                    repository.projectName(),
                    repository.branch(),
                    normalizedPath,
                    maxCharactersPerFile
            );
            if (content.truncated()) {
                limitations.add("Source file content was truncated before Java parsing: " + normalizedPath + ".");
            }
            var sourceFile = new GitLabEndpointUseCaseSourceFile(
                    normalizedPath,
                    content.content(),
                    content.truncated(),
                    true,
                    limitations
            );
            sourceFiles.put(cacheKey, sourceFile);
            return sourceFile;
        } catch (RuntimeException exception) {
            limitations.add("Could not read source file " + normalizedPath + ": " + safeMessage(exception));
            var failed = new GitLabEndpointUseCaseSourceFile(
                    normalizedPath,
                    "",
                    false,
                    false,
                    limitations
            );
            sourceFiles.put(cacheKey, failed);
            return failed;
        }
    }

    public GitLabEndpointUseCaseParsedSourceFile parseJava(String path) {
        var sourceFile = readFile(path);
        if (!sourceFile.readSuccessful()) {
            return new GitLabEndpointUseCaseParsedSourceFile(
                    sourceFile,
                    null,
                    false,
                    sourceFile.limitations()
            );
        }

        var cacheKey = cacheKey("ast", sourceFile.path(), contentHash(sourceFile.content()));
        var cached = parsedSourceFiles.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        var limitations = new ArrayList<String>(sourceFile.limitations());
        try {
            var parseResult = javaParser.parse(
                    ParseStart.COMPILATION_UNIT,
                    Providers.provider(sourceFile.content())
            );
            parseResult.getProblems().stream()
                    .map(problem -> "Could not parse Java source " + sourceFile.path() + ": " + problem.getMessage())
                    .forEach(limitations::add);
            var compilationUnit = parseResult.getResult().orElse(null);
            var parsed = new GitLabEndpointUseCaseParsedSourceFile(
                    sourceFile,
                    compilationUnit,
                    parseResult.isSuccessful() && compilationUnit != null,
                    limitations
            );
            parsedSourceFiles.put(cacheKey, parsed);
            return parsed;
        } catch (RuntimeException exception) {
            limitations.add("Could not parse Java source " + sourceFile.path() + ": " + safeMessage(exception));
            var parsed = new GitLabEndpointUseCaseParsedSourceFile(
                    sourceFile,
                    null,
                    false,
                    limitations
            );
            parsedSourceFiles.put(cacheKey, parsed);
            return parsed;
        }
    }

    public GitLabEndpointUseCaseLimits limitsSnapshot(int maxDepth, int maxFiles) {
        return new GitLabEndpointUseCaseLimits(
                maxDepth,
                maxFiles,
                maxReadFiles,
                false,
                false,
                readFileCount,
                readFileLimitReached
        );
    }

    public GitLabEndpointUseCaseRepositoryContext repository() {
        return repository;
    }

    public int maxReadFiles() {
        return maxReadFiles;
    }

    public int maxCharactersPerFile() {
        return maxCharactersPerFile;
    }

    public int readFileCount() {
        return readFileCount;
    }

    public boolean readFileLimitReached() {
        return readFileLimitReached;
    }

    public List<String> pathPrefixes() {
        return pathPrefixes;
    }

    private List<GitLabRepositoryFile> listRepositoryFilesWithinPrefix(String pathPrefix) {
        var result = repositoryPort.listRepositoryFiles(
                repository.group(),
                repository.projectName(),
                repository.branch(),
                pathPrefix
        );
        return result != null ? List.copyOf(result) : List.of();
    }

    private List<String> effectiveListPrefixes(String requestedPrefix) {
        if (!StringUtils.hasText(requestedPrefix)) {
            return pathPrefixes;
        }
        if (pathWithinPrefixes(requestedPrefix)) {
            return List.of(requestedPrefix);
        }
        return pathPrefixes.stream()
                .filter(prefix -> pathWithinPrefix(prefix, requestedPrefix))
                .toList();
    }

    private String normalizeOptionalPath(String pathPrefix) {
        if (!StringUtils.hasText(pathPrefix)) {
            return null;
        }
        var normalized = GitLabEndpointUseCaseModelSupport.normalizeFilePath(pathPrefix);
        while (normalized != null && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private List<String> normalizePathPrefixes(List<String> pathPrefixes) {
        if (pathPrefixes == null || pathPrefixes.isEmpty()) {
            return List.of();
        }
        var normalized = new LinkedHashSet<String>();
        for (var prefix : pathPrefixes) {
            var value = normalizeOptionalPath(prefix);
            if (StringUtils.hasText(value)) {
                normalized.add(value);
            }
        }
        return List.copyOf(normalized);
    }

    private boolean pathWithinPrefixes(String path) {
        if (pathPrefixes.isEmpty()) {
            return true;
        }
        var normalizedPath = GitLabEndpointUseCaseModelSupport.normalizeFilePath(path);
        if (!StringUtils.hasText(normalizedPath)) {
            return false;
        }
        return pathPrefixes.stream().anyMatch(prefix -> pathWithinPrefix(normalizedPath, prefix));
    }

    private boolean pathWithinPrefix(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private List<String> focusedReadBoundaryLimitations(String path) {
        if (pathPrefixes.isEmpty() || pathWithinPrefixes(path)) {
            return List.of();
        }
        return List.of("Source file " + path
                + " is outside default repository discovery scope and was read because it was explicitly requested. "
                + "Default discovery pathPrefixes: " + String.join(", ", pathPrefixes) + ".");
    }

    private String cacheKey(String type, String... parts) {
        var key = new StringBuilder(type)
                .append('|')
                .append(repository.group())
                .append('|')
                .append(repository.projectName())
                .append('|')
                .append(repository.branch())
                .append('|')
                .append(String.join("\u001E", pathPrefixes));
        for (var part : parts) {
            key.append('|').append(part);
        }
        return key.toString();
    }

    private String contentHash(String content) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            return Integer.toHexString(content.hashCode());
        }
    }

    private String safeMessage(RuntimeException exception) {
        return StringUtils.hasText(exception.getMessage())
                ? exception.getMessage()
                : exception.getClass().getSimpleName();
    }
}
