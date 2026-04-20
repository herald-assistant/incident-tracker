package pl.mkn.incidenttracker.analysis.evidence.provider.exploratory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceAttribute;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceItem;
import pl.mkn.incidenttracker.analysis.ai.AnalysisEvidenceSection;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabProperties;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryFileCandidate;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositoryPort;
import pl.mkn.incidenttracker.analysis.adapter.gitlab.GitLabRepositorySearchQuery;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisContext;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceProvider;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceReference;
import pl.mkn.incidenttracker.analysis.evidence.AnalysisStepPhase;
import pl.mkn.incidenttracker.analysis.evidence.provider.deployment.DeploymentContextEvidenceView;
import pl.mkn.incidenttracker.analysis.evidence.provider.elasticsearch.ElasticLogEvidenceView;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@Slf4j
@RequiredArgsConstructor
public class ExploratoryFlowEvidenceProvider implements AnalysisEvidenceProvider {

    private static final Pattern ENDPOINT_PATTERN = Pattern.compile("\"(/[^\"\\s]+)\"");
    private static final Pattern RABBIT_PATTERN = Pattern.compile("(queue|topic|routingKey|exchange)\\s*[=:]\\s*([A-Za-z0-9._/-]+)");
    private static final Set<String> STOP_WORDS = Set.of(
            "null", "true", "false", "while", "calling", "service", "request",
            "exception", "error", "java", "class", "with", "from", "this"
    );

    private final GitLabRepositoryPort gitLabRepositoryPort;
    private final GitLabProperties gitLabProperties;
    private final ExploratoryAnalysisProperties properties;

    @Override
    public AnalysisEvidenceSection collect(AnalysisContext context) {
        if (!properties.isEnabled()) {
            return emptySection();
        }

        var logEvidence = ElasticLogEvidenceView.from(context);
        var deploymentContext = DeploymentContextEvidenceView.from(context);
        if (logEvidence.isEmpty() || !StringUtils.hasText(deploymentContext.gitLabBranch())) {
            return emptySection();
        }

        var reconstruction = reconstruct(logEvidence, deploymentContext, context);
        if (reconstruction.items().isEmpty()) {
            return emptySection();
        }

        return new AnalysisEvidenceSection(
                producedEvidence().provider(),
                producedEvidence().category(),
                List.copyOf(reconstruction.items())
        );
    }

    @Override
    public AnalysisEvidenceReference producedEvidence() {
        return ExploratoryFlowEvidenceView.EVIDENCE_REFERENCE;
    }

    @Override
    public List<AnalysisEvidenceReference> consumedEvidence() {
        return List.of(
                ElasticLogEvidenceView.EVIDENCE_REFERENCE,
                DeploymentContextEvidenceView.EVIDENCE_REFERENCE,
                new AnalysisEvidenceReference("gitlab", "resolved-code"),
                new AnalysisEvidenceReference("operational-context", "matched-context")
        );
    }

    @Override
    public AnalysisStepPhase stepPhase() {
        return AnalysisStepPhase.ENRICHMENT;
    }

    @Override
    public String stepCode() {
        return "EXPLORATORY_FLOW_RECONSTRUCTION";
    }

    @Override
    public String stepLabel() {
        return "Rekonstrukcja flow między komponentami";
    }

    private ReconstructionResult reconstruct(
            ElasticLogEvidenceView logEvidence,
            DeploymentContextEvidenceView deploymentContext,
            AnalysisContext context
    ) {
        var nodes = new LinkedHashMap<String, MutableNode>();
        var edges = new LinkedHashMap<String, MutableEdge>();
        var items = new ArrayList<AnalysisEvidenceItem>();
        var gitLabCodeProjects = gitLabProjects(context);
        var operationalRepositoryProjects = operationalRepositoryProjects(context);
        var operationalIntegrationEdges = operationalIntegrationEdges(context);

        var orderedEntries = logEvidence.entries().stream()
                .sorted(Comparator.comparing(entry -> safeTimestamp(entry.timestamp())))
                .toList();
        var primaryComponentName = firstMeaningfulComponentName(orderedEntries);
        String previousNodeId = null;
        var sequence = 1;

        for (var entry : orderedEntries) {
            var componentName = componentName(entry);
            if (!StringUtils.hasText(componentName)) {
                continue;
            }

            var node = nodes.computeIfAbsent(nodeId(componentName), unused ->
                    MutableNode.fact(nodeId(componentName), componentName, entry.timestamp()));
            node.title = componentName;
            node.firstSeenAt = firstNonBlank(node.firstSeenAt, entry.timestamp());
            node.errorSource = node.errorSource || isErrorEntry(entry);
            node.addMetadata("service", entry.serviceName());
            node.addMetadata("container", entry.containerName());
            node.addMetadata("class", entry.className());

            if (StringUtils.hasText(previousNodeId) && !previousNodeId.equals(node.id)) {
                var previousNode = nodes.get(previousNodeId);
                upsertEdge(
                        edges,
                        previousNodeId,
                        node.id,
                        sequence++,
                        "HTTP",
                        "FACT",
                        entry.timestamp(),
                        durationBetween(previousNode != null ? previousNode.firstSeenAt : null, entry.timestamp()),
                        "Powiązanie wynikające z kolejności logów o tym samym correlationId."
                );
            }

            previousNodeId = node.id;
        }

        addGitLabCodeMetadata(context, nodes);
        addOperationalRepositoryNodes(operationalRepositoryProjects, nodes);
        addOperationalIntegrationHypotheses(operationalIntegrationEdges, nodes, edges, sequence);
        sequence = edges.values().stream()
                .map(MutableEdge::sequence)
                .max(Integer::compareTo)
                .orElse(0) + 1;

        if (StringUtils.hasText(primaryComponentName) && isGitLabConfigured()) {
            var searchKeywords = searchKeywords(logEvidence, context);
            var projectHints = repositoryHints(primaryComponentName, gitLabCodeProjects, operationalRepositoryProjects);
            var searchResults = gitLabRepositoryPort.searchCandidateFiles(new GitLabRepositorySearchQuery(
                    context.correlationId(),
                    gitLabProperties.getGroup(),
                    deploymentContext.gitLabBranch(),
                    projectHints,
                    List.of(),
                    searchKeywords
            ));

            var perRepositoryReadCounts = new LinkedHashMap<String, Integer>();
            for (var candidate : limitFileCandidates(searchResults)) {
                var repositoryKey = candidate.projectName();
                var readCount = perRepositoryReadCounts.getOrDefault(repositoryKey, 0);
                if (readCount >= properties.getMaxCodeChunkReadsPerRepository()) {
                    continue;
                }

                perRepositoryReadCounts.put(repositoryKey, readCount + 1);
                var candidateNode = nodes.computeIfAbsent(nodeId(candidate.projectName()), unused ->
                        MutableNode.hypothesis(nodeId(candidate.projectName()), candidate.projectName()));
                candidateNode.addMetadata("repo", candidate.projectName());
                candidateNode.addMetadata("branch", candidate.branch());
                candidateNode.addMetadata("file", candidate.filePath());
                candidateNode.addMetadata("matchReason", candidate.matchReason());

                readRepositoryCandidate(candidate, candidateNode);

                if (!nodeId(primaryComponentName).equals(candidateNode.id)) {
                    upsertEdge(
                            edges,
                            nodeId(primaryComponentName),
                            candidateNode.id,
                            sequence++,
                            "UNKNOWN",
                            hypothesisStatus(2),
                            null,
                            null,
                            "Hipoteza z deep search repo: " + candidate.matchReason()
                    );
                }

                items.add(searchTraceItem(candidate));
                if (items.size() >= properties.getMaxRepositories()) {
                    break;
                }
            }
        }

        var limitedNodes = nodes.values().stream()
                .limit(properties.getMaxFlowNodes())
                .toList();
        var allowedNodeIds = limitedNodes.stream().map(MutableNode::id).collect(java.util.stream.Collectors.toSet());
        var limitedEdges = edges.values().stream()
                .filter(edge -> allowedNodeIds.contains(edge.fromNodeId) && allowedNodeIds.contains(edge.toNodeId))
                .sorted(Comparator.comparingInt(MutableEdge::sequence))
                .limit(properties.getMaxFlowEdges())
                .toList();

        for (var node : limitedNodes) {
            items.add(node.toEvidenceItem());
        }
        for (var edge : limitedEdges) {
            items.add(edge.toEvidenceItem());
        }

        return new ReconstructionResult(List.copyOf(items));
    }

    private void addGitLabCodeMetadata(AnalysisContext context, Map<String, MutableNode> nodes) {
        for (var section : context.evidenceSections()) {
            if (!"gitlab".equals(section.provider()) || !"resolved-code".equals(section.category())) {
                continue;
            }

            for (var item : section.items()) {
                var attributes = pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceAttributes.byName(item.attributes());
                var projectName = pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceAttributes.text(attributes, "projectName");
                if (!StringUtils.hasText(projectName)) {
                    continue;
                }

                var node = nodes.computeIfAbsent(nodeId(projectName), unused ->
                        MutableNode.fact(nodeId(projectName), projectName, null));
                node.addMetadata("repo", projectName);
                node.addMetadata("branch", pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceAttributes.text(attributes, "branch"));
                node.addMetadata("class", simpleName(pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceAttributes.text(attributes, "symbol")));
                node.addMetadata("file", pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceAttributes.text(attributes, "filePath"));
                node.addMetadata("line", pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceAttributes.text(attributes, "lineNumber"));
            }
        }
    }

    private void addOperationalRepositoryNodes(
            List<String> operationalRepositoryProjects,
            Map<String, MutableNode> nodes
    ) {
        for (var project : operationalRepositoryProjects.stream().limit(properties.getMaxRepositories()).toList()) {
            var node = nodes.computeIfAbsent(nodeId(project), unused ->
                    MutableNode.hypothesis(nodeId(project), project));
            node.addMetadata("repo", project);
            node.addMetadata("source", "operational-context");
        }
    }

    private void addOperationalIntegrationHypotheses(
            List<OperationalIntegrationEdge> operationalIntegrationEdges,
            Map<String, MutableNode> nodes,
            Map<String, MutableEdge> edges,
            int startingSequence
    ) {
        var sequence = startingSequence;
        for (var integration : operationalIntegrationEdges) {
            if (!StringUtils.hasText(integration.fromComponent()) || !StringUtils.hasText(integration.toComponent())) {
                continue;
            }

            var fromNode = nodes.computeIfAbsent(nodeId(integration.fromComponent()), unused ->
                    MutableNode.hypothesis(nodeId(integration.fromComponent()), integration.fromComponent()));
            var toNode = nodes.computeIfAbsent(nodeId(integration.toComponent()), unused ->
                    MutableNode.hypothesis(nodeId(integration.toComponent()), integration.toComponent()));
            fromNode.addMetadata("source", "operational-context");
            toNode.addMetadata("source", "operational-context");
            upsertEdge(
                    edges,
                    fromNode.id,
                    toNode.id,
                    sequence++,
                    interactionType(integration.protocol()),
                    hypothesisStatus(2),
                    null,
                    null,
                    "Hipoteza operacyjna: " + integration.supportSummary()
            );
        }
    }

    private AnalysisEvidenceItem searchTraceItem(GitLabRepositoryFileCandidate candidate) {
        return new AnalysisEvidenceItem(
                "Deep search " + candidate.projectName() + " -> " + candidate.filePath(),
                List.of(
                        new AnalysisEvidenceAttribute("kind", "TRACE"),
                        new AnalysisEvidenceAttribute("projectName", candidate.projectName()),
                        new AnalysisEvidenceAttribute("branch", candidate.branch()),
                        new AnalysisEvidenceAttribute("filePath", candidate.filePath()),
                        new AnalysisEvidenceAttribute("matchReason", candidate.matchReason()),
                        new AnalysisEvidenceAttribute("matchScore", String.valueOf(candidate.matchScore()))
                )
        );
    }

    private void readRepositoryCandidate(GitLabRepositoryFileCandidate candidate, MutableNode node) {
        var content = gitLabRepositoryPort.readFile(
                candidate.group(),
                candidate.projectName(),
                candidate.branch(),
                candidate.filePath(),
                4_000
        ).content();
        node.addMetadata("class", stripExtension(lastPathSegment(candidate.filePath())));

        var endpointMatcher = ENDPOINT_PATTERN.matcher(content);
        if (endpointMatcher.find()) {
            node.addMetadata("endpoint", endpointMatcher.group(1));
        }

        var rabbitMatcher = RABBIT_PATTERN.matcher(content);
        if (rabbitMatcher.find()) {
            node.addMetadata(rabbitMatcher.group(1), rabbitMatcher.group(2));
        }
    }

    private List<GitLabRepositoryFileCandidate> limitFileCandidates(List<GitLabRepositoryFileCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        var grouped = new LinkedHashMap<String, List<GitLabRepositoryFileCandidate>>();
        for (var candidate : candidates) {
            grouped.computeIfAbsent(candidate.projectName(), unused -> new ArrayList<>()).add(candidate);
        }

        var limited = new ArrayList<GitLabRepositoryFileCandidate>();
        for (var entry : grouped.entrySet().stream().limit(properties.getMaxRepositories()).toList()) {
            limited.addAll(entry.getValue().stream()
                    .sorted(Comparator.comparingInt(GitLabRepositoryFileCandidate::matchScore).reversed())
                    .limit(properties.getMaxFileCandidatesPerRepository())
                    .toList());
        }
        return List.copyOf(limited);
    }

    private List<String> repositoryHints(
            String primaryComponentName,
            List<String> gitLabCodeProjects,
            List<String> operationalRepositoryProjects
    ) {
        var hints = new LinkedHashSet<String>();
        addValue(hints, primaryComponentName);
        addValue(hints, normalizeProjectHint(primaryComponentName));
        gitLabCodeProjects.forEach(project -> {
            addValue(hints, project);
            addValue(hints, normalizeProjectHint(project));
        });
        operationalRepositoryProjects.forEach(project -> {
            addValue(hints, project);
            addValue(hints, normalizeProjectHint(project));
        });
        return hints.stream().limit(properties.getMaxRepositories()).toList();
    }

    private List<String> gitLabProjects(AnalysisContext context) {
        var projects = new LinkedHashSet<String>();
        for (var section : context.evidenceSections()) {
            if (!"gitlab".equals(section.provider()) || !"resolved-code".equals(section.category())) {
                continue;
            }

            for (var item : section.items()) {
                for (var attribute : item.attributes()) {
                    if ("projectName".equals(attribute.name())) {
                        addValue(projects, attribute.value());
                    }
                }
            }
        }
        return List.copyOf(projects);
    }

    private List<String> operationalRepositoryProjects(AnalysisContext context) {
        var projects = new LinkedHashSet<String>();
        for (var section : context.evidenceSections()) {
            if (!"operational-context".equals(section.provider()) || !"matched-context".equals(section.category())) {
                continue;
            }

            for (var item : section.items()) {
                var attributes = pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceAttributes.byName(item.attributes());
                addValue(projects, pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceAttributes.text(attributes, "project"));
                addValue(projects, pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceAttributes.text(attributes, "repositoryId"));
            }
        }
        return List.copyOf(projects);
    }

    private List<OperationalIntegrationEdge> operationalIntegrationEdges(AnalysisContext context) {
        var edges = new ArrayList<OperationalIntegrationEdge>();
        for (var section : context.evidenceSections()) {
            if (!"operational-context".equals(section.provider()) || !"matched-context".equals(section.category())) {
                continue;
            }

            for (var item : section.items()) {
                if (!item.title().startsWith("Operational integration ")) {
                    continue;
                }

                var attributes = pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceAttributes.byName(item.attributes());
                edges.add(new OperationalIntegrationEdge(
                        pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceAttributes.text(attributes, "from"),
                        pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceAttributes.text(attributes, "to"),
                        pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceAttributes.text(attributes, "protocol"),
                        firstNonBlank(
                                pl.mkn.incidenttracker.analysis.evidence.AnalysisEvidenceAttributes.text(attributes, "matchedBy"),
                                item.title()
                        )
                ));
            }
        }
        return List.copyOf(edges);
    }

    private List<String> searchKeywords(ElasticLogEvidenceView logEvidence, AnalysisContext context) {
        var keywords = new LinkedHashSet<String>();

        for (var entry : logEvidence.entries()) {
            tokenize(keywords, entry.message());
            tokenize(keywords, entry.exception());
            addValue(keywords, simpleName(entry.className()));
        }

        for (var project : gitLabProjects(context)) {
            addValue(keywords, normalizeProjectHint(project));
        }

        return keywords.stream()
                .filter(StringUtils::hasText)
                .limit(properties.getMaxSearchTermsPerComponent())
                .toList();
    }

    private void tokenize(Set<String> keywords, String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return;
        }

        for (var token : rawText.split("[^A-Za-z0-9._/-]+")) {
            if (!StringUtils.hasText(token)) {
                continue;
            }

            var normalized = token.trim().toLowerCase(Locale.ROOT);
            if (normalized.length() < 4 || STOP_WORDS.contains(normalized)) {
                continue;
            }

            addValue(keywords, normalized);
        }
    }

    private void upsertEdge(
            Map<String, MutableEdge> edges,
            String fromNodeId,
            String toNodeId,
            int sequence,
            String interactionType,
            String factStatus,
            String startedAt,
            Long durationMs,
            String supportSummary
    ) {
        var edgeId = fromNodeId + "->" + toNodeId;
        edges.compute(edgeId, (unused, current) -> {
            if (current == null) {
                return new MutableEdge(
                        edgeId,
                        fromNodeId,
                        toNodeId,
                        sequence,
                        interactionType,
                        factStatus,
                        startedAt,
                        durationMs,
                        supportSummary
                );
            }

            if ("FACT".equals(factStatus)) {
                current.factStatus = "FACT";
            }
            current.supportSummary = firstNonBlank(current.supportSummary, supportSummary);
            current.startedAt = firstNonBlank(current.startedAt, startedAt);
            current.durationMs = current.durationMs != null ? current.durationMs : durationMs;
            current.sequence = Math.min(current.sequence, sequence);
            return current;
        });
    }

    private AnalysisEvidenceSection emptySection() {
        return new AnalysisEvidenceSection(
                producedEvidence().provider(),
                producedEvidence().category(),
                List.of()
        );
    }

    private boolean isGitLabConfigured() {
        return StringUtils.hasText(gitLabProperties.getBaseUrl())
                && StringUtils.hasText(gitLabProperties.getGroup());
    }

    private String hypothesisStatus(int supportScore) {
        return supportScore >= properties.getMinHypothesisSupportScore() ? "HYPOTHESIS" : "SKIPPED";
    }

    private String interactionType(String protocol) {
        var normalized = StringUtils.hasText(protocol) ? protocol.trim().toUpperCase(Locale.ROOT) : "";
        if (normalized.contains("HTTP") || normalized.contains("REST")) {
            return "HTTP";
        }
        if (normalized.contains("RABBIT") || normalized.contains("KAFKA") || normalized.contains("MQ")) {
            return "MESSAGING";
        }
        if (normalized.contains("CONFIG")) {
            return "CONFIG";
        }
        return "UNKNOWN";
    }

    private String componentName(ElasticLogEvidenceView.LogEntry entry) {
        return firstNonBlank(entry.containerName(), entry.serviceName(), entry.className());
    }

    private String firstMeaningfulComponentName(List<ElasticLogEvidenceView.LogEntry> orderedEntries) {
        for (var entry : orderedEntries) {
            var componentName = componentName(entry);
            if (StringUtils.hasText(componentName)) {
                return componentName;
            }
        }

        return null;
    }

    private boolean isErrorEntry(ElasticLogEvidenceView.LogEntry entry) {
        return "ERROR".equalsIgnoreCase(entry.level()) || StringUtils.hasText(entry.exception());
    }

    private Instant safeTimestamp(String value) {
        if (!StringUtils.hasText(value)) {
            return Instant.MAX;
        }

        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            return Instant.MAX;
        }
    }

    private Long durationBetween(String startedAt, String endedAt) {
        if (!StringUtils.hasText(startedAt) || !StringUtils.hasText(endedAt)) {
            return null;
        }

        try {
            return Duration.between(Instant.parse(startedAt), Instant.parse(endedAt)).toMillis();
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String nodeId(String value) {
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }

    private void addValue(Set<String> values, String value) {
        if (StringUtils.hasText(value)) {
            values.add(value.trim());
        }
    }

    private String normalizeProjectHint(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/_-]+", "-")
                : null;
    }

    private String simpleName(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        var normalized = value.trim();
        var separator = Math.max(normalized.lastIndexOf('.'), normalized.lastIndexOf('/'));
        return separator >= 0 ? normalized.substring(separator + 1) : normalized;
    }

    private String lastPathSegment(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }

        var normalized = value.replace('\\', '/');
        var separator = normalized.lastIndexOf('/');
        return separator >= 0 ? normalized.substring(separator + 1) : normalized;
    }

    private String stripExtension(String value) {
        if (!StringUtils.hasText(value)) {
            return value;
        }

        var separator = value.lastIndexOf('.');
        return separator > 0 ? value.substring(0, separator) : value;
    }

    private String firstNonBlank(String... values) {
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }

        return null;
    }

    private record ReconstructionResult(List<AnalysisEvidenceItem> items) {
    }

    private record OperationalIntegrationEdge(
            String fromComponent,
            String toComponent,
            String protocol,
            String supportSummary
    ) {
    }

    private static final class MutableNode {

        private final String id;
        private final String kind;
        private final String componentName;
        private final LinkedHashMap<String, String> metadata = new LinkedHashMap<>();
        private String title;
        private String factStatus;
        private String firstSeenAt;
        private boolean errorSource;

        private MutableNode(String id, String kind, String componentName, String factStatus, String firstSeenAt) {
            this.id = id;
            this.kind = kind;
            this.componentName = componentName;
            this.title = componentName;
            this.factStatus = factStatus;
            this.firstSeenAt = firstSeenAt;
        }

        private static MutableNode fact(String id, String componentName, String firstSeenAt) {
            return new MutableNode(id, "COMPONENT", componentName, "FACT", firstSeenAt);
        }

        private static MutableNode hypothesis(String id, String componentName) {
            return new MutableNode(id, "COMPONENT", componentName, "HYPOTHESIS", null);
        }

        private void addMetadata(String name, String value) {
            if (StringUtils.hasText(name) && StringUtils.hasText(value)) {
                metadata.putIfAbsent(name, value.trim());
            }
        }

        private AnalysisEvidenceItem toEvidenceItem() {
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            attributes.add(new AnalysisEvidenceAttribute("kind", "NODE"));
            attributes.add(new AnalysisEvidenceAttribute("id", id));
            attributes.add(new AnalysisEvidenceAttribute("nodeKind", kind));
            attributes.add(new AnalysisEvidenceAttribute("title", title));
            attributes.add(new AnalysisEvidenceAttribute("componentName", componentName));
            attributes.add(new AnalysisEvidenceAttribute("factStatus", factStatus));
            if (StringUtils.hasText(firstSeenAt)) {
                attributes.add(new AnalysisEvidenceAttribute("firstSeenAt", firstSeenAt));
            }
            attributes.add(new AnalysisEvidenceAttribute("errorSource", String.valueOf(errorSource)));
            metadata.forEach((name, value) ->
                    attributes.add(new AnalysisEvidenceAttribute("metadata." + name, value))
            );
            return new AnalysisEvidenceItem("Flow node " + componentName, List.copyOf(attributes));
        }

        private String id() {
            return id;
        }
    }

    private static final class MutableEdge {

        private final String id;
        private final String fromNodeId;
        private final String toNodeId;
        private int sequence;
        private String interactionType;
        private String factStatus;
        private String startedAt;
        private Long durationMs;
        private String supportSummary;

        private MutableEdge(
                String id,
                String fromNodeId,
                String toNodeId,
                int sequence,
                String interactionType,
                String factStatus,
                String startedAt,
                Long durationMs,
                String supportSummary
        ) {
            this.id = id;
            this.fromNodeId = fromNodeId;
            this.toNodeId = toNodeId;
            this.sequence = sequence;
            this.interactionType = interactionType;
            this.factStatus = factStatus;
            this.startedAt = startedAt;
            this.durationMs = durationMs;
            this.supportSummary = supportSummary;
        }

        private AnalysisEvidenceItem toEvidenceItem() {
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            attributes.add(new AnalysisEvidenceAttribute("kind", "EDGE"));
            attributes.add(new AnalysisEvidenceAttribute("id", id));
            attributes.add(new AnalysisEvidenceAttribute("fromNodeId", fromNodeId));
            attributes.add(new AnalysisEvidenceAttribute("toNodeId", toNodeId));
            attributes.add(new AnalysisEvidenceAttribute("sequence", String.valueOf(sequence)));
            attributes.add(new AnalysisEvidenceAttribute("interactionType", interactionType));
            attributes.add(new AnalysisEvidenceAttribute("factStatus", factStatus));
            if (StringUtils.hasText(startedAt)) {
                attributes.add(new AnalysisEvidenceAttribute("startedAt", startedAt));
            }
            if (durationMs != null) {
                attributes.add(new AnalysisEvidenceAttribute("durationMs", String.valueOf(durationMs)));
            }
            if (StringUtils.hasText(supportSummary)) {
                attributes.add(new AnalysisEvidenceAttribute("supportSummary", supportSummary));
            }
            return new AnalysisEvidenceItem(
                    "Flow edge " + fromNodeId + " -> " + toNodeId,
                    List.copyOf(attributes)
            );
        }

        private int sequence() {
            return sequence;
        }
    }

}
