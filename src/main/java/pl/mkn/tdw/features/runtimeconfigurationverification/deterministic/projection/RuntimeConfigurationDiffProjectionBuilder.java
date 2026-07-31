package pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.projection;

import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationChangeKind;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDifference;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationValueType;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.SanitizedConfigurationDocument;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.SanitizedConfigurationNode;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.parse.ParsedConfigurationDocument;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.parse.ParsedConfigurationFile;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.parse.ParsedConfigurationNode;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.parse.ParsedConfigurationSnapshot;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationFileRole;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class RuntimeConfigurationDiffProjectionBuilder {

    public RuntimeConfigurationDiffProjection build(
            ParsedConfigurationSnapshot source,
            ParsedConfigurationSnapshot target,
            RuntimeConfigurationDeterministicContext deterministicContext
    ) {
        Objects.requireNonNull(source, "source is required");
        Objects.requireNonNull(target, "target is required");
        Objects.requireNonNull(deterministicContext, "deterministicContext is required");
        validateBranches(source, target, deterministicContext);

        var sanitizedDocuments = indexDocuments(deterministicContext.documents());
        var differences = indexDifferences(deterministicContext.differences());
        var attachedDifferenceIds = new LinkedHashSet<String>();
        var files = new ArrayList<RuntimeConfigurationDiffFile>();

        for (var role : RuntimeConfigurationFileRole.values()) {
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
                    attachedDifferenceIds
            ));
        }

        var expectedDifferenceIds = deterministicContext.differences().stream()
                .map(RuntimeConfigurationDifference::differenceId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (!attachedDifferenceIds.equals(expectedDifferenceIds)) {
            var missing = new LinkedHashSet<>(expectedDifferenceIds);
            missing.removeAll(attachedDifferenceIds);
            throw new IllegalArgumentException(
                    "Deterministic differences cannot be mapped to operator projection: " + missing
            );
        }

        return new RuntimeConfigurationDiffProjection(
                source.branch(),
                target.branch(),
                files
        );
    }

    private RuntimeConfigurationDiffFile buildFile(
            RuntimeConfigurationFileRole role,
            ParsedConfigurationFile sourceFile,
            ParsedConfigurationFile targetFile,
            Map<DocumentLocation, SanitizedConfigurationDocument> sanitizedDocuments,
            Map<DifferenceLocation, List<RuntimeConfigurationDifference>> differences,
            LinkedHashSet<String> attachedDifferenceIds
    ) {
        var documents = new ArrayList<RuntimeConfigurationDiffDocument>();
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
                    attachedDifferenceIds
            ));
        }

        return new RuntimeConfigurationDiffFile(
                role,
                format(role),
                sourceFile != null ? sourceFile.path() : null,
                targetFile != null ? targetFile.path() : null,
                sourceFile != null,
                targetFile != null,
                documents
        );
    }

    private RuntimeConfigurationDiffDocument buildDocument(
            RuntimeConfigurationFileRole role,
            int documentIndex,
            ParsedConfigurationDocument source,
            ParsedConfigurationDocument target,
            SanitizedConfigurationDocument sanitized,
            Map<DifferenceLocation, List<RuntimeConfigurationDifference>> differences,
            LinkedHashSet<String> attachedDifferenceIds
    ) {
        if (sanitized.sourcePresent() != (source != null)
                || sanitized.targetPresent() != (target != null)) {
            throw new IllegalArgumentException(
                    "Document presence does not match deterministic context for "
                            + role + " document " + documentIndex
            );
        }

        return new RuntimeConfigurationDiffDocument(
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
                        attachedDifferenceIds
                )
        );
    }

    private RuntimeConfigurationDiffNode buildNode(
            RuntimeConfigurationFileRole role,
            int documentIndex,
            ParsedConfigurationNode source,
            ParsedConfigurationNode target,
            SanitizedConfigurationNode sanitized,
            Map<DifferenceLocation, List<RuntimeConfigurationDifference>> differences,
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
                .map(RuntimeConfigurationDifference::differenceId)
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

        var children = new ArrayList<RuntimeConfigurationDiffNode>();
        var sanitizedIndex = 0;
        for (var name : names) {
            children.add(buildNode(
                    role,
                    documentIndex,
                    sourceChildren.get(name),
                    targetChildren.get(name),
                    sanitized.children().get(sanitizedIndex++),
                    differences,
                    attachedDifferenceIds
            ));
        }

        var rawNode = source != null ? source : target;
        return new RuntimeConfigurationDiffNode(
                rawNode.name(),
                rawNode.path(),
                changeKind,
                nodeValue(source),
                nodeValue(target),
                differenceIds,
                children
        );
    }

    private void validateBranches(
            ParsedConfigurationSnapshot source,
            ParsedConfigurationSnapshot target,
            RuntimeConfigurationDeterministicContext deterministicContext
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
            RuntimeConfigurationChangeKind structuralKind,
            RuntimeConfigurationChangeKind projectedKind
    ) {
        if (structuralKind == projectedKind) {
            return;
        }
        if (structuralKind == RuntimeConfigurationChangeKind.UNCHANGED
                && projectedKind == RuntimeConfigurationChangeKind.EFFECTIVE_CHANGED) {
            return;
        }
        throw new IllegalArgumentException(
                "Difference kind " + projectedKind
                        + " does not match structural relation " + structuralKind
        );
    }

    private RuntimeConfigurationDiffValue nodeValue(ParsedConfigurationNode node) {
        if (node == null) {
            return RuntimeConfigurationDiffValue.absent();
        }
        return new RuntimeConfigurationDiffValue(
                RuntimeConfigurationDiffValuePresence.PRESENT,
                node.type(),
                node.scalar() ? node.scalarValue() : null,
                node.scalar() ? null : node.children().size()
        );
    }

    private RuntimeConfigurationDiffValue profileValue(ParsedConfigurationDocument document) {
        if (document == null || document.profileValue() == null) {
            return RuntimeConfigurationDiffValue.absent();
        }
        var value = immutableValue(document.profileValue());
        var type = valueType(value);
        return new RuntimeConfigurationDiffValue(
                RuntimeConfigurationDiffValuePresence.PRESENT,
                type,
                value,
                cardinality(value)
        );
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

    private RuntimeConfigurationValueType valueType(Object value) {
        if (value == null) {
            return RuntimeConfigurationValueType.NULL;
        }
        if (value instanceof Map<?, ?>) {
            return RuntimeConfigurationValueType.MAP;
        }
        if (value instanceof List<?>) {
            return RuntimeConfigurationValueType.LIST;
        }
        if (value instanceof Boolean) {
            return RuntimeConfigurationValueType.BOOLEAN;
        }
        if (value instanceof Number || value instanceof BigDecimal || value instanceof BigInteger) {
            return RuntimeConfigurationValueType.NUMBER;
        }
        if (value instanceof CharSequence || value instanceof Character) {
            return RuntimeConfigurationValueType.STRING;
        }
        return RuntimeConfigurationValueType.UNKNOWN;
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

    private Map<DifferenceLocation, List<RuntimeConfigurationDifference>> indexDifferences(
            List<RuntimeConfigurationDifference> differences
    ) {
        var values = new LinkedHashMap<DifferenceLocation, List<RuntimeConfigurationDifference>>();
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

    private RuntimeConfigurationDiffFileFormat format(RuntimeConfigurationFileRole role) {
        return role == RuntimeConfigurationFileRole.APPLICATION_YAML
                ? RuntimeConfigurationDiffFileFormat.YAML
                : RuntimeConfigurationDiffFileFormat.VAR;
    }

    private record DocumentLocation(
            RuntimeConfigurationFileRole role,
            int documentIndex
    ) {
    }

    private record DifferenceLocation(
            RuntimeConfigurationFileRole role,
            int documentIndex,
            String path
    ) {
    }
}
