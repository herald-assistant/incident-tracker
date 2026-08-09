package pl.mkn.tdw.features.configdriftviewer.deterministic.projection;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerChangeKind;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDifference;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerValueType;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.SanitizedConfigurationDocument;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.SanitizedConfigurationNode;
import pl.mkn.tdw.features.configdriftviewer.deterministic.parse.ParsedConfigurationDocument;
import pl.mkn.tdw.features.configdriftviewer.deterministic.parse.ParsedConfigurationFile;
import pl.mkn.tdw.features.configdriftviewer.deterministic.parse.ParsedConfigurationNode;
import pl.mkn.tdw.features.configdriftviewer.deterministic.parse.ParsedConfigurationSnapshot;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerFileRole;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ConfigDriftViewerDiffProjectionBuilder {

    private static final Pattern SPRING_REFERENCE = Pattern.compile("\\$\\{([^}:]+)(?::[^}]*)?}");

    public ConfigDriftViewerDiffProjection build(
            ParsedConfigurationSnapshot source,
            ParsedConfigurationSnapshot target,
            ConfigDriftViewerDeterministicContext deterministicContext
    ) {
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(target, "target is required");
        Objects.requireNonNull(deterministicContext, "deterministicContext is required");
        validateBranches(source, target, deterministicContext);

        var sanitizedDocuments = indexDocuments(deterministicContext.documents());
        var differences = indexDifferences(deterministicContext.differences());
        var sourceValues = varScalarValues(source);
        var targetValues = varScalarValues(target);
        var attachedDifferenceIds = new LinkedHashSet<String>();
        var files = new ArrayList<ConfigDriftViewerDiffFile>();

        for (var role : ConfigDriftViewerFileRole.values()) {
            var sourceFile = source.file(role);
            var targetFile = target.file(role);
            if (sourceFile == null && targetFile == null) {
                continue;
            }
            files.add(buildFile(
                    role,
                    sourceFile,
                    targetFile,
                    sanitizedDocuments,
                    differences,
                    sourceValues,
                    targetValues,
                    attachedDifferenceIds
            ));
        }

        var expectedDifferenceIds = deterministicContext.differences().stream()
                .map(ConfigDriftViewerDifference::differenceId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!attachedDifferenceIds.equals(expectedDifferenceIds)) {
            var missing = new LinkedHashSet<>(expectedDifferenceIds);
            missing.removeAll(attachedDifferenceIds);
            throw new IllegalArgumentException(
                    "Deterministic differences cannot be mapped to operator projection: " + missing
            );
        }

        return new ConfigDriftViewerDiffProjection(
                source.branch(),
                target.branch(),
                files
        );
    }

    private ConfigDriftViewerDiffFile buildFile(
            ConfigDriftViewerFileRole role,
            ParsedConfigurationFile sourceFile,
            ParsedConfigurationFile targetFile,
            Map<DocumentLocation, SanitizedConfigurationDocument> sanitizedDocuments,
            Map<DifferenceLocation, List<ConfigDriftViewerDifference>> differences,
            Map<String, Object> sourceValues,
            Map<String, Object> targetValues,
            LinkedHashSet<String> attachedDifferenceIds
    ) {
        var documents = new ArrayList<ConfigDriftViewerDiffDocument>();
        var count = Math.max(documentCount(sourceFile), documentCount(targetFile));
        for (var index = 0; index < count; index++) {
            var sourceDocument = document(sourceFile, index);
            var targetDocument = document(targetFile, index);
            var sanitizedDocument = sanitizedDocuments.get(new DocumentLocation(role, index));
            if (sanitizedDocument == null) {
                throw new IllegalArgumentException(
                        "Missing sanitized document for " + role + " document " + index
                );
            }
            documents.add(buildDocument(
                    role,
                    index,
                    sourceDocument,
                    targetDocument,
                    sanitizedDocument,
                    differences,
                    sourceValues,
                    targetValues,
                    attachedDifferenceIds
            ));
        }

        return new ConfigDriftViewerDiffFile(
                role,
                format(role),
                sourceFile != null ? sourceFile.path() : null,
                targetFile != null ? targetFile.path() : null,
                sourceFile != null,
                targetFile != null,
                documents
        );
    }

    private ConfigDriftViewerDiffDocument buildDocument(
            ConfigDriftViewerFileRole role,
            int documentIndex,
            ParsedConfigurationDocument source,
            ParsedConfigurationDocument target,
            SanitizedConfigurationDocument sanitized,
            Map<DifferenceLocation, List<ConfigDriftViewerDifference>> differences,
            Map<String, Object> sourceValues,
            Map<String, Object> targetValues,
            LinkedHashSet<String> attachedDifferenceIds
    ) {
        if (sanitized.sourcePresent() != (source != null)
                || sanitized.targetPresent() != (target != null)) {
            throw new IllegalArgumentException(
                    "Document presence does not match deterministic context for "
                            + role + " document " + documentIndex
            );
        }

        return new ConfigDriftViewerDiffDocument(
                documentIndex,
                source != null,
                target != null,
                profileValue(source),
                profileValue(target),
                buildNode(
                        role,
                        documentIndex,
                        source != null ? source.root() : null,
                        target != null ? target.root() : null,
                        sanitized.root(),
                        differences,
                        sourceValues,
                        targetValues,
                        attachedDifferenceIds
                )
        );
    }

    private ConfigDriftViewerDiffNode buildNode(
            ConfigDriftViewerFileRole role,
            int documentIndex,
            ParsedConfigurationNode source,
            ParsedConfigurationNode target,
            SanitizedConfigurationNode sanitized,
            Map<DifferenceLocation, List<ConfigDriftViewerDifference>> differences,
            Map<String, Object> sourceValues,
            Map<String, Object> targetValues,
            LinkedHashSet<String> attachedDifferenceIds
    ) {
        if (source == null && target == null) {
            throw new IllegalArgumentException("Operator projection node requires at least one side");
        }
        if (sanitized == null) {
            throw new IllegalArgumentException(
                    "Missing sanitized node for " + role + " document " + documentIndex
            );
        }
        validateTypes(source, target, sanitized);

        var directDifferences = differences.getOrDefault(
                new DifferenceLocation(role, documentIndex, sanitized.path()),
                List.of()
        );
        if (directDifferences.size() > 1) {
            throw new IllegalArgumentException(
                    "Multiple deterministic differences mapped to one node: "
                            + role + " document " + documentIndex + " path " + sanitized.path()
            );
        }
        var changeKind = directDifferences.isEmpty()
                ? sanitized.relation()
                : directDifferences.get(0).kind();
        validateChangeKind(sanitized.relation(), changeKind);
        var differenceIds = directDifferences.stream()
                .map(ConfigDriftViewerDifference::differenceId)
                .toList();
        attachedDifferenceIds.addAll(differenceIds);

        var sourceChildren = byName(source != null ? source.children() : List.of());
        var targetChildren = byName(target != null ? target.children() : List.of());
        var names = new LinkedHashSet<String>();
        names.addAll(sourceChildren.keySet());
        names.addAll(targetChildren.keySet());
        if (names.size() != sanitized.children().size()) {
            throw new IllegalArgumentException(
                    "Node shape does not match deterministic context for "
                            + role + " document " + documentIndex
            );
        }

        var children = new ArrayList<ConfigDriftViewerDiffNode>();
        var sanitizedIndex = 0;
        for (var name : names) {
            children.add(buildNode(
                    role,
                    documentIndex,
                    sourceChildren.get(name),
                    targetChildren.get(name),
                    sanitized.children().get(sanitizedIndex++),
                    differences,
                    sourceValues,
                    targetValues,
                    attachedDifferenceIds
            ));
        }

        var rawNode = source != null ? source : target;
        var effectiveValues = effectiveValues(changeKind, source, target, sourceValues, targetValues);
        return new ConfigDriftViewerDiffNode(
                rawNode.name(),
                rawNode.path(),
                changeKind,
                nodeValue(source),
                nodeValue(target),
                effectiveValues.source(),
                effectiveValues.target(),
                differenceIds,
                children
        );
    }

    private void validateBranches(
            ParsedConfigurationSnapshot source,
            ParsedConfigurationSnapshot target,
            ConfigDriftViewerDeterministicContext deterministicContext
    ) {
        if (!Objects.equals(source.branch(), deterministicContext.sourceBranch())
                || !Objects.equals(target.branch(), deterministicContext.targetBranch())) {
            throw new IllegalArgumentException(
                    "Parsed snapshots do not match deterministic context branches"
            );
        }
    }

    private void validateTypes(
            ParsedConfigurationNode source,
            ParsedConfigurationNode target,
            SanitizedConfigurationNode sanitized
    ) {
        var sourceType = source != null ? source.type() : null;
        var targetType = target != null ? target.type() : null;
        if (sourceType != sanitized.sourceType() || targetType != sanitized.targetType()) {
            throw new IllegalArgumentException(
                    "Parsed node types do not match deterministic context at " + sanitized.path()
            );
        }
    }

    private void validateChangeKind(
            ConfigDriftViewerChangeKind structuralKind,
            ConfigDriftViewerChangeKind projectedKind
    ) {
        if (structuralKind == projectedKind) {
            return;
        }
        if (structuralKind == ConfigDriftViewerChangeKind.UNCHANGED
                && projectedKind == ConfigDriftViewerChangeKind.EFFECTIVE_CHANGED) {
            return;
        }
        throw new IllegalArgumentException(
                "Difference kind " + projectedKind
                        + " does not match structural relation " + structuralKind
        );
    }

    private ConfigDriftViewerDiffValue nodeValue(ParsedConfigurationNode node) {
        if (node == null) {
            return ConfigDriftViewerDiffValue.absent();
        }
        return new ConfigDriftViewerDiffValue(
                ConfigDriftViewerDiffValuePresence.PRESENT,
                node.type(),
                node.scalar() ? node.scalarValue() : null,
                node.scalar() ? null : node.children().size()
        );
    }

    private ConfigDriftViewerDiffValue profileValue(ParsedConfigurationDocument document) {
        if (document == null || document.profileValue() == null) {
            return ConfigDriftViewerDiffValue.absent();
        }
        var value = immutableValue(document.profileValue());
        var type = valueType(value);
        return new ConfigDriftViewerDiffValue(
                ConfigDriftViewerDiffValuePresence.PRESENT,
                type,
                value,
                cardinality(value)
        );
    }

    private EffectiveValues effectiveValues(
            ConfigDriftViewerChangeKind changeKind,
            ParsedConfigurationNode source,
            ParsedConfigurationNode target,
            Map<String, Object> sourceValues,
            Map<String, Object> targetValues
    ) {
        if (changeKind != ConfigDriftViewerChangeKind.EFFECTIVE_CHANGED
                || source == null
                || target == null
                || !source.scalar()
                || !target.scalar()) {
            return EffectiveValues.empty();
        }
        var sourceEffective = resolveEffective(source.scalarValue(), sourceValues, new LinkedHashSet<>());
        var targetEffective = resolveEffective(target.scalarValue(), targetValues, new LinkedHashSet<>());
        if (sourceEffective == null || targetEffective == null) {
            return EffectiveValues.empty();
        }
        return new EffectiveValues(
                value(sourceEffective),
                value(targetEffective)
        );
    }

    private ConfigDriftViewerDiffValue value(Object rawValue) {
        var value = immutableValue(rawValue);
        var type = valueType(value);
        return new ConfigDriftViewerDiffValue(
                ConfigDriftViewerDiffValuePresence.PRESENT,
                type,
                value,
                cardinality(value)
        );
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

    private List<ParsedConfigurationNode> flattenScalars(ParsedConfigurationNode root) {
        var values = new ArrayList<ParsedConfigurationNode>();
        collectScalars(root, values);
        return values;
    }

    private void collectScalars(
            ParsedConfigurationNode node,
            List<ParsedConfigurationNode> values
    ) {
        if (node.scalar()) {
            values.add(node);
            return;
        }
        node.children().forEach(child -> collectScalars(child, values));
    }

    private Object immutableValue(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(this::immutableValue).toList();
        }
        if (value instanceof Map<?, ?> map) {
            var copy = new LinkedHashMap<Object, Object>();
            map.forEach((key, entryValue) -> copy.put(key, immutableValue(entryValue)));
            return Collections.unmodifiableMap(copy);
        }
        return value;
    }

    private ConfigDriftViewerValueType valueType(Object value) {
        if (value == null) {
            return ConfigDriftViewerValueType.NULL;
        }
        if (value instanceof Map<?, ?>) {
            return ConfigDriftViewerValueType.MAP;
        }
        if (value instanceof List<?>) {
            return ConfigDriftViewerValueType.LIST;
        }
        if (value instanceof Boolean) {
            return ConfigDriftViewerValueType.BOOLEAN;
        }
        if (value instanceof Number || value instanceof BigDecimal || value instanceof BigInteger) {
            return ConfigDriftViewerValueType.NUMBER;
        }
        if (value instanceof CharSequence || value instanceof Character) {
            return ConfigDriftViewerValueType.STRING;
        }
        return ConfigDriftViewerValueType.UNKNOWN;
    }

    private Integer cardinality(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.size();
        }
        if (value instanceof List<?> list) {
            return list.size();
        }
        return null;
    }

    private Map<DocumentLocation, SanitizedConfigurationDocument> indexDocuments(
            List<SanitizedConfigurationDocument> documents
    ) {
        var values = new LinkedHashMap<DocumentLocation, SanitizedConfigurationDocument>();
        for (var document : documents) {
            var location = new DocumentLocation(document.role(), document.documentIndex());
            if (values.put(location, document) != null) {
                throw new IllegalArgumentException("Duplicate sanitized document " + location);
            }
        }
        return values;
    }

    private Map<DifferenceLocation, List<ConfigDriftViewerDifference>> indexDifferences(
            List<ConfigDriftViewerDifference> differences
    ) {
        var values = new LinkedHashMap<DifferenceLocation, List<ConfigDriftViewerDifference>>();
        for (var difference : differences) {
            var location = new DifferenceLocation(
                    difference.role(),
                    difference.documentIndex(),
                    difference.path()
            );
            var existing = values.getOrDefault(location, List.of());
            var updated = new ArrayList<>(existing);
            updated.add(difference);
            values.put(location, List.copyOf(updated));
        }
        return values;
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

    private ConfigDriftViewerDiffFileFormat format(ConfigDriftViewerFileRole role) {
        return role == ConfigDriftViewerFileRole.APPLICATION_YAML
                ? ConfigDriftViewerDiffFileFormat.YAML
                : ConfigDriftViewerDiffFileFormat.VAR;
    }

    private record DocumentLocation(
            ConfigDriftViewerFileRole role,
            int documentIndex
    ) {
    }

    private record DifferenceLocation(
            ConfigDriftViewerFileRole role,
            int documentIndex,
            String path
    ) {
    }

    private record EffectiveValues(
            ConfigDriftViewerDiffValue source,
            ConfigDriftViewerDiffValue target
    ) {

        private static EffectiveValues empty() {
            return new EffectiveValues(null, null);
        }
    }
}
