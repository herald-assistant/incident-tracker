package pl.mkn.incidenttracker.analysis.evidence.provider.gitlabdeterministic;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabProperties;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryFileChunk;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryFileContent;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryProjectCandidate;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.source.GitLabSourceResolveMatch;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.source.GitLabSourceResolveRequest;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.source.GitLabSourceResolveService;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.source.GitLabSourceResolveSession;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceReference;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisStepPhase;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextEvidenceView;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextResolver;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.ResolvedDeploymentContext;
import pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch.ElasticLogEvidenceView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
@Slf4j
@RequiredArgsConstructor
public class GitLabDeterministicEvidenceProvider implements AnalysisEvidenceProvider {

    private static final Pattern STACKTRACE_FRAME_PATTERN = Pattern.compile(
            "at\\s+([A-Za-z0-9_$.]+)\\.[A-Za-z0-9_$<>]+\\(([^():]+\\.java):(\\d+)\\)"
    );
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
            "([A-Za-z0-9._-]+(?:/[A-Za-z0-9._-]+)+\\.(?:java|kt|groovy))(?::(\\d+))?"
    );
    private static final int DEFAULT_MAX_CONTENT_CHARACTERS = 4_000;
    private static final int DEFAULT_CHUNK_RADIUS = 20;
    private static final List<String> LOW_VALUE_CLASS_SUFFIXES = List.of("ExceptionHandler", "Filter", "Logger");
    private static final List<String> IGNORED_STACKTRACE_PREFIXES = List.of(
            "java.",
            "javax.",
            "jakarta.",
            "jdk.",
            "sun.",
            "com.sun.",
            "org.springframework.",
            "org.apache.",
            "org.hibernate.",
            "org.slf4j.",
            "ch.qos.logback.",
            "io.micrometer.",
            "reactor.",
            "kotlin.",
            "groovy."
    );

    private final GitLabRepositoryPort gitLabRepositoryPort;
    private final GitLabProperties gitLabProperties;
    private final GitLabSourceResolveService gitLabSourceResolveService;
    private final DeploymentContextResolver deploymentContextResolver;

    @Override
    public AnalysisEvidenceSection collect(AnalysisContext context) {
        var logEvidence = ElasticLogEvidenceView.from(context);
        if (logEvidence.isEmpty() || !isGitLabConfigured()) {
            return emptySection();
        }

        var items = resolveCodeItems(logEvidence, DeploymentContextEvidenceView.from(context));
        return new AnalysisEvidenceSection(
                producedEvidence().provider(),
                producedEvidence().category(),
                List.copyOf(items)
        );
    }

    @Override
    public AnalysisEvidenceReference producedEvidence() {
        return GitLabResolvedCodeEvidenceView.EVIDENCE_REFERENCE;
    }

    @Override
    public List<AnalysisEvidenceReference> consumedEvidence() {
        return List.of(
                ElasticLogEvidenceView.EVIDENCE_REFERENCE,
                DeploymentContextEvidenceView.EVIDENCE_REFERENCE
        );
    }

    @Override
    public AnalysisStepPhase stepPhase() {
        return AnalysisStepPhase.ENRICHMENT;
    }

    @Override
    public String stepCode() {
        return "GITLAB_RESOLVED_CODE";
    }

    @Override
    public String stepLabel() {
        return "Zbieranie danych z repozytorium";
    }

    private AnalysisEvidenceSection emptySection() {
        return new AnalysisEvidenceSection(
                producedEvidence().provider(),
                producedEvidence().category(),
                List.of()
        );
    }

    private List<AnalysisEvidenceItem> resolveCodeItems(
            ElasticLogEvidenceView logEvidence,
            DeploymentContextEvidenceView deploymentContext
    ) {
        var resolvedItems = new LinkedHashMap<String, ResolvedEvidence>();
        var resolveSession = gitLabSourceResolveService.openSession();
        var projectPathCache = new LinkedHashMap<String, List<String>>();

        for (var logEntry : logEvidence.entries()) {
            var deployment = deploymentContext.findFor(logEntry)
                    .orElseGet(() -> deploymentContextResolver.resolve(logEntry));
            if (deployment == null || !StringUtils.hasText(deployment.branch())) {
                continue;
            }

            for (var reference : extractReferences(logEntry)) {
                var resolved = resolveReference(deployment, logEntry, reference, resolveSession, projectPathCache);
                if (resolved == null) {
                    continue;
                }

                resolvedItems.merge(
                        resolved.key(),
                        resolved,
                        (current, candidate) -> candidate.priority() > current.priority() ? candidate : current
                );

                if (resolvedItems.size() >= gitLabProperties.getMaxCandidateCount()) {
                    return resolvedItems.values().stream().map(ResolvedEvidence::item).toList();
                }
            }
        }

        return resolvedItems.values().stream().map(ResolvedEvidence::item).toList();
    }

    private ResolvedEvidence resolveReference(
            ResolvedDeploymentContext deployment,
            ElasticLogEvidenceView.LogEntry logEntry,
            GitLabCodeReference reference,
            GitLabSourceResolveSession resolveSession,
            Map<String, List<String>> projectPathCache
    ) {
        for (var projectName : resolveProjectPaths(logEntry, deployment, projectPathCache)) {
            var resolved = reference.filePath() != null
                    ? resolveDirectFile(deployment, projectName, reference)
                    : resolveSymbolReference(deployment, projectName, reference, resolveSession);

            if (resolved != null) {
                return resolved;
            }
        }

        return null;
    }

    private ResolvedEvidence resolveDirectFile(
            ResolvedDeploymentContext deployment,
            String projectName,
            GitLabCodeReference reference
    ) {
        try {
            if (reference.lineNumber() != null) {
                var chunk = gitLabRepositoryPort.readFileChunk(
                        gitLabProperties.getGroup(),
                        projectName,
                        deployment.branch(),
                        reference.filePath(),
                        Math.max(1, reference.lineNumber() - DEFAULT_CHUNK_RADIUS),
                        reference.lineNumber() + DEFAULT_CHUNK_RADIUS,
                        DEFAULT_MAX_CONTENT_CHARACTERS
                );
                return chunkEvidence(deployment, projectName, reference, reference.filePath(), null, chunk);
            }

            var fileContent = gitLabRepositoryPort.readFile(
                    gitLabProperties.getGroup(),
                    projectName,
                    deployment.branch(),
                    reference.filePath(),
                    DEFAULT_MAX_CONTENT_CHARACTERS
            );
            return fileEvidence(deployment, projectName, reference, reference.filePath(), null, fileContent);
        } catch (RuntimeException exception) {
            log.debug(
                    "GitLab direct file resolution failed group={} projectName={} branch={} filePath={} reason={}",
                    gitLabProperties.getGroup(),
                    projectName,
                    deployment.branch(),
                    reference.filePath(),
                    exception.getMessage()
            );
            return null;
        }
    }

    private ResolvedEvidence resolveSymbolReference(
            ResolvedDeploymentContext deployment,
            String projectName,
            GitLabCodeReference reference,
            GitLabSourceResolveSession resolveSession
    ) {
        for (var symbolCandidate : symbolCandidates(reference.symbol())) {
            try {
                var match = gitLabSourceResolveService.resolveMatch(new GitLabSourceResolveRequest(
                        gitLabProperties.getBaseUrl(),
                        gitLabProperties.getGroup(),
                        projectName,
                        deployment.branch(),
                        symbolCandidate
                ), resolveSession);

                if (reference.lineNumber() != null) {
                    var chunk = gitLabRepositoryPort.readFileChunk(
                            gitLabProperties.getGroup(),
                            projectName,
                            deployment.branch(),
                            match.matchedPath(),
                            Math.max(1, reference.lineNumber() - DEFAULT_CHUNK_RADIUS),
                            reference.lineNumber() + DEFAULT_CHUNK_RADIUS,
                            DEFAULT_MAX_CONTENT_CHARACTERS
                    );
                    return chunkEvidence(deployment, projectName, reference, match.matchedPath(), match, chunk);
                }

                var fileContent = gitLabRepositoryPort.readFile(
                        gitLabProperties.getGroup(),
                        projectName,
                        deployment.branch(),
                        match.matchedPath(),
                        DEFAULT_MAX_CONTENT_CHARACTERS
                );
                return fileEvidence(deployment, projectName, reference, match.matchedPath(), match, fileContent);
            } catch (RuntimeException exception) {
                log.debug(
                        "GitLab symbol resolution failed group={} projectName={} branch={} symbol={} reason={}",
                        gitLabProperties.getGroup(),
                        projectName,
                        deployment.branch(),
                        symbolCandidate,
                        exception.getMessage()
                );
            }
        }

        return null;
    }

    private ResolvedEvidence fileEvidence(
            ResolvedDeploymentContext deployment,
            String projectName,
            GitLabCodeReference reference,
            String filePath,
            GitLabSourceResolveMatch match,
            GitLabRepositoryFileContent fileContent
    ) {
        var attributes = new ArrayList<AnalysisEvidenceAttribute>();
        addBaseCodeAttributes(attributes, deployment, projectName, reference, filePath, match);
        addAttribute(attributes, GitLabResolvedCodeEvidenceView.ATTRIBUTE_CONTENT, fileContent.content());
        addAttribute(
                attributes,
                GitLabResolvedCodeEvidenceView.ATTRIBUTE_CONTENT_TRUNCATED,
                String.valueOf(fileContent.truncated())
        );

        return new ResolvedEvidence(
                evidenceKey(projectName, deployment.branch(), filePath),
                reference.priority(),
                new AnalysisEvidenceItem(projectName + " file " + filePath, List.copyOf(attributes))
        );
    }

    private ResolvedEvidence chunkEvidence(
            ResolvedDeploymentContext deployment,
            String projectName,
            GitLabCodeReference reference,
            String filePath,
            GitLabSourceResolveMatch match,
            GitLabRepositoryFileChunk chunk
    ) {
        var attributes = new ArrayList<AnalysisEvidenceAttribute>();
        addBaseCodeAttributes(attributes, deployment, projectName, reference, filePath, match);
        addAttribute(
                attributes,
                GitLabResolvedCodeEvidenceView.ATTRIBUTE_REQUESTED_START_LINE,
                String.valueOf(chunk.requestedStartLine())
        );
        addAttribute(
                attributes,
                GitLabResolvedCodeEvidenceView.ATTRIBUTE_REQUESTED_END_LINE,
                String.valueOf(chunk.requestedEndLine())
        );
        addAttribute(
                attributes,
                GitLabResolvedCodeEvidenceView.ATTRIBUTE_RETURNED_START_LINE,
                String.valueOf(chunk.returnedStartLine())
        );
        addAttribute(
                attributes,
                GitLabResolvedCodeEvidenceView.ATTRIBUTE_RETURNED_END_LINE,
                String.valueOf(chunk.returnedEndLine())
        );
        addAttribute(
                attributes,
                GitLabResolvedCodeEvidenceView.ATTRIBUTE_TOTAL_LINES,
                String.valueOf(chunk.totalLines())
        );
        addAttribute(attributes, GitLabResolvedCodeEvidenceView.ATTRIBUTE_CONTENT, chunk.content());
        addAttribute(
                attributes,
                GitLabResolvedCodeEvidenceView.ATTRIBUTE_CONTENT_TRUNCATED,
                String.valueOf(chunk.truncated())
        );

        var lineSuffix = reference.lineNumber() != null ? " around line " + reference.lineNumber() : "";
        return new ResolvedEvidence(
                evidenceKey(projectName, deployment.branch(), filePath),
                reference.priority(),
                new AnalysisEvidenceItem(projectName + " file " + filePath + lineSuffix, List.copyOf(attributes))
        );
    }

    private void addBaseCodeAttributes(
            List<AnalysisEvidenceAttribute> attributes,
            ResolvedDeploymentContext deployment,
            String projectName,
            GitLabCodeReference reference,
            String filePath,
            GitLabSourceResolveMatch match
    ) {
        addAttribute(attributes, GitLabResolvedCodeEvidenceView.ATTRIBUTE_ENVIRONMENT, deployment.environment());
        addAttribute(attributes, GitLabResolvedCodeEvidenceView.ATTRIBUTE_BRANCH, deployment.branch());
        addAttribute(attributes, GitLabResolvedCodeEvidenceView.ATTRIBUTE_GROUP, gitLabProperties.getGroup());
        addAttribute(attributes, GitLabResolvedCodeEvidenceView.ATTRIBUTE_PROJECT_NAME, projectName);
        addAttribute(attributes, GitLabResolvedCodeEvidenceView.ATTRIBUTE_FILE_PATH, filePath);
        addAttribute(
                attributes,
                GitLabResolvedCodeEvidenceView.ATTRIBUTE_REFERENCE_TYPE,
                reference.kind().name()
        );
        addAttribute(attributes, GitLabResolvedCodeEvidenceView.ATTRIBUTE_SYMBOL, reference.symbol());
        addAttribute(attributes, GitLabResolvedCodeEvidenceView.ATTRIBUTE_RAW_REFERENCE, reference.rawValue());

        if (reference.lineNumber() != null) {
            addAttribute(
                    attributes,
                    GitLabResolvedCodeEvidenceView.ATTRIBUTE_LINE_NUMBER,
                    String.valueOf(reference.lineNumber())
            );
        }
        if (match != null && match.score() != null) {
            addAttribute(
                    attributes,
                    GitLabResolvedCodeEvidenceView.ATTRIBUTE_RESOLVE_SCORE,
                    String.valueOf(match.score())
            );
        }
    }

    private List<String> resolveProjectPaths(
            ElasticLogEvidenceView.LogEntry logEntry,
            ResolvedDeploymentContext deployment,
            Map<String, List<String>> projectPathCache
    ) {
        var exactProjectPaths = exactProjectCandidates(logEntry, deployment);
        var discoveryHints = projectDiscoveryHints(logEntry, deployment);
        var cacheKey = String.join("|", discoveryHints);

        if (projectPathCache.containsKey(cacheKey)) {
            return projectPathCache.get(cacheKey);
        }

        var resolvedProjectPaths = new LinkedHashSet<String>();
        var searchResults = searchProjectsByHints(discoveryHints);
        for (var searchResult : searchResults) {
            addValue(resolvedProjectPaths, searchResult.projectPath());
        }

        resolvedProjectPaths.addAll(exactProjectPaths);

        var result = List.copyOf(resolvedProjectPaths);
        projectPathCache.put(cacheKey, result);
        return result;
    }

    private List<GitLabRepositoryProjectCandidate> searchProjectsByHints(List<String> projectHints) {
        try {
            var candidates = gitLabRepositoryPort.searchProjects(gitLabProperties.getGroup(), projectHints);
            return candidates != null ? candidates : List.of();
        } catch (RuntimeException exception) {
            log.debug(
                    "GitLab project discovery failed group={} projectHints={} reason={}",
                    gitLabProperties.getGroup(),
                    projectHints,
                    exception.getMessage()
            );
            return List.of();
        }
    }

    private List<String> exactProjectCandidates(
            ElasticLogEvidenceView.LogEntry logEntry,
            ResolvedDeploymentContext deployment
    ) {
        var candidates = new LinkedHashSet<String>();
        addValue(candidates, logEntry.containerName());
        addValue(candidates, deployment.projectNameHint());
        addValue(candidates, normalizeProjectHint(logEntry.containerName()));
        addValue(candidates, normalizeProjectHint(deployment.projectNameHint()));
        return List.copyOf(candidates);
    }

    private List<String> projectDiscoveryHints(
            ElasticLogEvidenceView.LogEntry logEntry,
            ResolvedDeploymentContext deployment
    ) {
        var hints = new LinkedHashSet<String>();
        addProjectHintVariants(hints, logEntry.containerName());
        addProjectHintVariants(hints, deployment.projectNameHint());
        addProjectHintVariants(hints, logEntry.serviceName());
        return List.copyOf(hints);
    }

    private void addProjectHintVariants(LinkedHashSet<String> hints, String rawHint) {
        if (!StringUtils.hasText(rawHint)) {
            return;
        }

        var trimmedHint = rawHint.trim();
        hints.add(trimmedHint);

        var normalizedHint = normalizeProjectHint(trimmedHint);
        addValue(hints, normalizedHint);

        var normalizedGroupPrefix = normalizeProjectHint(groupLeafToken(gitLabProperties.getGroup()));
        if (StringUtils.hasText(normalizedHint) && StringUtils.hasText(normalizedGroupPrefix)) {
            var prefix = normalizedGroupPrefix + "_";
            if (normalizedHint.startsWith(prefix) && normalizedHint.length() > prefix.length()) {
                addValue(hints, normalizedHint.substring(prefix.length()));
            }
        }
    }

    private String groupLeafToken(String group) {
        if (!StringUtils.hasText(group)) {
            return null;
        }

        var trimmedGroup = group.trim();
        var lastSlash = trimmedGroup.lastIndexOf('/');
        return lastSlash >= 0 ? trimmedGroup.substring(lastSlash + 1) : trimmedGroup;
    }

    private List<GitLabCodeReference> extractReferences(ElasticLogEvidenceView.LogEntry logEntry) {
        var references = new LinkedHashMap<String, GitLabCodeReference>();
        registerFullPathReferences(references, logEntry.message());
        registerFullPathReferences(references, logEntry.exception());
        registerStacktraceReferences(references, logEntry.exception());

        if (!hasStacktraceReference(references) && shouldUseClassReference(logEntry.className())) {
            var symbol = logEntry.className().trim();
            references.putIfAbsent(
                    referenceKey(GitLabCodeReferenceKind.CLASS_NAME, symbol, null, null),
                    new GitLabCodeReference(GitLabCodeReferenceKind.CLASS_NAME, symbol, null, null, symbol, 100)
            );
        }

        return references.values().stream()
                .sorted((left, right) -> Integer.compare(right.priority(), left.priority()))
                .limit(Math.max(1, gitLabProperties.getMaxCandidateCount()))
                .toList();
    }

    private void registerFullPathReferences(Map<String, GitLabCodeReference> references, String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return;
        }

        var matcher = FILE_PATH_PATTERN.matcher(rawText);
        while (matcher.find()) {
            var filePath = matcher.group(1);
            var line = matcher.group(2);
            var lineNumber = StringUtils.hasText(line) ? Integer.parseInt(line) : null;
            references.putIfAbsent(
                    referenceKey(GitLabCodeReferenceKind.FULL_PATH, null, filePath, lineNumber),
                    new GitLabCodeReference(
                            GitLabCodeReferenceKind.FULL_PATH,
                            null,
                            filePath,
                            lineNumber,
                            matcher.group(),
                            300
                    )
            );
        }
    }

    private void registerStacktraceReferences(Map<String, GitLabCodeReference> references, String rawException) {
        if (!StringUtils.hasText(rawException)) {
            return;
        }

        var matcher = STACKTRACE_FRAME_PATTERN.matcher(rawException);
        while (matcher.find()) {
            var symbol = normalizeSymbolForSourceLookup(matcher.group(1));
            if (!shouldUseStacktraceReference(symbol)) {
                continue;
            }

            var lineNumber = Integer.parseInt(matcher.group(3));
            references.putIfAbsent(
                    referenceKey(GitLabCodeReferenceKind.STACKTRACE_SYMBOL, symbol, null, lineNumber),
                    new GitLabCodeReference(
                            GitLabCodeReferenceKind.STACKTRACE_SYMBOL,
                            symbol,
                            null,
                            lineNumber,
                            matcher.group(),
                            200
                    )
            );
        }
    }

    private boolean hasStacktraceReference(Map<String, GitLabCodeReference> references) {
        return references.values().stream()
                .anyMatch(reference -> reference.kind() == GitLabCodeReferenceKind.STACKTRACE_SYMBOL);
    }

    private boolean shouldUseStacktraceReference(String symbol) {
        if (!StringUtils.hasText(symbol)) {
            return false;
        }

        return IGNORED_STACKTRACE_PREFIXES.stream().noneMatch(symbol::startsWith);
    }

    private boolean shouldUseClassReference(String className) {
        if (!StringUtils.hasText(className)) {
            return false;
        }

        var simpleName = simpleName(className);
        return LOW_VALUE_CLASS_SUFFIXES.stream().noneMatch(simpleName::endsWith);
    }

    private List<String> symbolCandidates(String symbol) {
        var candidates = new LinkedHashSet<String>();
        addValue(candidates, symbol);

        if (StringUtils.hasText(symbol) && symbol.contains(".")) {
            addValue(candidates, "." + symbol.substring(symbol.indexOf('.') + 1));
            addValue(candidates, simpleName(symbol));
        }

        return List.copyOf(candidates);
    }

    private boolean isGitLabConfigured() {
        return StringUtils.hasText(gitLabProperties.getBaseUrl())
                && StringUtils.hasText(gitLabProperties.getGroup());
    }

    private String normalizeProjectHint(String value) {
        return StringUtils.hasText(value)
                ? value.trim()
                        .toLowerCase(Locale.ROOT)
                        .replace('-', '_')
                        .replaceAll("[^a-z0-9/_]+", "_")
                : null;
    }

    private String simpleName(String symbol) {
        var separator = symbol.lastIndexOf('.');
        return separator >= 0 ? symbol.substring(separator + 1) : symbol;
    }

    private String normalizeSymbolForSourceLookup(String symbol) {
        if (!StringUtils.hasText(symbol)) {
            return symbol;
        }

        var normalized = symbol.trim();
        var proxySeparator = normalized.indexOf("$$");
        if (proxySeparator > 0) {
            normalized = normalized.substring(0, proxySeparator);
        }

        var innerClassSeparator = normalized.indexOf('$');
        if (innerClassSeparator > 0) {
            normalized = normalized.substring(0, innerClassSeparator);
        }

        return normalized;
    }

    private String evidenceKey(String projectName, String branch, String filePath) {
        return projectName + "::" + branch + "::" + filePath;
    }

    private String referenceKey(GitLabCodeReferenceKind kind, String symbol, String filePath, Integer lineNumber) {
        return kind + "::" + symbol + "::" + filePath + "::" + lineNumber;
    }

    private void addAttribute(List<AnalysisEvidenceAttribute> attributes, String name, String value) {
        if (StringUtils.hasText(value)) {
            attributes.add(new AnalysisEvidenceAttribute(name, value));
        }
    }

    private void addValue(LinkedHashSet<String> values, String value) {
        if (StringUtils.hasText(value)) {
            values.add(value.trim());
        }
    }

    private record GitLabCodeReference(
            GitLabCodeReferenceKind kind,
            String symbol,
            String filePath,
            Integer lineNumber,
            String rawValue,
            int priority
    ) {
    }

    private enum GitLabCodeReferenceKind {
        FULL_PATH,
        STACKTRACE_SYMBOL,
        CLASS_NAME
    }

    private record ResolvedEvidence(
            String key,
            int priority,
            AnalysisEvidenceItem item
    ) {
    }
}
