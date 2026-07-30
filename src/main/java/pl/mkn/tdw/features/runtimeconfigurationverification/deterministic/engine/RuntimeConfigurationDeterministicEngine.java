package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.engine;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationChangeKind;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDifference;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationFinding;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationFindingSeverity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationReference;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationReferenceStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationSensitivity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationValueType;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.SanitizedConfigurationDocument;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.SanitizedConfigurationNode;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.parse.ParsedConfigurationDocument;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.parse.ParsedConfigurationFile;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.parse.ParsedConfigurationNode;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.parse.ParsedConfigurationSnapshot;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationBranchCoverage;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationFileRole;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScope;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RuntimeConfigurationDeterministicEngine {

    private static final Pattern SPRING_REFERENCE = Pattern.compile("\\$\\{([^}:]+)(?::[^}]*)?}");
    private static final Pattern EXTERNAL_REFERENCE = Pattern.compile("\\$([A-Za-z0-9_.-]+)\\$");
    private static final Pattern LOCAL_EXPRESSION = Pattern.compile(
            "(?<![A-Za-z0-9_.])((?:local|variable)\\.[A-Za-z0-9_.-]+)"
    );
    private static final Set<String> ENVIRONMENTAL_PATH_TOKENS = Set.of(
            "environment",
            "env",
            "host",
            "url",
            "jdbcurl",
            "schema",
            "queuename",
            "exchange"
    );

    private final RuntimeConfigurationSensitivityClassifier sensitivityClassifier =
            new RuntimeConfigurationSensitivityClassifier();
    private final RuntimeConfigurationDynamicKeyClassifier dynamicKeyClassifier =
            new RuntimeConfigurationDynamicKeyClassifier();

    public RuntimeConfigurationDeterministicContext build(
            RuntimeConfigurationScope scope,
            RuntimeConfigurationBranchCoverage sourceCoverage,
            RuntimeConfigurationBranchCoverage targetCoverage,
            ParsedConfigurationSnapshot source,
            ParsedConfigurationSnapshot target
    ) {
        return build(
                scope,
                sourceCoverage,
                targetCoverage,
                source,
                target,
                new RuntimeConfigurationRunPseudonymizer()
        );
    }

    RuntimeConfigurationDeterministicContext build(
            RuntimeConfigurationScope scope,
            RuntimeConfigurationBranchCoverage sourceCoverage,
            RuntimeConfigurationBranchCoverage targetCoverage,
            ParsedConfigurationSnapshot source,
            ParsedConfigurationSnapshot target,
            RuntimeConfigurationRunPseudonymizer pseudonymizer
    ) {
        var state = new BuildState(pseudonymizer);
        var documents = buildDocuments(source, target, state);
        var references = buildReferences(source, target, state);
        addEffectiveDifferences(source, target, state);
        var findings = buildFindings(
                sourceCoverage,
                targetCoverage,
                source,
                target,
                references,
                state
        );
        var status = status(sourceCoverage, targetCoverage, source, target, references, state, findings);

        return new RuntimeConfigurationDeterministicContext(
                scope.repositoryId(),
                scope.systemId(),
                scope.systemLabel(),
                scope.configurationDirectory(),
                source.branch(),
                target.branch(),
                status,
                sourceCoverage,
                targetCoverage,
                documents,
                references,
                state.differences,
                findings
        );
    }

    private List<SanitizedConfigurationDocument> buildDocuments(
            ParsedConfigurationSnapshot source,
            ParsedConfigurationSnapshot target,
            BuildState state
    ) {
        var documents = new ArrayList<SanitizedConfigurationDocument>();
        for (var role : RuntimeConfigurationFileRole.values()) {
            var sourceFile = source.file(role);
            var targetFile = target.file(role);
            var count = Math.max(documentCount(sourceFile), documentCount(targetFile));
            for (var index = 0; index < count; index++) {
                var sourceDocument = document(sourceFile, index);
                var targetDocument = document(targetFile, index);
                var sourceRoot = sourceDocument != null ? sourceDocument.root() : null;
                var targetRoot = targetDocument != null ? targetDocument.root() : null;
                var root = sanitizeNode(
                        role,
                        index,
                        sourceRoot,
                        targetRoot,
                        "",
                        state
                );
                documents.add(new SanitizedConfigurationDocument(
                        role,
                        sourceFile != null ? sourceFile.path() : null,
                        targetFile != null ? targetFile.path() : null,
                        index,
                        sourceDocument != null,
                        targetDocument != null,
                        profileToken(sourceDocument, state.pseudonymizer),
                        profileToken(targetDocument, state.pseudonymizer),
                        root
                ));
            }
        }
        return List.copyOf(documents);
    }

    private SanitizedConfigurationNode sanitizeNode(
            RuntimeConfigurationFileRole role,
            int documentIndex,
            ParsedConfigurationNode source,
            ParsedConfigurationNode target,
            String safeParentPath,
            BuildState state
    ) {
        if (source == null && target == null) {
            return null;
        }
        var rawName = source != null ? source.name() : target.name();
        var safeName = dynamicKeyClassifier.dynamic(rawName)
                ? state.pseudonymizer.keyToken(rawName)
                : rawName;
        var rawPath = source != null ? source.path() : target.path();
        var safePath = rawPath == null || rawPath.isBlank()
                ? ""
                : safePath(safeParentPath, safeName);
        state.safePaths.put(new NodeLocation(role, documentIndex, rawPath), safePath);

        var sourceType = source != null ? source.type() : null;
        var targetType = target != null ? target.type() : null;
        var sensitivity = sensitivityClassifier.classify(rawPath);
        var children = sanitizeChildren(
                role,
                documentIndex,
                source != null ? source.children() : List.of(),
                target != null ? target.children() : List.of(),
                safePath,
                state
        );
        var relation = relation(source, target, children);
        var sourceToken = publicToken(source, sensitivity, state.pseudonymizer);
        var targetToken = publicToken(target, sensitivity, state.pseudonymizer);

        if (differenceWorthy(source, target, relation)) {
            state.addDifference(
                    role,
                    documentIndex,
                    safePath,
                    relation,
                    sourceType,
                    targetType,
                    sensitivity,
                    sourceToken,
                    targetToken,
                    rawPath
            );
        }

        return new SanitizedConfigurationNode(
                safeName,
                safePath,
                sourceType,
                targetType,
                relation,
                sensitivity,
                sourceToken,
                targetToken,
                cardinality(source),
                cardinality(target),
                children
        );
    }

    private List<SanitizedConfigurationNode> sanitizeChildren(
            RuntimeConfigurationFileRole role,
            int documentIndex,
            List<ParsedConfigurationNode> sourceChildren,
            List<ParsedConfigurationNode> targetChildren,
            String safeParentPath,
            BuildState state
    ) {
        var sourceByName = byName(sourceChildren);
        var targetByName = byName(targetChildren);
        var names = new LinkedHashSet<String>();
        names.addAll(sourceByName.keySet());
        names.addAll(targetByName.keySet());
        return names.stream()
                .map(name -> sanitizeNode(
                        role,
                        documentIndex,
                        sourceByName.get(name),
                        targetByName.get(name),
                        safeParentPath,
                        state
                ))
                .toList();
    }

    private RuntimeConfigurationChangeKind relation(
            ParsedConfigurationNode source,
            ParsedConfigurationNode target,
            List<SanitizedConfigurationNode> children
    ) {
        if (source == null) {
            return RuntimeConfigurationChangeKind.ADDED;
        }
        if (target == null) {
            return RuntimeConfigurationChangeKind.REMOVED;
        }
        if (source.type() != target.type()) {
            return RuntimeConfigurationChangeKind.TYPE_CHANGED;
        }
        if (source.scalar()) {
            return Objects.equals(canonical(source.scalarValue()), canonical(target.scalarValue()))
                    ? RuntimeConfigurationChangeKind.UNCHANGED
                    : RuntimeConfigurationChangeKind.CHANGED;
        }
        return children.stream().allMatch(child ->
                child.relation() == RuntimeConfigurationChangeKind.UNCHANGED)
                ? RuntimeConfigurationChangeKind.UNCHANGED
                : RuntimeConfigurationChangeKind.CHANGED;
    }

    private boolean differenceWorthy(
            ParsedConfigurationNode source,
            ParsedConfigurationNode target,
            RuntimeConfigurationChangeKind relation
    ) {
        if (relation == RuntimeConfigurationChangeKind.UNCHANGED) {
            return false;
        }
        if (relation == RuntimeConfigurationChangeKind.TYPE_CHANGED) {
            return true;
        }
        return (source == null || source.scalar()) && (target == null || target.scalar());
    }

    private List<RuntimeConfigurationReference> buildReferences(
            ParsedConfigurationSnapshot source,
            ParsedConfigurationSnapshot target,
            BuildState state
    ) {
        var sourceAnalysis = analyzeReferences(source);
        var targetAnalysis = analyzeReferences(target);
        var keys = new LinkedHashSet<ReferenceKey>();
        keys.addAll(sourceAnalysis.observations.keySet());
        keys.addAll(targetAnalysis.observations.keySet());
        var results = new ArrayList<RuntimeConfigurationReference>();
        for (var key : keys) {
            var sourceObservation = sourceAnalysis.observations.get(key);
            var targetObservation = targetAnalysis.observations.get(key);
            var location = sourceObservation != null
                    ? sourceObservation.location
                    : targetObservation.location;
            var safeSourcePath = state.safePaths.getOrDefault(
                    location,
                    sanitizeReferencePath(location.rawPath, state.pseudonymizer)
            );
            var targetPath = sourceObservation != null
                    ? sourceObservation.targetPath
                    : targetObservation.targetPath;
            var safeTargetPath = sanitizeReferencePath(targetPath, state.pseudonymizer);
            var reference = new RuntimeConfigurationReference(
                    "reference-" + String.format("%03d", results.size() + 1),
                    location.role,
                    location.documentIndex,
                    safeSourcePath,
                    safeTargetPath,
                    sourceObservation != null
                            ? sourceObservation.kind
                            : targetObservation.kind,
                    sourceObservation != null
                            ? sourceObservation.status
                            : RuntimeConfigurationReferenceStatus.ABSENT,
                    targetObservation != null
                            ? targetObservation.status
                            : RuntimeConfigurationReferenceStatus.ABSENT
            );
            results.add(reference);
        }
        return List.copyOf(results);
    }

    private ReferenceAnalysis analyzeReferences(ParsedConfigurationSnapshot snapshot) {
        var leaves = scalarLocations(snapshot);
        var resolvablePaths = new LinkedHashSet<String>();
        for (var location : leaves) {
            if (EnumSet.of(
                    RuntimeConfigurationFileRole.GLOBAL_VAR,
                    RuntimeConfigurationFileRole.LOCAL_VAR
            ).contains(location.location.role)) {
                resolvablePaths.add(location.location.rawPath);
            }
        }

        var edges = new LinkedHashMap<String, Set<String>>();
        var candidates = new ArrayList<ReferenceCandidate>();
        for (var leaf : leaves) {
            if (!(leaf.node.scalarValue() instanceof String value)) {
                continue;
            }
            for (var target : references(value)) {
                candidates.add(new ReferenceCandidate(
                        leaf.location,
                        target.targetPath,
                        target.kind,
                        target.external
                ));
                if (!target.external) {
                    edges.computeIfAbsent(leaf.location.rawPath, ignored -> new LinkedHashSet<>())
                            .add(target.targetPath);
                }
            }
        }

        var observations = new LinkedHashMap<ReferenceKey, ReferenceObservation>();
        for (var candidate : candidates) {
            var status = candidate.external
                    ? RuntimeConfigurationReferenceStatus.EXTERNAL
                    : !resolvablePaths.contains(candidate.targetPath)
                    ? RuntimeConfigurationReferenceStatus.UNRESOLVED
                    : pathExists(candidate.targetPath, candidate.location.rawPath, edges, new LinkedHashSet<>())
                    ? RuntimeConfigurationReferenceStatus.CYCLIC
                    : RuntimeConfigurationReferenceStatus.RESOLVED;
            var key = new ReferenceKey(
                    candidate.location.role,
                    candidate.location.documentIndex,
                    candidate.location.rawPath,
                    candidate.targetPath,
                    candidate.kind
            );
            observations.put(key, new ReferenceObservation(
                    candidate.location,
                    candidate.targetPath,
                    candidate.kind,
                    status
            ));
        }
        return new ReferenceAnalysis(observations);
    }

    private void addEffectiveDifferences(
            ParsedConfigurationSnapshot source,
            ParsedConfigurationSnapshot target,
            BuildState state
    ) {
        var sourceValues = varScalarValues(source);
        var targetValues = varScalarValues(target);
        var sourceApplications = applicationScalars(source);
        var targetApplications = applicationScalars(target);
        var keys = new LinkedHashSet<NodeLocation>();
        keys.addAll(sourceApplications.keySet());
        keys.retainAll(targetApplications.keySet());
        for (var location : keys) {
            var sourceNode = sourceApplications.get(location);
            var targetNode = targetApplications.get(location);
            if (!Objects.equals(
                    canonical(sourceNode.scalarValue()),
                    canonical(targetNode.scalarValue())
            )) {
                continue;
            }
            var sourceEffective = resolveEffective(sourceNode.scalarValue(), sourceValues, new LinkedHashSet<>());
            var targetEffective = resolveEffective(targetNode.scalarValue(), targetValues, new LinkedHashSet<>());
            if (sourceEffective == null
                    || targetEffective == null
                    || Objects.equals(canonical(sourceEffective), canonical(targetEffective))) {
                continue;
            }
            if (state.hasDifference(location)) {
                continue;
            }
            var safePath = state.safePaths.getOrDefault(
                    location,
                    sanitizeReferencePath(location.rawPath, state.pseudonymizer)
            );
            var sensitivity = sensitivityClassifier.classify(location.rawPath);
            state.addDifference(
                    location.role,
                    location.documentIndex,
                    safePath,
                    RuntimeConfigurationChangeKind.EFFECTIVE_CHANGED,
                    sourceNode.type(),
                    targetNode.type(),
                    sensitivity,
                    sensitivity == RuntimeConfigurationSensitivity.SENSITIVE
                            ? null
                            : state.pseudonymizer.valueToken(sourceEffective),
                    sensitivity == RuntimeConfigurationSensitivity.SENSITIVE
                            ? null
                            : state.pseudonymizer.valueToken(targetEffective),
                    location.rawPath
            );
        }
    }

    private List<RuntimeConfigurationFinding> buildFindings(
            RuntimeConfigurationBranchCoverage sourceCoverage,
            RuntimeConfigurationBranchCoverage targetCoverage,
            ParsedConfigurationSnapshot source,
            ParsedConfigurationSnapshot target,
            List<RuntimeConfigurationReference> references,
            BuildState state
    ) {
        var findings = new ArrayList<RuntimeConfigurationFinding>();
        if (!sourceCoverage.complete()) {
            addFinding(findings, "INCOMPLETE_SOURCE_COVERAGE", RuntimeConfigurationFindingSeverity.ERROR, "", List.of(), List.of());
        }
        if (!targetCoverage.complete()) {
            addFinding(findings, "INCOMPLETE_TARGET_COVERAGE", RuntimeConfigurationFindingSeverity.ERROR, "", List.of(), List.of());
        }
        addParserFindings(findings, source, "SOURCE");
        addParserFindings(findings, target, "TARGET");

        for (var difference : state.differences) {
            if (difference.sensitivity() == RuntimeConfigurationSensitivity.SENSITIVE) {
                addFinding(
                        findings,
                        "SENSITIVE_VALUE_CHANGE",
                        RuntimeConfigurationFindingSeverity.WARNING,
                        difference.path(),
                        List.of(difference.differenceId()),
                        List.of()
                );
            } else if (difference.kind() == RuntimeConfigurationChangeKind.TYPE_CHANGED) {
                addFinding(
                        findings,
                        "CONFIGURATION_TYPE_CHANGE",
                        RuntimeConfigurationFindingSeverity.WARNING,
                        difference.path(),
                        List.of(difference.differenceId()),
                        List.of()
                );
            } else if (difference.kind() == RuntimeConfigurationChangeKind.EFFECTIVE_CHANGED) {
                addFinding(
                        findings,
                        "EFFECTIVE_VALUE_CHANGE",
                        RuntimeConfigurationFindingSeverity.WARNING,
                        difference.path(),
                        List.of(difference.differenceId()),
                        List.of()
                );
            }
        }

        for (var reference : references) {
            if (reference.sourceStatus() == RuntimeConfigurationReferenceStatus.CYCLIC
                    || reference.targetStatus() == RuntimeConfigurationReferenceStatus.CYCLIC) {
                addFinding(
                        findings,
                        "CYCLIC_REFERENCE",
                        RuntimeConfigurationFindingSeverity.ERROR,
                        reference.sourcePath(),
                        List.of(),
                        List.of(reference.referenceId())
                );
            } else if (reference.sourceStatus() == RuntimeConfigurationReferenceStatus.UNRESOLVED
                    || reference.targetStatus() == RuntimeConfigurationReferenceStatus.UNRESOLVED) {
                addFinding(
                        findings,
                        "UNRESOLVED_REFERENCE",
                        RuntimeConfigurationFindingSeverity.ERROR,
                        reference.sourcePath(),
                        List.of(),
                        List.of(reference.referenceId())
                );
            }
        }

        addEnvironmentFindings(findings, source, target, state);
        addUnrelatedGlobalFindings(findings, references, state);
        return List.copyOf(findings);
    }

    private void addParserFindings(
            List<RuntimeConfigurationFinding> findings,
            ParsedConfigurationSnapshot snapshot,
            String branchRole
    ) {
        for (var file : snapshot.files()) {
            for (var issue : file.issues()) {
                addFinding(
                        findings,
                        branchRole + "_" + issue.code(),
                        issue.code().contains("UNSUPPORTED")
                                ? RuntimeConfigurationFindingSeverity.WARNING
                                : RuntimeConfigurationFindingSeverity.ERROR,
                        issue.path(),
                        List.of(),
                        List.of()
                );
            }
        }
    }

    private void addEnvironmentFindings(
            List<RuntimeConfigurationFinding> findings,
            ParsedConfigurationSnapshot source,
            ParsedConfigurationSnapshot target,
            BuildState state
    ) {
        var sourceLeaves = scalarMap(source);
        var targetLeaves = scalarMap(target);
        for (var entry : targetLeaves.entrySet()) {
            var value = entry.getValue().scalarValue();
            if (value instanceof String text
                    && containsBranchMarker(text, source.branch())
                    && !source.branch().equals(target.branch())) {
                var safePath = state.safePaths.getOrDefault(
                        entry.getKey(),
                        sanitizeReferencePath(entry.getKey().rawPath, state.pseudonymizer)
                );
                addFinding(
                        findings,
                        "WRONG_ENVIRONMENT_MARKER",
                        RuntimeConfigurationFindingSeverity.ERROR,
                        safePath,
                        state.differenceIds(entry.getKey()),
                        List.of()
                );
            }
        }

        for (var entry : sourceLeaves.entrySet()) {
            var targetNode = targetLeaves.get(entry.getKey());
            if (targetNode == null
                    || !environmentalPath(entry.getKey().rawPath)
                    || placeholder(entry.getValue().scalarValue())
                    || !Objects.equals(
                    canonical(entry.getValue().scalarValue()),
                    canonical(targetNode.scalarValue())
            )) {
                continue;
            }
            var safePath = state.safePaths.getOrDefault(
                    entry.getKey(),
                    sanitizeReferencePath(entry.getKey().rawPath, state.pseudonymizer)
            );
            addFinding(
                    findings,
                    "SUSPICIOUS_UNCHANGED_ENVIRONMENT_VALUE",
                    RuntimeConfigurationFindingSeverity.WARNING,
                    safePath,
                    List.of(),
                    List.of()
            );
        }
    }

    private void addUnrelatedGlobalFindings(
            List<RuntimeConfigurationFinding> findings,
            List<RuntimeConfigurationReference> references,
            BuildState state
    ) {
        var referenced = references.stream()
                .map(RuntimeConfigurationReference::targetPath)
                .collect(java.util.stream.Collectors.toSet());
        for (var difference : state.differences) {
            if (difference.role() != RuntimeConfigurationFileRole.GLOBAL_VAR
                    || referenced.contains(difference.path())) {
                continue;
            }
            addFinding(
                    findings,
                    "UNRELATED_GLOBAL_DIFFERENCE",
                    RuntimeConfigurationFindingSeverity.INFO,
                    difference.path(),
                    List.of(difference.differenceId()),
                    List.of()
            );
        }
    }

    private RuntimeConfigurationDeterministicStatus status(
            RuntimeConfigurationBranchCoverage sourceCoverage,
            RuntimeConfigurationBranchCoverage targetCoverage,
            ParsedConfigurationSnapshot source,
            ParsedConfigurationSnapshot target,
            List<RuntimeConfigurationReference> references,
            BuildState state,
            List<RuntimeConfigurationFinding> findings
    ) {
        var incomplete = !sourceCoverage.complete()
                || !targetCoverage.complete()
                || source.files().stream().anyMatch(file -> !file.issues().isEmpty())
                || target.files().stream().anyMatch(file -> !file.issues().isEmpty())
                || references.stream().anyMatch(reference ->
                EnumSet.of(
                        RuntimeConfigurationReferenceStatus.UNRESOLVED,
                        RuntimeConfigurationReferenceStatus.CYCLIC
                ).contains(reference.sourceStatus())
                        || EnumSet.of(
                        RuntimeConfigurationReferenceStatus.UNRESOLVED,
                        RuntimeConfigurationReferenceStatus.CYCLIC
                ).contains(reference.targetStatus()));
        if (incomplete) {
            return RuntimeConfigurationDeterministicStatus.INCOMPLETE;
        }
        if (!state.differences.isEmpty()
                || findings.stream().anyMatch(finding ->
                finding.severity() != RuntimeConfigurationFindingSeverity.INFO)) {
            return RuntimeConfigurationDeterministicStatus.REVIEW_REQUIRED;
        }
        return RuntimeConfigurationDeterministicStatus.NO_BLOCKING_ANOMALIES;
    }

    private List<ReferenceTarget> references(String value) {
        var targets = new LinkedHashMap<String, ReferenceTarget>();
        collect(targets, SPRING_REFERENCE.matcher(value), "SPRING_PLACEHOLDER", false);
        collect(targets, EXTERNAL_REFERENCE.matcher(value), "EXTERNAL_PLACEHOLDER", true);
        var localMatcher = LOCAL_EXPRESSION.matcher(value);
        while (localMatcher.find()) {
            var target = localMatcher.group(1);
            if (targets.values().stream().anyMatch(existing ->
                    existing.targetPath().equals(target))) {
                continue;
            }
            targets.putIfAbsent(
                    target + "::VAR_EXPRESSION",
                    new ReferenceTarget(target, "VAR_EXPRESSION", false)
            );
        }
        return List.copyOf(targets.values());
    }

    private void collect(
            Map<String, ReferenceTarget> targets,
            Matcher matcher,
            String kind,
            boolean forcedExternal
    ) {
        while (matcher.find()) {
            var target = matcher.group(1);
            var external = forcedExternal
                    || !(target.startsWith("local.") || target.startsWith("variable."));
            targets.putIfAbsent(
                    target + "::" + kind,
                    new ReferenceTarget(target, kind, external)
            );
        }
    }

    private Object resolveEffective(
            Object value,
            Map<String, Object> values,
            Set<String> visited
    ) {
        if (!(value instanceof String text)) {
            return value;
        }
        var matcher = SPRING_REFERENCE.matcher(text);
        var result = new StringBuffer();
        var replaced = false;
        while (matcher.find()) {
            var target = matcher.group(1);
            if (!(target.startsWith("local.") || target.startsWith("variable."))) {
                return null;
            }
            var resolved = resolveReference(target, values, visited);
            if (resolved == null) {
                return null;
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(String.valueOf(resolved)));
            replaced = true;
        }
        matcher.appendTail(result);
        return replaced ? result.toString() : value;
    }

    private Object resolveReference(
            String path,
            Map<String, Object> values,
            Set<String> visited
    ) {
        if (!visited.add(path)) {
            return null;
        }
        var value = values.get(path);
        var resolved = resolveEffective(value, values, visited);
        visited.remove(path);
        return resolved;
    }

    private Map<String, Object> varScalarValues(ParsedConfigurationSnapshot snapshot) {
        var values = new LinkedHashMap<String, Object>();
        addVarValues(values, snapshot.file(RuntimeConfigurationFileRole.GLOBAL_VAR));
        addVarValues(values, snapshot.file(RuntimeConfigurationFileRole.LOCAL_VAR));
        return values;
    }

    private void addVarValues(Map<String, Object> values, ParsedConfigurationFile file) {
        if (file == null) {
            return;
        }
        for (var document : file.documents()) {
            flattenScalars(document.root()).forEach(node -> values.put(node.path(), node.scalarValue()));
        }
    }

    private Map<NodeLocation, ParsedConfigurationNode> applicationScalars(
            ParsedConfigurationSnapshot snapshot
    ) {
        var values = new LinkedHashMap<NodeLocation, ParsedConfigurationNode>();
        var file = snapshot.file(RuntimeConfigurationFileRole.APPLICATION_YAML);
        if (file == null) {
            return values;
        }
        for (var document : file.documents()) {
            for (var node : flattenScalars(document.root())) {
                values.put(new NodeLocation(
                        RuntimeConfigurationFileRole.APPLICATION_YAML,
                        document.index(),
                        node.path()
                ), node);
            }
        }
        return values;
    }

    private Map<NodeLocation, ParsedConfigurationNode> scalarMap(
            ParsedConfigurationSnapshot snapshot
    ) {
        return scalarLocations(snapshot).stream().collect(
                LinkedHashMap::new,
                (map, leaf) -> map.put(leaf.location, leaf.node),
                Map::putAll
        );
    }

    private List<LocatedNode> scalarLocations(ParsedConfigurationSnapshot snapshot) {
        var values = new ArrayList<LocatedNode>();
        for (var file : snapshot.files()) {
            for (var document : file.documents()) {
                for (var node : flattenScalars(document.root())) {
                    values.add(new LocatedNode(
                            new NodeLocation(file.role(), document.index(), node.path()),
                            node
                    ));
                }
            }
        }
        return values;
    }

    private List<ParsedConfigurationNode> flattenScalars(ParsedConfigurationNode root) {
        var values = new ArrayList<ParsedConfigurationNode>();
        collectScalars(root, values);
        return values;
    }

    private void collectScalars(
            ParsedConfigurationNode node,
            Collection<ParsedConfigurationNode> values
    ) {
        if (node.scalar()) {
            values.add(node);
            return;
        }
        node.children().forEach(child -> collectScalars(child, values));
    }

    private boolean pathExists(
            String current,
            String expected,
            Map<String, Set<String>> edges,
            Set<String> visited
    ) {
        if (current.equals(expected)) {
            return true;
        }
        if (!visited.add(current)) {
            return false;
        }
        for (var next : edges.getOrDefault(current, Set.of())) {
            if (pathExists(next, expected, edges, visited)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, ParsedConfigurationNode> byName(List<ParsedConfigurationNode> nodes) {
        var values = new LinkedHashMap<String, ParsedConfigurationNode>();
        nodes.forEach(node -> values.put(node.name(), node));
        return values;
    }

    private int documentCount(ParsedConfigurationFile file) {
        return file != null ? file.documents().size() : 0;
    }

    private ParsedConfigurationDocument document(ParsedConfigurationFile file, int index) {
        return file != null && index < file.documents().size()
                ? file.documents().get(index)
                : null;
    }

    private String profileToken(
            ParsedConfigurationDocument document,
            RuntimeConfigurationRunPseudonymizer pseudonymizer
    ) {
        return document != null && document.profileValue() != null
                ? pseudonymizer.valueToken(document.profileValue())
                : null;
    }

    private String publicToken(
            ParsedConfigurationNode node,
            RuntimeConfigurationSensitivity sensitivity,
            RuntimeConfigurationRunPseudonymizer pseudonymizer
    ) {
        return node != null
                && node.scalar()
                && sensitivity == RuntimeConfigurationSensitivity.NON_SENSITIVE
                ? pseudonymizer.valueToken(node.scalarValue())
                : null;
    }

    private Integer cardinality(ParsedConfigurationNode node) {
        return node != null && !node.scalar() ? node.children().size() : null;
    }

    private String safePath(String parent, String name) {
        if (name == null || name.startsWith("[")) {
            return (parent != null ? parent : "") + (name != null ? name : "");
        }
        return parent == null || parent.isBlank() ? name : parent + "." + name;
    }

    private String sanitizeReferencePath(
            String path,
            RuntimeConfigurationRunPseudonymizer pseudonymizer
    ) {
        if (path == null || path.isBlank()) {
            return "";
        }
        var result = new StringBuilder();
        for (var segment : path.split("\\.")) {
            if (!result.isEmpty()) {
                result.append('.');
            }
            result.append(dynamicKeyClassifier.dynamic(segment)
                    ? pseudonymizer.keyToken(segment)
                    : segment);
        }
        return result.toString();
    }

    private String canonical(Object value) {
        return value == null ? "<null>" : value.getClass().getName() + ":" + value;
    }

    private boolean containsBranchMarker(String value, String branch) {
        return Pattern.compile("(?i)(^|[^A-Za-z0-9])" + Pattern.quote(branch) + "([^A-Za-z0-9]|$)")
                .matcher(value)
                .find();
    }

    private boolean environmentalPath(String path) {
        var normalized = (path != null ? path : "")
                .replaceAll("([a-z0-9])([A-Z])", "$1.$2")
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9]+", ".");
        for (var token : normalized.split("\\.")) {
            if (ENVIRONMENTAL_PATH_TOKENS.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private boolean placeholder(Object value) {
        return value instanceof String string
                && (string.contains("${") || EXTERNAL_REFERENCE.matcher(string).find());
    }

    private void addFinding(
            List<RuntimeConfigurationFinding> findings,
            String code,
            RuntimeConfigurationFindingSeverity severity,
            String path,
            List<String> differenceIds,
            List<String> referenceIds
    ) {
        findings.add(new RuntimeConfigurationFinding(
                "finding-" + String.format("%03d", findings.size() + 1),
                code,
                severity,
                path,
                differenceIds,
                referenceIds
        ));
    }

    private record NodeLocation(
            RuntimeConfigurationFileRole role,
            int documentIndex,
            String rawPath
    ) {
    }

    private record LocatedNode(
            NodeLocation location,
            ParsedConfigurationNode node
    ) {
    }

    private record ReferenceTarget(
            String targetPath,
            String kind,
            boolean external
    ) {
    }

    private record ReferenceCandidate(
            NodeLocation location,
            String targetPath,
            String kind,
            boolean external
    ) {
    }

    private record ReferenceKey(
            RuntimeConfigurationFileRole role,
            int documentIndex,
            String sourcePath,
            String targetPath,
            String kind
    ) {
    }

    private record ReferenceObservation(
            NodeLocation location,
            String targetPath,
            String kind,
            RuntimeConfigurationReferenceStatus status
    ) {
    }

    private record ReferenceAnalysis(
            Map<ReferenceKey, ReferenceObservation> observations
    ) {
    }

    private static final class BuildState {

        private final RuntimeConfigurationRunPseudonymizer pseudonymizer;
        private final Map<NodeLocation, String> safePaths = new LinkedHashMap<>();
        private final Map<NodeLocation, List<String>> differenceIds = new LinkedHashMap<>();
        private final List<RuntimeConfigurationDifference> differences = new ArrayList<>();

        private BuildState(RuntimeConfigurationRunPseudonymizer pseudonymizer) {
            this.pseudonymizer = pseudonymizer;
        }

        private void addDifference(
                RuntimeConfigurationFileRole role,
                int documentIndex,
                String path,
                RuntimeConfigurationChangeKind kind,
                RuntimeConfigurationValueType sourceType,
                RuntimeConfigurationValueType targetType,
                RuntimeConfigurationSensitivity sensitivity,
                String sourceToken,
                String targetToken,
                String rawPath
        ) {
            var id = "difference-" + String.format("%03d", differences.size() + 1);
            differences.add(new RuntimeConfigurationDifference(
                    id,
                    role,
                    documentIndex,
                    path,
                    kind,
                    sourceType,
                    targetType,
                    sensitivity,
                    sourceToken,
                    targetToken
            ));
            differenceIds.computeIfAbsent(
                    new NodeLocation(role, documentIndex, rawPath),
                    ignored -> new ArrayList<>()
            ).add(id);
        }

        private boolean hasDifference(NodeLocation location) {
            return differenceIds.containsKey(location);
        }

        private List<String> differenceIds(NodeLocation location) {
            return List.copyOf(differenceIds.getOrDefault(location, List.of()));
        }
    }
}
