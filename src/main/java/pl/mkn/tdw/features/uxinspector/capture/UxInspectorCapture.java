package pl.mkn.tdw.features.uxinspector.capture;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonDeserialize(using = UxInspectorCaptureDeserializer.class)
public record UxInspectorCapture(
        String schema,
        int version,
        String captureId,
        Instant capturedAt,
        CaptureProfile captureProfile,
        Page page,
        Target target,
        List<Ancestor> ancestors,
        FormSnapshot formSnapshot,
        Traversal traversal,
        Signals signals,
        List<String> limits,
        Client client
) {
    public static final String SCHEMA = "tdw.ux-inspector-capture";
    public static final int VERSION = 3;
    public static final int MAX_BYTES = 128 * 1024;
    public static final int MAX_FORM_CONTROLS = 64;
    public static final int MAX_FORM_VALUE_LENGTH = 16 * 1024;
    public static final int MAX_FORM_VALUE_CHARACTERS = 64 * 1024;

    public UxInspectorCapture {
        ancestors = ancestors != null ? List.copyOf(ancestors) : List.of();
        limits = limits != null ? List.copyOf(limits) : List.of();
    }

    static UxInspectorCapture fromJson(JsonNode node) {
        requireObject(node, "capture");
        requireFields(node, "capture", Set.of("schema", "version", "captureId", "capturedAt", "captureProfile",
                "page", "target", "ancestors", "formSnapshot", "traversal", "signals", "limits", "client"));
        var ancestorsNode = requiredArray(node, "ancestors");
        var ancestors = new java.util.ArrayList<Ancestor>();
        for (var value : ancestorsNode) ancestors.add(ancestor(value));
        return new UxInspectorCapture(
                text(node, "schema"), integer(node, "version"), text(node, "captureId"), instant(node, "capturedAt"),
                captureProfile(node), page(requiredObject(node, "page")), target(requiredObject(node, "target")), ancestors,
                nullableFormSnapshot(node.get("formSnapshot")),
                traversal(requiredObject(node, "traversal")), signals(requiredObject(node, "signals")),
                strings(requiredArray(node, "limits"), "limits"), client(requiredObject(node, "client"))
        );
    }

    private static Page page(JsonNode node) {
        requireFields(node, "page", Set.of("origin", "path", "title", "language", "queryParameterNames"));
        return new Page(text(node, "origin"), text(node, "path"), nullableText(node, "title"),
                nullableText(node, "language"), strings(requiredArray(node, "queryParameterNames"), "queryParameterNames"));
    }

    private static Target target(JsonNode node) {
        requireFields(node, "target", Set.of("tag", "role", "accessibleName", "text", "domFingerprint", "state", "bounds"));
        return new Target(text(node, "tag"), nullableText(node, "role"), nullableText(node, "accessibleName"),
                nullableText(node, "text"), domFingerprint(requiredObject(node, "domFingerprint")),
                state(requiredObject(node, "state")), bounds(requiredObject(node, "bounds")));
    }

    private static DomFingerprint domFingerprint(JsonNode node) {
        requireFields(node, "domFingerprint", Set.of("stableAttributes", "selectorCandidates",
                "componentBoundaryTags", "labelFor"));
        return new DomFingerprint(stringMap(requiredObject(node, "stableAttributes"), "stableAttributes"),
                strings(requiredArray(node, "selectorCandidates"), "selectorCandidates"),
                strings(requiredArray(node, "componentBoundaryTags"), "componentBoundaryTags"),
                nullableText(node, "labelFor"));
    }

    private static Ancestor ancestor(JsonNode node) {
        requireObject(node, "ancestor");
        requireFields(node, "ancestor", Set.of("depth", "tag", "role", "accessibleName", "text", "stableAttributes"));
        return new Ancestor(integer(node, "depth"), text(node, "tag"), nullableText(node, "role"),
                nullableText(node, "accessibleName"), nullableText(node, "text"),
                stringMap(requiredObject(node, "stableAttributes"), "stableAttributes"));
    }

    private static TargetState state(JsonNode node) {
        requireFields(node, "state", Set.of("disabled", "ariaDisabled", "readOnly", "required", "invalid", "checked",
                "expanded", "hidden"));
        return new TargetState(bool(node, "disabled"), bool(node, "ariaDisabled"), bool(node, "readOnly"),
                bool(node, "required"), bool(node, "invalid"), nullableBool(node, "checked"),
                nullableBool(node, "expanded"), bool(node, "hidden"));
    }

    private static Bounds bounds(JsonNode node) {
        requireFields(node, "bounds", Set.of("x", "y", "width", "height"));
        return new Bounds(number(node, "x"), number(node, "y"), number(node, "width"), number(node, "height"));
    }

    private static CaptureProfile captureProfile(JsonNode node) {
        try {
            return CaptureProfile.valueOf(text(node, "captureProfile"));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("captureProfile is invalid");
        }
    }

    private static FormSnapshot nullableFormSnapshot(JsonNode node) {
        if (node == null || node.isNull()) return null;
        requireObject(node, "formSnapshot");
        requireFields(node, "formSnapshot", Set.of("source", "stableAttributes", "selectorCandidates", "valid",
                "observedControlCount", "emittedControlCount", "omittedControlCount", "controls", "submitters",
                "excludedControls", "valueCharacters", "valuesTruncated"));
        var controls = new java.util.ArrayList<FormControl>();
        for (var value : requiredArray(node, "controls")) controls.add(formControl(value));
        var submitters = new java.util.ArrayList<FormSubmitter>();
        for (var value : requiredArray(node, "submitters")) submitters.add(formSubmitter(value));
        var excluded = new java.util.ArrayList<ExcludedFormControl>();
        for (var value : requiredArray(node, "excludedControls")) excluded.add(excludedFormControl(value));
        return new FormSnapshot(text(node, "source"), stringMap(requiredObject(node, "stableAttributes"), "stableAttributes"),
                strings(requiredArray(node, "selectorCandidates"), "selectorCandidates"), nullableBool(node, "valid"),
                integer(node, "observedControlCount"), integer(node, "emittedControlCount"),
                integer(node, "omittedControlCount"), controls, submitters, excluded,
                integer(node, "valueCharacters"), bool(node, "valuesTruncated"));
    }

    private static FormControl formControl(JsonNode node) {
        requireObject(node, "formControl");
        requireFields(node, "formControl", Set.of("selectedTarget", "tag", "type", "name", "formControlName",
                "accessibleName", "stableAttributes", "value", "valueTruncated", "checked", "selectedValues",
                "selectedLabels", "disabled", "readOnly", "required", "validity"));
        return new FormControl(bool(node, "selectedTarget"), text(node, "tag"), nullableText(node, "type"),
                nullableText(node, "name"), nullableText(node, "formControlName"), nullableText(node, "accessibleName"),
                stringMap(requiredObject(node, "stableAttributes"), "stableAttributes"), nullableText(node, "value"),
                bool(node, "valueTruncated"), nullableBool(node, "checked"),
                strings(requiredArray(node, "selectedValues"), "selectedValues"),
                strings(requiredArray(node, "selectedLabels"), "selectedLabels"), bool(node, "disabled"),
                bool(node, "readOnly"), bool(node, "required"), nullableValidity(node.get("validity")));
    }

    private static Validity nullableValidity(JsonNode node) {
        if (node == null || node.isNull()) return null;
        requireObject(node, "validity");
        requireFields(node, "validity", Set.of("valid", "valueMissing", "typeMismatch", "patternMismatch",
                "tooShort", "tooLong", "rangeUnderflow", "rangeOverflow", "stepMismatch", "badInput",
                "customError", "validationMessage"));
        return new Validity(bool(node, "valid"), bool(node, "valueMissing"), bool(node, "typeMismatch"),
                bool(node, "patternMismatch"), bool(node, "tooShort"), bool(node, "tooLong"),
                bool(node, "rangeUnderflow"), bool(node, "rangeOverflow"), bool(node, "stepMismatch"),
                bool(node, "badInput"), bool(node, "customError"), nullableText(node, "validationMessage"));
    }

    private static FormSubmitter formSubmitter(JsonNode node) {
        requireObject(node, "formSubmitter");
        requireFields(node, "formSubmitter", Set.of("selectedTarget", "tag", "type", "accessibleName",
                "stableAttributes", "disabled"));
        return new FormSubmitter(bool(node, "selectedTarget"), text(node, "tag"), nullableText(node, "type"),
                nullableText(node, "accessibleName"),
                stringMap(requiredObject(node, "stableAttributes"), "stableAttributes"), bool(node, "disabled"));
    }

    private static ExcludedFormControl excludedFormControl(JsonNode node) {
        requireObject(node, "excludedFormControl");
        requireFields(node, "excludedFormControl", Set.of("tag", "type", "name", "formControlName", "reason"));
        return new ExcludedFormControl(text(node, "tag"), nullableText(node, "type"), nullableText(node, "name"),
                nullableText(node, "formControlName"), text(node, "reason"));
    }

    private static Traversal traversal(JsonNode node) {
        requireFields(node, "traversal", Set.of("observedDepth", "emittedNodeCount", "omittedNodeCount", "reachedDocumentRoot"));
        return new Traversal(integer(node, "observedDepth"), integer(node, "emittedNodeCount"),
                integer(node, "omittedNodeCount"), bool(node, "reachedDocumentRoot"));
    }

    private static Signals signals(JsonNode node) {
        requireFields(node, "signals", Set.of("shadowBoundaryCount", "frame", "redactions"));
        return new Signals(integer(node, "shadowBoundaryCount"), text(node, "frame"),
                strings(requiredArray(node, "redactions"), "redactions"));
    }

    private static Client client(JsonNode node) {
        requireFields(node, "client", Set.of("name", "version", "featureId"));
        return new Client(text(node, "name"), text(node, "version"), text(node, "featureId"));
    }

    private static void requireFields(JsonNode node, String owner, Set<String> allowed) {
        var names = new LinkedHashSet<String>();
        node.fieldNames().forEachRemaining(names::add);
        var unknown = new LinkedHashSet<>(names);
        unknown.removeAll(allowed);
        if (!unknown.isEmpty()) throw new IllegalArgumentException("Unknown " + owner + " field(s): " + unknown);
        var missing = new LinkedHashSet<>(allowed);
        missing.removeAll(names);
        if (!missing.isEmpty()) throw new IllegalArgumentException("Missing " + owner + " field(s): " + missing);
    }

    private static JsonNode requiredObject(JsonNode node, String field) {
        var value = node.get(field);
        requireObject(value, field);
        return value;
    }

    private static void requireObject(JsonNode node, String field) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException(field + " must be an object");
    }

    private static JsonNode requiredArray(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isArray()) throw new IllegalArgumentException(field + " must be an array");
        return value;
    }

    private static String text(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isTextual()) throw new IllegalArgumentException(field + " must be a string");
        return value.textValue();
    }

    private static String nullableText(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isTextual()) throw new IllegalArgumentException(field + " must be a string or null");
        return value.textValue();
    }

    private static int integer(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isIntegralNumber()) throw new IllegalArgumentException(field + " must be an integer");
        return value.intValue();
    }

    private static double number(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isNumber()) throw new IllegalArgumentException(field + " must be a number");
        return value.doubleValue();
    }

    private static boolean bool(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || !value.isBoolean()) throw new IllegalArgumentException(field + " must be a boolean");
        return value.booleanValue();
    }

    private static Boolean nullableBool(JsonNode node, String field) {
        var value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isBoolean()) throw new IllegalArgumentException(field + " must be a boolean or null");
        return value.booleanValue();
    }

    private static Instant instant(JsonNode node, String field) {
        try { return Instant.parse(text(node, field)); }
        catch (RuntimeException exception) { throw new IllegalArgumentException(field + " must be an ISO-8601 instant"); }
    }

    private static List<String> strings(JsonNode node, String field) {
        var result = new java.util.ArrayList<String>();
        for (var value : node) {
            if (!value.isTextual()) throw new IllegalArgumentException(field + " must contain only strings");
            result.add(value.textValue());
        }
        return List.copyOf(result);
    }

    private static Map<String, String> stringMap(JsonNode node, String field) {
        var result = new LinkedHashMap<String, String>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (!entry.getValue().isTextual()) throw new IllegalArgumentException(field + " values must be strings");
            result.put(entry.getKey(), entry.getValue().textValue());
        }
        return Map.copyOf(result);
    }

    public record Page(String origin, String path, String title, String language, List<String> queryParameterNames) {
        public Page { queryParameterNames = queryParameterNames != null ? List.copyOf(queryParameterNames) : List.of(); }
    }
    public record Target(String tag, String role, String accessibleName, String text,
                         DomFingerprint domFingerprint, TargetState state, Bounds bounds) {}
    public record DomFingerprint(Map<String, String> stableAttributes, List<String> selectorCandidates,
                                 List<String> componentBoundaryTags, String labelFor) {
        public DomFingerprint {
            stableAttributes = stableAttributes != null ? Map.copyOf(stableAttributes) : Map.of();
            selectorCandidates = selectorCandidates != null ? List.copyOf(selectorCandidates) : List.of();
            componentBoundaryTags = componentBoundaryTags != null ? List.copyOf(componentBoundaryTags) : List.of();
        }
    }
    public record Ancestor(int depth, String tag, String role, String accessibleName, String text,
                           Map<String, String> stableAttributes) {
        public Ancestor { stableAttributes = stableAttributes != null ? Map.copyOf(stableAttributes) : Map.of(); }
    }
    public record TargetState(boolean disabled, boolean ariaDisabled, boolean readOnly, boolean required,
                              boolean invalid, Boolean checked, Boolean expanded, boolean hidden) {}
    public record Bounds(double x, double y, double width, double height) {}
    public enum CaptureProfile { ELEMENT_CONTEXT, FORM_DIAGNOSTICS }
    public record FormSnapshot(String source, Map<String, String> stableAttributes, List<String> selectorCandidates,
                               Boolean valid, int observedControlCount, int emittedControlCount,
                               int omittedControlCount, List<FormControl> controls, List<FormSubmitter> submitters,
                               List<ExcludedFormControl> excludedControls, int valueCharacters,
                               boolean valuesTruncated) {
        public FormSnapshot {
            stableAttributes = stableAttributes != null ? Map.copyOf(stableAttributes) : Map.of();
            selectorCandidates = selectorCandidates != null ? List.copyOf(selectorCandidates) : List.of();
            controls = controls != null ? List.copyOf(controls) : List.of();
            submitters = submitters != null ? List.copyOf(submitters) : List.of();
            excludedControls = excludedControls != null ? List.copyOf(excludedControls) : List.of();
        }
    }
    public record FormControl(boolean selectedTarget, String tag, String type, String name, String formControlName,
                              String accessibleName, Map<String, String> stableAttributes, String value,
                              boolean valueTruncated, Boolean checked, List<String> selectedValues,
                              List<String> selectedLabels, boolean disabled, boolean readOnly, boolean required,
                              Validity validity) {
        public FormControl {
            stableAttributes = stableAttributes != null ? Map.copyOf(stableAttributes) : Map.of();
            selectedValues = selectedValues != null ? List.copyOf(selectedValues) : List.of();
            selectedLabels = selectedLabels != null ? List.copyOf(selectedLabels) : List.of();
        }
    }
    public record Validity(boolean valid, boolean valueMissing, boolean typeMismatch, boolean patternMismatch,
                           boolean tooShort, boolean tooLong, boolean rangeUnderflow, boolean rangeOverflow,
                           boolean stepMismatch, boolean badInput, boolean customError, String validationMessage) {}
    public record FormSubmitter(boolean selectedTarget, String tag, String type, String accessibleName,
                                Map<String, String> stableAttributes, boolean disabled) {
        public FormSubmitter {
            stableAttributes = stableAttributes != null ? Map.copyOf(stableAttributes) : Map.of();
        }
    }
    public record ExcludedFormControl(String tag, String type, String name, String formControlName, String reason) {}
    public record Traversal(int observedDepth, int emittedNodeCount, int omittedNodeCount, boolean reachedDocumentRoot) {}
    public record Signals(int shadowBoundaryCount, String frame, List<String> redactions) {
        public Signals { redactions = redactions != null ? List.copyOf(redactions) : List.of(); }
    }
    public record Client(String name, String version, String featureId) {}
}
