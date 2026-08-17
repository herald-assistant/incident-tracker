package pl.mkn.tdw.features.uiexplorer.ai.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerSourceContextSnapshot;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerClaimConfidence;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerCoverageStatus;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerResultResponse;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerResultSection;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionMode;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceReference;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStartRequest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class UiExplorerAiResponseParser {

    private static final Set<String> TOP_LEVEL_FIELDS = Set.of(
            "screen", "scenarioDescription", "sourceRevision", "functionalOverview",
            "sections", "overallConfidence",
            "visibilityLimits", "unresolvedQuestions", "usage"
    );
    private static final Set<String> SCREEN_FIELDS = Set.of(
            "systemId", "screenId", "label", "routePattern", "navigationContext"
    );
    private static final Set<String> REVISION_FIELDS = Set.of("branch", "revision");
    private static final Set<String> SECTION_FIELDS = Set.of(
            "sectionId", "mode", "coverage", "confidence", "markdown",
            "sourceReferences", "visibilityLimits", "openQuestions"
    );
    private static final Set<String> SOURCE_REFERENCE_FIELDS = Set.of(
            "repository", "path", "symbol", "startLine", "endLine"
    );
    private final ObjectMapper objectMapper;

    public UiExplorerAiParseResult parse(
            String assistantContent,
            UiExplorerJobStartRequest request,
            UiExplorerSourceContextSnapshot context,
            Set<String> additionalSourcePaths
    ) {
        if (!StringUtils.hasText(assistantContent)) {
            return malformed(request, context, "AI response was empty.");
        }
        var root = readObject(assistantContent.trim());
        if (root == null) {
            return malformed(request, context, "AI response was not a valid JSON object.");
        }
        var shapeError = validateShape(root);
        if (shapeError != null) {
            return malformed(request, context, shapeError);
        }
        var identityError = validateIdentity(root, request, context);
        if (identityError != null) {
            return malformed(request, context, identityError);
        }
        if (root.has("usage") && !root.get("usage").isNull()) {
            return malformed(request, context, "AI response attempted to populate backend-owned usage.");
        }

        var allowedPaths = new LinkedHashSet<String>();
        context.sourceFiles().stream().map(file -> normalizePath(file.path())).forEach(allowedPaths::add);
        if (additionalSourcePaths != null) {
            additionalSourcePaths.stream().map(UiExplorerAiResponseParser::normalizePath)
                    .filter(StringUtils::hasText).forEach(allowedPaths::add);
        }

        var limitations = new ArrayList<String>();
        var sections = parseSections(root.get("sections"), request, allowedPaths, limitations);
        if (sections == null) {
            return malformed(request, context, "AI response contained an invalid section or source reference.");
        }
        sections = reconcileCoverage(
                sections,
                context,
                additionalSourcePaths != null && !additionalSourcePaths.isEmpty(),
                limitations
        );
        var functionalOverview = text(root, "functionalOverview");
        if (!StringUtils.hasText(functionalOverview)) {
            limitations.add("AI response omitted functionalOverview.");
            functionalOverview = "Functional overview is unavailable in the accepted partial AI response.";
        }
        var overallConfidence = enumValue(UiExplorerClaimConfidence.class, text(root, "overallConfidence"));
        if (overallConfidence == null) {
            limitations.add("AI response omitted a valid overallConfidence.");
            overallConfidence = UiExplorerClaimConfidence.UNKNOWN;
        }
        var visibilityLimits = strictTextList(root.get("visibilityLimits"));
        var unresolvedQuestions = strictTextList(root.get("unresolvedQuestions"));
        if (visibilityLimits == null || unresolvedQuestions == null) {
            return malformed(request, context, "AI response visibilityLimits and unresolvedQuestions must be arrays.");
        }
        if (!root.has("usage")) {
            limitations.add("AI response omitted backend-owned usage placeholder.");
        }

        var mergedLimits = new LinkedHashSet<String>(context.visibilityLimits());
        mergedLimits.addAll(visibilityLimits);
        mergedLimits.addAll(limitations);
        var result = new UiExplorerResultResponse(
                context.screen(),
                request.scenarioDescription(),
                context.sourceRevision(),
                functionalOverview,
                sections,
                overallConfidence,
                List.copyOf(mergedLimits),
                unresolvedQuestions,
                null
        );
        var partial = !limitations.isEmpty()
                || sections.stream().anyMatch(section -> section.coverage() != UiExplorerCoverageStatus.READY);
        return new UiExplorerAiParseResult(
                partial ? UiExplorerAiParseStatus.PARTIAL : UiExplorerAiParseStatus.COMPLETED,
                result,
                limitations
        );
    }

    public UiExplorerAiParseResult malformed(
            UiExplorerJobStartRequest request,
            UiExplorerSourceContextSnapshot context,
            String limitation
    ) {
        var safeLimitation = StringUtils.hasText(limitation)
                ? limitation.trim()
                : "AI response could not be safely accepted.";
        var sections = activeModes(request).entrySet().stream()
                .map(entry -> new UiExplorerResultSection(
                        entry.getKey(),
                        entry.getValue(),
                        UiExplorerCoverageStatus.BLOCKED,
                        UiExplorerClaimConfidence.UNKNOWN,
                        "AI response could not be safely accepted for this section.",
                        List.of(),
                        List.of(safeLimitation),
                        List.of("Repeat the analysis after correcting the AI response contract.")
                ))
                .toList();
        var limits = new LinkedHashSet<String>();
        if (context != null) {
            limits.addAll(context.visibilityLimits());
        }
        limits.add(safeLimitation);
        var result = new UiExplorerResultResponse(
                context != null ? context.screen() : null,
                request != null ? request.scenarioDescription() : null,
                context != null ? context.sourceRevision() : null,
                "AI result is unavailable because its response contract was not safely satisfied.",
                sections,
                UiExplorerClaimConfidence.UNKNOWN,
                List.copyOf(limits),
                List.of("A valid UI Explorer JSON response is required."),
                null
        );
        return new UiExplorerAiParseResult(UiExplorerAiParseStatus.MALFORMED, result, List.of(safeLimitation));
    }

    private List<UiExplorerResultSection> parseSections(
            JsonNode node,
            UiExplorerJobStartRequest request,
            Set<String> allowedPaths,
            List<String> limitations
    ) {
        var activeModes = activeModes(request);
        var parsed = new LinkedHashMap<UiExplorerSectionId, UiExplorerResultSection>();
        if (node != null && !node.isNull() && !node.isArray()) {
            return null;
        }
        if (node != null && node.isArray()) {
            for (var item : node) {
                try {
                    var section = objectMapper.treeToValue(item, UiExplorerResultSection.class);
                    if (section == null || section.sectionId() == null || section.mode() == null
                            || section.mode() == UiExplorerSectionMode.OFF || section.coverage() == null
                            || activeModes.get(section.sectionId()) != section.mode()
                            || parsed.putIfAbsent(section.sectionId(), section) != null
                            || !validSection(section, allowedPaths)) {
                        return null;
                    }
                } catch (JsonProcessingException | IllegalArgumentException exception) {
                    return null;
                }
            }
        }
        for (var entry : activeModes.entrySet()) {
            if (!parsed.containsKey(entry.getKey())) {
                limitations.add("AI response omitted active section " + entry.getKey().name() + ".");
                parsed.put(entry.getKey(), new UiExplorerResultSection(
                        entry.getKey(),
                        entry.getValue(),
                        UiExplorerCoverageStatus.BLOCKED,
                        UiExplorerClaimConfidence.UNKNOWN,
                        "The active section was omitted by AI and requires review.",
                        List.of(),
                        List.of("AI response omitted this active section."),
                        List.of("What evidence is required to complete this section?")
                ));
            }
        }
        return parsed.values().stream()
                .sorted(Comparator.comparingInt(section -> section.sectionId().ordinal()))
                .toList();
    }

    private boolean validSection(UiExplorerResultSection section, Set<String> allowedPaths) {
        return StringUtils.hasText(section.markdown())
                && section.confidence() != null
                && validReferences(section.sourceReferences(), allowedPaths)
                && (section.confidence() != UiExplorerClaimConfidence.CONFIRMED
                || !section.sourceReferences().isEmpty());
    }

    private List<UiExplorerResultSection> reconcileCoverage(
            List<UiExplorerResultSection> sections,
            UiExplorerSourceContextSnapshot context,
            boolean fallbackEvidenceCaptured,
            List<String> limitations
    ) {
        if (fallbackEvidenceCaptured) {
            return sections;
        }
        var deterministicCoverage = context.sectionCoverage().stream().collect(java.util.stream.Collectors.toMap(
                coverage -> coverage.sectionId(),
                coverage -> coverage.status(),
                (left, right) -> left,
                LinkedHashMap::new
        ));
        return sections.stream().map(section -> {
            var deterministic = deterministicCoverage.get(section.sectionId());
            if (section.coverage() != UiExplorerCoverageStatus.READY
                    || deterministic == null || deterministic == UiExplorerCoverageStatus.READY) {
                return section;
            }
            limitations.add("Section " + section.sectionId().name()
                    + " cannot be READY without captured fallback source evidence.");
            var sectionLimits = new LinkedHashSet<>(section.visibilityLimits());
            sectionLimits.add("Deterministic coverage remains " + deterministic.name()
                    + " and no targeted fallback source evidence was captured.");
            return new UiExplorerResultSection(
                    section.sectionId(),
                    section.mode(),
                    UiExplorerCoverageStatus.PARTIAL,
                    section.confidence(),
                    section.markdown(),
                    section.sourceReferences(),
                    List.copyOf(sectionLimits),
                    section.openQuestions()
            );
        }).toList();
    }

    private boolean validReferences(List<UiExplorerSourceReference> references, Set<String> allowedPaths) {
        for (var reference : references) {
            var path = reference != null ? normalizePath(reference.path()) : null;
            if (reference == null || StringUtils.hasText(reference.repository())
                    || !allowedPaths.contains(path)
                    || reference.startLine() != null && reference.startLine() < 1
                    || reference.endLine() != null && reference.endLine() < 1
                    || reference.startLine() != null && reference.endLine() != null
                    && reference.endLine() < reference.startLine()) {
                return false;
            }
        }
        return true;
    }

    private String validateShape(JsonNode root) {
        var unexpected = unexpectedField(root, TOP_LEVEL_FIELDS);
        if (unexpected != null) {
            return "AI response contract violation: unexpected top-level field " + unexpected + ".";
        }
        if (!objectWithFields(root.get("screen"), SCREEN_FIELDS)
                || !objectWithFields(root.get("sourceRevision"), REVISION_FIELDS)) {
            return "AI response screen or sourceRevision shape is invalid.";
        }
        var sections = root.get("sections");
        if (sections != null && !sections.isNull() && sections.isArray()) {
            for (var section : sections) {
                if (!objectWithFields(section, SECTION_FIELDS)) {
                    return "AI response section shape is invalid.";
                }
                if (!referenceArrayValidShape(section.get("sourceReferences"))) {
                    return "AI response section sourceReferences shape is invalid.";
                }
            }
        }
        return null;
    }

    private String validateIdentity(
            JsonNode root,
            UiExplorerJobStartRequest request,
            UiExplorerSourceContextSnapshot context
    ) {
        if (request == null || context == null || context.screen() == null || context.sourceRevision() == null) {
            return "Expected UI Explorer request context is unavailable.";
        }
        var screen = root.get("screen");
        if (!equalsText(screen, "systemId", context.screen().systemId())
                || !equalsText(screen, "screenId", context.screen().screenId())
                || !equalsText(screen, "label", context.screen().label())
                || !equalsText(screen, "routePattern", context.screen().routePattern())
                || !equalsNullableText(screen, "navigationContext", context.screen().navigationContext())) {
            return "AI response screen identity does not match the validated catalog screen.";
        }
        var revision = root.get("sourceRevision");
        if (!equalsText(revision, "branch", context.sourceRevision().branch())
                || !equalsText(revision, "revision", context.sourceRevision().revision())) {
            return "AI response sourceRevision does not match the validated source revision.";
        }
        if (!java.util.Objects.equals(request.scenarioDescription(), nullableText(root, "scenarioDescription"))) {
            return "AI response scenarioDescription does not match the request.";
        }
        return null;
    }

    private LinkedHashMap<UiExplorerSectionId, UiExplorerSectionMode> activeModes(UiExplorerJobStartRequest request) {
        var active = new LinkedHashMap<UiExplorerSectionId, UiExplorerSectionMode>();
        if (request != null) {
            request.resolvedSectionModes().stream()
                    .filter(assignment -> assignment.mode() != UiExplorerSectionMode.OFF)
                    .forEach(assignment -> active.put(assignment.sectionId(), assignment.mode()));
        }
        return active;
    }

    private JsonNode readObject(String content) {
        try {
            var node = objectMapper.readTree(content);
            return node != null && node.isObject() ? node : null;
        } catch (JsonProcessingException exception) {
            return null;
        }
    }

    private boolean objectWithFields(JsonNode node, Set<String> allowed) {
        return node != null && node.isObject() && unexpectedField(node, allowed) == null;
    }

    private boolean referenceArrayValidShape(JsonNode node) {
        if (node == null || node.isNull()) {
            return true;
        }
        if (!node.isArray()) {
            return false;
        }
        for (var reference : node) {
            if (!objectWithFields(reference, SOURCE_REFERENCE_FIELDS)) {
                return false;
            }
        }
        return true;
    }

    private String unexpectedField(JsonNode node, Set<String> allowed) {
        var names = node.fieldNames();
        while (names.hasNext()) {
            var name = names.next();
            if (!allowed.contains(name)) {
                return name;
            }
        }
        return null;
    }

    private List<String> strictTextList(JsonNode node) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            return null;
        }
        var values = new LinkedHashSet<String>();
        for (var item : node) {
            if (!item.isTextual()) {
                return null;
            }
            if (StringUtils.hasText(item.asText())) {
                values.add(item.asText().trim());
            }
        }
        return List.copyOf(values);
    }

    private static String text(JsonNode node, String field) {
        var value = node != null ? node.get(field) : null;
        return value != null && value.isTextual() && StringUtils.hasText(value.asText())
                ? value.asText().trim()
                : null;
    }

    private static String nullableText(JsonNode node, String field) {
        var value = node != null ? node.get(field) : null;
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private static boolean equalsText(JsonNode node, String field, String expected) {
        return java.util.Objects.equals(text(node, field), expected);
    }

    private static boolean equalsNullableText(JsonNode node, String field, String expected) {
        return java.util.Objects.equals(nullableText(node, field), expected);
    }

    private static String normalizePath(String value) {
        return StringUtils.hasText(value)
                ? value.trim().replace('\\', '/').replaceAll("^/+", "").replaceAll("/+$", "")
                : null;
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

}
