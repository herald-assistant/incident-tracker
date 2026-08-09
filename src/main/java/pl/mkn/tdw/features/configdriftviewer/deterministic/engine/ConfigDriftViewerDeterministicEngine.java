package pl.mkn.tdw.features.configdriftviewer.deterministic.engine;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerChangeKind;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicStatus;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDifference;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerFinding;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerFindingSeverity;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerReference;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerReferenceStatus;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerSensitivity;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerValueType;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.SanitizedConfigurationDocument;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.SanitizedConfigurationNode;
import pl.mkn.tdw.features.configdriftviewer.deterministic.parse.ParsedConfigurationDocument;
import pl.mkn.tdw.features.configdriftviewer.deterministic.parse.ParsedConfigurationFile;
import pl.mkn.tdw.features.configdriftviewer.deterministic.parse.ParsedConfigurationNode;
import pl.mkn.tdw.features.configdriftviewer.deterministic.parse.ParsedConfigurationSnapshot;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerBranchCoverage;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerFileRole;
import pl.mkn.tdw.features.configdriftviewer.scope.ConfigDriftViewerScope;

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
public class ConfigDriftViewerDeterministicEngine {

    private static final Pattern SPRING_REFERENCE = Pattern.compile("\\$\\{([^}:]+)(?::[^}]*)?}");
    private static final Pattern EXTERNAL_REFERENCE = Pattern.compile("\\$([A-Za-z0-9_.-]+)\\$");
    private static final Pattern LOCAL_EXPRESSION = Pattern.compile(
            "(?<![A-Za-z0-9_.])((?:local|variable)\\.[A-Za-z0-9_.-]+)"
    );
    private final ConfigDriftViewerSensitivityClassifier sensitivityClassifier =
            new ConfigDriftViewerSensitivityClassifier();
    private final ConfigDriftViewerDynamicKeyClassifier dynamicKeyClassifier =
            new ConfigDriftViewerDynamicKeyClassifier();

    public ConfigDriftViewerDeterministicContext build(
            ConfigDriftViewerScope scope,
            ConfigDriftViewerBranchCoverage sourceCoverage,
            ConfigDriftViewerBranchCoverage targetCoverage,
            ParsedConfigurationSnapshot source,
            ParsedConfigurationSnapshot target
    ) {
        return build(
                scope,
                sourceCoverage,
                targetCoverage,
                source,
                target,
                new ConfigDriftViewerRunPseudonymizer()
        );
    }

    ConfigDriftViewerDeterministicContext build(
            ConfigDriftViewerScope scope,
            ConfigDriftViewerBranchCoverage sourceCoverage,
            ConfigDriftViewerBranchCoverage targetCoverage,
            ParsedConfigurationSnapshot source,
            ParsedConfigurationSnapshot target,
            ConfigDriftViewerRunPseudonymizer pseudonymizer
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

        return new ConfigDriftViewerDeterministicContext(
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
        for (var role : ConfigDriftViewerFileRole.values()) {
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
            ConfigDriftViewerFileRole role,
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
            ConfigDriftViewerFileRole role,
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

    private ConfigDriftViewerChangeKind relation(
            ParsedConfigurationNode source,
            ParsedConfigurationNode target,
            List<SanitizedConfigurationNode> children
    ) {
        if (source == null) {
            return ConfigDriftViewerChangeKind.ADDED;
        }
        if (target == null) {
            return ConfigDriftViewerChangeKind.REMOVED;
        }
        if (source.type() != target.type()) {
            return ConfigDriftViewerChangeKind.TYPE_CHANGED;
        }
        if (source.scalar()) {
            return Objects.equals(canonical(source.scalarValue()), canonical(target.scalarValue()))
                    ? ConfigDriftViewerChangeKind.UNCHANGED
                    : ConfigDriftViewerChangeKind.CHANGED;
        }
        return children.stream().allMatch(child ->
                child.relation() == ConfigDriftViewerChangeKind.UNCHANGED)
                ? ConfigDriftViewerChangeKind.UNCHANGED
                : ConfigDriftViewerChangeKind.CHANGED;
    }

    private boolean differenceWorthy(
            ParsedConfigurationNode source,
            ParsedConfigurationNode target,
            ConfigDriftViewerChangeKind relation
    ) {
        if (relation == ConfigDriftViewerChangeKind.UNCHANGED) {
            return false;
        }
        if (relation == ConfigDriftViewerChangeKind.TYPE_CHANGED) {
            return true;
        }
        return (source == null || source.scalar()) && (target == null || target.scalar());
    }

    private List<ConfigDriftViewerReference> buildReferences(
            ParsedConfigurationSnapshot source,
            ParsedConfigurationSnapshot target,
            BuildState state
    ) {
        var sourceAnalysis = analyzeReferences(source);
        var targetAnalysis = analyzeReferences(target);
        var keys = new LinkedHashSet<ReferenceKey>();
        keys.addAll(sourceAnalysis.observations.keySet());
        keys.addAll(targetAnalysis.observations.keySet());
        var results = new ArrayList<ConfigDriftViewerReference>();
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
            var reference = new ConfigDriftViewerReference(
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
                            : ConfigDriftViewerReferenceStatus.ABSENT,
                    targetObservation != null
                            ? targetObservation.status
                            : ConfigDriftViewerReferenceStatus.ABSENT
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
                    ConfigDriftViewerFileRole.GLOBAL_VAR,
                    ConfigDriftViewerFileRole.LOCAL_VAR
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
                    ? ConfigDriftViewerReferenceStatus.EXTERNAL
                    : !resolvablePaths.contains(candidate.targetPath)
                    ? ConfigDriftViewerReferenceStatus.UNRESOLVED
                    : pathExists(candidate.targetPath, candidate.location.rawPath, edges, new LinkedHashSet<>())
                    ? ConfigDriftViewerReferenceStatus.CYCLIC
                    : ConfigDriftViewerReferenceStatus.RESOLVED;
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
                    ConfigDriftViewerChangeKind.EFFECTIVE_CHANGED,
                    sourceNode.type(),
                    targetNode.type(),
                    sensitivity,
                    sensitivity == ConfigDriftViewerSensitivity.SENSITIVE
                            ? null
                            : state.pseudonymizer.valueToken(sourceEffective),
                    sensitivity == ConfigDriftViewerSensitivity.SENSITIVE
                            ? null
                            : state.pseudonymizer.valueToken(targetEffective),
                    location.rawPath
            );
        }
    }

    private List<ConfigDriftViewerFinding> buildFindings(
            ConfigDriftViewerBranchCoverage sourceCoverage,
            ConfigDriftViewerBranchCoverage targetCoverage,
            ParsedConfigurationSnapshot source,
            ParsedConfigurationSnapshot target,
            List<ConfigDriftViewerReference> references,
            BuildState state
    ) {
        var findings = new ArrayList<ConfigDriftViewerFinding>();
        if (!sourceCoverage.complete()) {
            addFinding(findings, "INCOMPLETE_SOURCE_COVERAGE", ConfigDriftViewerFindingSeverity.ERROR, "", List.of(), List.of());
        }
        if (!targetCoverage.complete()) {
            addFinding(findings, "INCOMPLETE_TARGET_COVERAGE", ConfigDriftViewerFindingSeverity.ERROR, "", List.of(), List.of());
        }
        var parserCausedReferenceIds = new LinkedHashSet<String>();
        parserCausedReferenceIds.addAll(addParserFindings(
                findings, source, "SOURCE", false, references
        ));
        parserCausedReferenceIds.addAll(addParserFindings(
                findings, target, "TARGET", true, references
        ));

        var targetLeaves = scalarMap(target);

        for (var difference : state.differences) {
            if (difference.sensitivity() == ConfigDriftViewerSensitivity.SENSITIVE
                    && hardcodedSensitiveAddition(difference, targetLeaves)) {
                addFinding(
                        findings,
                        "HARDCODED_SENSITIVE_VALUE_ADDED",
                        ConfigDriftViewerFindingSeverity.ERROR,
                        difference.path(),
                        List.of(difference.differenceId()),
                        List.of()
                );
            }
        }

        for (var reference : references) {
            if (reference.sourceStatus() == ConfigDriftViewerReferenceStatus.CYCLIC
                    || reference.targetStatus() == ConfigDriftViewerReferenceStatus.CYCLIC) {
                addFinding(
                        findings,
                        "CYCLIC_REFERENCE",
                        ConfigDriftViewerFindingSeverity.ERROR,
                        reference.sourcePath(),
                        List.of(),
                        List.of(reference.referenceId())
                );
            } else if ((reference.sourceStatus() == ConfigDriftViewerReferenceStatus.UNRESOLVED
                    || reference.targetStatus() == ConfigDriftViewerReferenceStatus.UNRESOLVED)
                    && !parserCausedReferenceIds.contains(reference.referenceId())) {
                addFinding(
                        findings,
                        "UNRESOLVED_REFERENCE",
                        ConfigDriftViewerFindingSeverity.ERROR,
                        reference.sourcePath(),
                        List.of(),
                        List.of(reference.referenceId())
                );
            }
        }

        return List.copyOf(findings);
    }

    private Set<String> addParserFindings(
            List<ConfigDriftViewerFinding> findings,
            ParsedConfigurationSnapshot snapshot,
            String branchRole,
            boolean targetSide,
            List<ConfigDriftViewerReference> references
    ) {
        var linkedReferenceIds = new LinkedHashSet<String>();
        for (var file : snapshot.files()) {
            for (var issue : file.issues()) {
                var matchingReferences = references.stream()
                        .filter(reference -> targetSide
                                ? reference.targetStatus() == ConfigDriftViewerReferenceStatus.UNRESOLVED
                                : reference.sourceStatus() == ConfigDriftViewerReferenceStatus.UNRESOLVED)
                        .filter(reference -> matchesIssuePath(reference.targetPath(), issue.path()))
                        .toList();
                var linkedReferences = matchingReferences.size() == 1
                        ? matchingReferences
                        : List.<ConfigDriftViewerReference>of();
                var referenceIds = linkedReferences.stream()
                        .map(ConfigDriftViewerReference::referenceId)
                        .toList();
                linkedReferenceIds.addAll(referenceIds);
                addFinding(
                        findings,
                        branchRole + "_" + issue.code(),
                        ConfigDriftViewerFindingSeverity.ERROR,
                        linkedReferences.isEmpty()
                                ? issue.path()
                                : linkedReferences.get(0).targetPath(),
                        List.of(),
                        referenceIds,
                        file.path(),
                        issue.line()
                );
            }
        }
        return Set.copyOf(linkedReferenceIds);
    }

    private boolean matchesIssuePath(String referencePath, String issuePath) {
        return issuePath != null
                && !issuePath.isBlank()
                && referencePath != null
                && (referencePath.equals(issuePath) || referencePath.endsWith("." + issuePath));
    }

    private boolean hardcodedSensitiveAddition(
            ConfigDriftViewerDifference difference,
            Map<NodeLocation, ParsedConfigurationNode> targetLeaves
    ) {
        if (difference.kind() != ConfigDriftViewerChangeKind.ADDED) {
            return false;
        }
        var targetNode = targetLeaves.get(new NodeLocation(
                difference.role(), difference.documentIndex(), difference.path()
        ));
        if (targetNode == null || targetNode.scalarValue() == null) {
            return false;
        }
        if (targetNode.scalarValue() instanceof String value) {
            return !value.isBlank() && !placeholder(value);
        }
        return true;
    }

    private ConfigDriftViewerDeterministicStatus status(
            ConfigDriftViewerBranchCoverage sourceCoverage,
            ConfigDriftViewerBranchCoverage targetCoverage,
            ParsedConfigurationSnapshot source,
            ParsedConfigurationSnapshot target,
            List<ConfigDriftViewerReference> references,
            BuildState state,
            List<ConfigDriftViewerFinding> findings
    ) {
        var incomplete = !sourceCoverage.complete()
                || !targetCoverage.complete()
                || source.files().stream().anyMatch(file -> !file.issues().isEmpty())
                || target.files().stream().anyMatch(file -> !file.issues().isEmpty())
                || references.stream().anyMatch(reference ->
                EnumSet.of(
                        ConfigDriftViewerReferenceStatus.UNRESOLVED,
                        ConfigDriftViewerReferenceStatus.CYCLIC
                ).contains(reference.sourceStatus())
                        || EnumSet.of(
                        ConfigDriftViewerReferenceStatus.UNRESOLVED,
                        ConfigDriftViewerReferenceStatus.CYCLIC
                ).contains(reference.targetStatus()));
        if (incomplete) {
            return ConfigDriftViewerDeterministicStatus.INCOMPLETE;
        }
        if (!state.differences.isEmpty()
                || findings.stream().anyMatch(finding ->
                finding.severity() != ConfigDriftViewerFindingSeverity.INFO)) {
            return ConfigDriftViewerDeterministicStatus.REVIEW_REQUIRED;
        }
        return ConfigDriftViewerDeterministicStatus.NO_BLOCKING_ANOMALIES;
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
        addVarValues(values, snapshot.file(ConfigDriftViewerFileRole.GLOBAL_VAR));
        addVarValues(values, snapshot.file(ConfigDriftViewerFileRole.LOCAL_VAR));
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
        var file = snapshot.file(ConfigDriftViewerFileRole.APPLICATION_YAML);
        if (file == null) {
            return values;
        }
        for (var document : file.documents()) {
            for (var node : flattenScalars(document.root())) {
                values.put(new NodeLocation(
                        ConfigDriftViewerFileRole.APPLICATION_YAML,
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
            ConfigDriftViewerRunPseudonymizer pseudonymizer
    ) {
        return document != null && document.profileValue() != null
                ? pseudonymizer.valueToken(document.profileValue())
                : null;
    }

    private String publicToken(
            ParsedConfigurationNode node,
            ConfigDriftViewerSensitivity sensitivity,
            ConfigDriftViewerRunPseudonymizer pseudonymizer
    ) {
        return node != null
                && node.scalar()
                && sensitivity == ConfigDriftViewerSensitivity.NON_SENSITIVE
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
            ConfigDriftViewerRunPseudonymizer pseudonymizer
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

    private boolean placeholder(Object value) {
        return value instanceof String string
                && (string.contains("${") || EXTERNAL_REFERENCE.matcher(string).find());
    }

    private void addFinding(
            List<ConfigDriftViewerFinding> findings,
            String code,
            ConfigDriftViewerFindingSeverity severity,
            String path,
            List<String> differenceIds,
            List<String> referenceIds
    ) {
        addFinding(findings, code, severity, path, differenceIds, referenceIds, null, null);
    }

    private void addFinding(
            List<ConfigDriftViewerFinding> findings,
            String code,
            ConfigDriftViewerFindingSeverity severity,
            String path,
            List<String> differenceIds,
            List<String> referenceIds,
            String filePath,
            Integer line
    ) {
        findings.add(new ConfigDriftViewerFinding(
                "finding-" + String.format("%03d", findings.size() + 1),
                code,
                severity,
                path,
                differenceIds,
                referenceIds,
                filePath,
                line
        ));
    }

    private record NodeLocation(
            ConfigDriftViewerFileRole role,
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
            ConfigDriftViewerFileRole role,
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
            ConfigDriftViewerReferenceStatus status
    ) {
    }

    private record ReferenceAnalysis(
            Map<ReferenceKey, ReferenceObservation> observations
    ) {
    }

    private static final class BuildState {

        private final ConfigDriftViewerRunPseudonymizer pseudonymizer;
        private final Map<NodeLocation, String> safePaths = new LinkedHashMap<>();
        private final Map<NodeLocation, List<String>> differenceIds = new LinkedHashMap<>();
        private final List<ConfigDriftViewerDifference> differences = new ArrayList<>();

        private BuildState(ConfigDriftViewerRunPseudonymizer pseudonymizer) {
            this.pseudonymizer = pseudonymizer;
        }

        private void addDifference(
                ConfigDriftViewerFileRole role,
                int documentIndex,
                String path,
                ConfigDriftViewerChangeKind kind,
                ConfigDriftViewerValueType sourceType,
                ConfigDriftViewerValueType targetType,
                ConfigDriftViewerSensitivity sensitivity,
                String sourceToken,
                String targetToken,
                String rawPath
        ) {
            var id = "difference-" + String.format("%03d", differences.size() + 1);
            differences.add(new ConfigDriftViewerDifference(
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
