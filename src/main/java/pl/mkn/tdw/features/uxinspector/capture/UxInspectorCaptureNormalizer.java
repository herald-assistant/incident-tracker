package pl.mkn.tdw.features.uxinspector.capture;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class UxInspectorCaptureNormalizer {
    private static final Set<String> ATTRIBUTES = Set.of("id", "name", "type", "data-testid", "data-test",
            "data-cy", "formcontrolname", "aria-label", "aria-describedby");
    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z][A-Za-z0-9_.:-]*$");
    private static final Pattern SENSITIVE = Pattern.compile("(authorization|bearer|cookie|csrf|jwt|pass(word|wd)?|secret|session|token|one[-_ ]?time|otp|cvv|cvc)", Pattern.CASE_INSENSITIVE);
    private static final Pattern JWT = Pattern.compile("^[A-Za-z0-9_-]{16,}\\.[A-Za-z0-9_-]{16,}\\.[A-Za-z0-9_-]{16,}$");
    private static final Pattern SELECTOR = Pattern.compile("^(#[A-Za-z][A-Za-z0-9_.:-]*|[a-z][a-z0-9-]{0,39}\\[(data-testid|data-test|data-cy|formcontrolname|name|aria-label)=\"[A-Za-z][A-Za-z0-9_.:-]*\"\\]|[a-z][a-z0-9-]{0,39}\\[class~=\"[A-Za-z][A-Za-z0-9_.:-]*\"\\])$");
    private static final Set<String> FORM_SOURCES = Set.of("NEAREST_FORM", "SELECTED_CONTROL_ONLY");
    private static final Set<String> EXCLUSION_REASONS = Set.of("SENSITIVE_TYPE", "FILE_CONTROL",
            "SENSITIVE_AUTOCOMPLETE", "SENSITIVE_NAME", "SENSITIVE_VALUE");
    private static final Pattern EMAIL = Pattern.compile("\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern UUID = Pattern.compile("\\b[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LONG_NUMBER = Pattern.compile("\\b\\d{6,}\\b");
    private final ObjectMapper objectMapper;

    public UxInspectorCapture normalize(UxInspectorCapture capture) {
        if (capture == null) throw new IllegalArgumentException("capture is required");
        require(UxInspectorCapture.SCHEMA.equals(capture.schema()), "Unsupported UX Inspector capture schema");
        require(capture.version() == UxInspectorCapture.VERSION, "Unsupported UX Inspector capture version");
        require(matches(capture.captureId(), "^[A-Za-z0-9_-]{8,96}$"), "Invalid captureId");
        require(capture.capturedAt() != null, "capturedAt is required");
        require(capture.captureProfile() != null, "captureProfile is required");
        require(capture.client() != null && "TDW UX Inspector".equals(capture.client().name())
                && "ux-inspector".equals(capture.client().featureId()), "Unsupported UX Inspector client");
        var page = normalizePage(capture.page());
        var redactions = new LinkedHashSet<String>();
        if (capture.signals() != null) redactions.addAll(limitCodes(capture.signals().redactions(), 24));
        var target = normalizeTarget(capture.target(), redactions);
        var ancestors = capture.ancestors().stream().limit(24).map(value -> normalizeAncestor(value, redactions)).toList();
        var limits = new LinkedHashSet<>(limitCodes(capture.limits(), 32));
        var formSnapshot = normalizeFormSnapshot(capture.captureProfile(), capture.formSnapshot(), redactions, limits);
        var traversal = normalizeTraversal(capture.traversal(), ancestors.size());
        var signals = normalizeSignals(capture.signals(), redactions);
        if (capture.ancestors().size() > 24) limits.add("ANCESTORS_TRUNCATED");
        var normalized = new UxInspectorCapture(UxInspectorCapture.SCHEMA, UxInspectorCapture.VERSION,
                capture.captureId(), capture.capturedAt(), capture.captureProfile(), page, target, ancestors,
                formSnapshot, traversal, signals,
                List.copyOf(limits), new UxInspectorCapture.Client("TDW UX Inspector",
                bounded(capture.client().version(), 40, "unknown"), "ux-inspector"));
        require(serializedBytes(normalized) <= UxInspectorCapture.MAX_BYTES,
                "UX Inspector capture exceeds the 128 KiB post-normalization limit");
        return normalized;
    }

    private UxInspectorCapture.Page normalizePage(UxInspectorCapture.Page page) {
        require(page != null, "page is required");
        require(StringUtils.hasText(page.origin()) && page.origin().trim().length() <= 2048,
                "page.origin must be an HTTP(S) origin within the supported limit");
        var origin = page.origin().trim();
        try {
            var uri = URI.create(origin);
            require(("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null
                    && uri.getRawQuery() == null && uri.getRawFragment() == null && (uri.getPath() == null || uri.getPath().isEmpty()),
                    "page.origin must be an HTTP(S) origin");
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("page.origin must be an HTTP(S) origin");
        }
        var queryNames = page.queryParameterNames().stream().filter(this::safeIdentifier).distinct().limit(16).toList();
        return new UxInspectorCapture.Page(origin, sanitizePath(page.path()),
                sanitizeText(page.title(), 180, null), bounded(page.language(), 20, null), queryNames);
    }

    private UxInspectorCapture.Target normalizeTarget(UxInspectorCapture.Target target, Set<String> redactions) {
        require(target != null && matches(target.tag(), "^[A-Za-z][A-Za-z0-9-]{0,39}$"), "target.tag is invalid");
        require(target.domFingerprint() != null, "target.domFingerprint is required");
        return new UxInspectorCapture.Target(target.tag().toLowerCase(Locale.ROOT), sanitizeText(target.role(), 40, redactions),
                sanitizeText(target.accessibleName(), 140, redactions), sanitizeText(target.text(), 180, redactions),
                normalizeFingerprint(target.domFingerprint()), target.state() != null ? target.state() : emptyState(),
                normalizeBounds(target.bounds()));
    }

    private UxInspectorCapture.DomFingerprint normalizeFingerprint(UxInspectorCapture.DomFingerprint value) {
        var selectors = value.selectorCandidates().stream().filter(this::safeSelector).distinct().limit(8).toList();
        require(selectors.size() == value.selectorCandidates().size(), "domFingerprint.selectorCandidates are invalid");
        var boundaries = value.componentBoundaryTags().stream()
                .map(tag -> tag != null ? tag.toLowerCase(Locale.ROOT) : null)
                .filter(tag -> tag != null && tag.contains("-") && tag.matches("^[a-z][a-z0-9-]{0,39}$"))
                .distinct().limit(8).toList();
        require(boundaries.size() == value.componentBoundaryTags().size(), "domFingerprint.componentBoundaryTags are invalid");
        var labelFor = nullableIdentifier(value.labelFor());
        require(value.labelFor() == null || labelFor != null, "domFingerprint.labelFor is invalid");
        return new UxInspectorCapture.DomFingerprint(attributes(value.stableAttributes()), selectors, boundaries, labelFor);
    }

    private UxInspectorCapture.FormSnapshot normalizeFormSnapshot(
            UxInspectorCapture.CaptureProfile profile,
            UxInspectorCapture.FormSnapshot value,
            Set<String> redactions,
            Set<String> limits
    ) {
        if (profile == UxInspectorCapture.CaptureProfile.ELEMENT_CONTEXT) {
            require(value == null, "ELEMENT_CONTEXT must not contain formSnapshot");
            return null;
        }
        if (value == null) return null;
        require(FORM_SOURCES.contains(value.source()), "formSnapshot.source is invalid");
        require(value.controls().size() <= UxInspectorCapture.MAX_FORM_CONTROLS,
                "formSnapshot.controls exceeds the supported limit");
        require(value.submitters().size() <= 16 && value.excludedControls().size() <= 32,
                "formSnapshot metadata exceeds the supported limit");
        var selectors = value.selectorCandidates().stream().filter(this::safeSelector).distinct().limit(8).toList();
        require(selectors.size() == value.selectorCandidates().size(), "formSnapshot.selectorCandidates are invalid");
        var controls = new ArrayList<UxInspectorCapture.FormControl>();
        var valueCharacters = 0;
        var backendTruncated = false;
        for (var control : value.controls()) {
            var normalized = normalizeFormControl(control,
                    Math.max(0, UxInspectorCapture.MAX_FORM_VALUE_CHARACTERS - valueCharacters), redactions);
            valueCharacters += formValueCharacters(normalized);
            backendTruncated |= normalized.valueTruncated();
            controls.add(normalized);
        }
        require(valueCharacters <= UxInspectorCapture.MAX_FORM_VALUE_CHARACTERS,
                "formSnapshot values exceed the supported budget");
        require(value.emittedControlCount() == value.controls().size(),
                "formSnapshot.emittedControlCount is inconsistent");
        require(value.observedControlCount() >= value.emittedControlCount()
                        && value.omittedControlCount() >= 0 && value.observedControlCount() <= 4096,
                "formSnapshot control counts are invalid");
        var submitters = value.submitters().stream().map(item -> normalizeSubmitter(item, redactions)).toList();
        var excluded = value.excludedControls().stream().map(this::normalizeExcludedControl).toList();
        if (!excluded.isEmpty()) redactions.add("BACKEND_SENSITIVE_FORM_CONTROLS_EXCLUDED");
        var truncated = value.valuesTruncated() || backendTruncated || value.omittedControlCount() > 0;
        if (truncated) limits.add("FORM_VALUES_TRUNCATED");
        return new UxInspectorCapture.FormSnapshot(value.source(), attributes(value.stableAttributes()), selectors,
                value.valid(), value.observedControlCount(), controls.size(), value.omittedControlCount(), controls,
                submitters, excluded, valueCharacters, truncated);
    }

    private UxInspectorCapture.FormControl normalizeFormControl(
            UxInspectorCapture.FormControl value,
            int remainingCharacters,
            Set<String> redactions
    ) {
        require(value != null && matches(value.tag(), "^[A-Za-z][A-Za-z0-9-]{0,39}$"),
                "form control tag is invalid");
        var type = bounded(value.type(), 40, null);
        var name = nullableIdentifier(value.name());
        var formControlName = nullableIdentifier(value.formControlName());
        require(value.name() == null || name != null, "form control name is invalid");
        require(value.formControlName() == null || formControlName != null, "formControlName is invalid");
        require(!isSensitiveControl(type, name, formControlName), "sensitive form control entered controls payload");
        var selectedValues = normalizeFormValues(value.selectedValues(), remainingCharacters);
        remainingCharacters -= selectedValues.stream().mapToInt(String::length).sum();
        var selectedLabels = normalizeFormValues(value.selectedLabels(), Math.max(0, remainingCharacters));
        remainingCharacters -= selectedLabels.stream().mapToInt(String::length).sum();
        var normalizedValue = normalizeFormValue(value.value(), Math.max(0, remainingCharacters));
        require(!looksSensitiveValue(normalizedValue), "sensitive form value entered capture");
        var truncated = value.valueTruncated()
                || value.value() != null && normalizedValue.length() < normalizeRaw(value.value()).length()
                || rawCharacters(value.selectedValues()) > selectedValues.stream().mapToInt(String::length).sum()
                || rawCharacters(value.selectedLabels()) > selectedLabels.stream().mapToInt(String::length).sum();
        var validity = normalizeValidity(value.validity(), redactions);
        return new UxInspectorCapture.FormControl(value.selectedTarget(), value.tag().toLowerCase(Locale.ROOT),
                type != null ? type.toLowerCase(Locale.ROOT) : null, name, formControlName,
                sanitizeText(value.accessibleName(), 140, redactions), attributes(value.stableAttributes()),
                normalizedValue, truncated, value.checked(), selectedValues, selectedLabels,
                value.disabled(), value.readOnly(), value.required(), validity);
    }

    private UxInspectorCapture.Validity normalizeValidity(UxInspectorCapture.Validity value, Set<String> redactions) {
        if (value == null) return null;
        return new UxInspectorCapture.Validity(value.valid(), value.valueMissing(), value.typeMismatch(),
                value.patternMismatch(), value.tooShort(), value.tooLong(), value.rangeUnderflow(),
                value.rangeOverflow(), value.stepMismatch(), value.badInput(), value.customError(),
                sanitizeText(value.validationMessage(), 300, redactions));
    }

    private UxInspectorCapture.FormSubmitter normalizeSubmitter(
            UxInspectorCapture.FormSubmitter value,
            Set<String> redactions
    ) {
        require(value != null && matches(value.tag(), "^[A-Za-z][A-Za-z0-9-]{0,39}$"),
                "form submitter tag is invalid");
        return new UxInspectorCapture.FormSubmitter(value.selectedTarget(), value.tag().toLowerCase(Locale.ROOT),
                bounded(value.type(), 40, null), sanitizeText(value.accessibleName(), 140, redactions),
                attributes(value.stableAttributes()), value.disabled());
    }

    private UxInspectorCapture.ExcludedFormControl normalizeExcludedControl(UxInspectorCapture.ExcludedFormControl value) {
        require(value != null && matches(value.tag(), "^[A-Za-z][A-Za-z0-9-]{0,39}$")
                        && EXCLUSION_REASONS.contains(value.reason()), "excluded form control is invalid");
        return new UxInspectorCapture.ExcludedFormControl(value.tag().toLowerCase(Locale.ROOT),
                bounded(value.type(), 40, null), safeIdentifier(value.name()) ? value.name().trim() : null,
                safeIdentifier(value.formControlName()) ? value.formControlName().trim() : null, value.reason());
    }

    private List<String> normalizeFormValues(List<String> values, int remainingCharacters) {
        require(values == null || values.size() <= 64, "form selected values exceed the supported limit");
        var result = new ArrayList<String>();
        var remaining = Math.max(0, remainingCharacters);
        for (var value : values != null ? values : List.<String>of()) {
            require(value != null, "form selected values cannot contain null");
            var normalized = normalizeFormValue(value, remaining);
            require(!looksSensitiveValue(normalized), "sensitive selected value entered capture");
            result.add(normalized);
            remaining -= normalized.length();
        }
        return List.copyOf(result);
    }

    private String normalizeFormValue(String value, int remainingCharacters) {
        if (value == null) return null;
        var normalized = normalizeRaw(value);
        var max = Math.min(UxInspectorCapture.MAX_FORM_VALUE_LENGTH, Math.max(0, remainingCharacters));
        return normalized.length() <= max ? normalized : normalized.substring(0, max);
    }

    private String normalizeRaw(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFC).replace("\r\n", "\n");
    }

    private int formValueCharacters(UxInspectorCapture.FormControl value) {
        return (value.value() != null ? value.value().length() : 0)
                + value.selectedValues().stream().mapToInt(String::length).sum()
                + value.selectedLabels().stream().mapToInt(String::length).sum();
    }

    private int rawCharacters(List<String> values) {
        return (values != null ? values : List.<String>of()).stream()
                .mapToInt(value -> value != null ? normalizeRaw(value).length() : 0).sum();
    }

    private boolean isSensitiveControl(String type, String name, String formControlName) {
        return Set.of("password", "file").contains(type != null ? type.toLowerCase(Locale.ROOT) : "")
                || SENSITIVE.matcher(String.join(" ", nullToEmpty(name), nullToEmpty(formControlName))).find();
    }

    private boolean looksSensitiveValue(String value) {
        if (!StringUtils.hasText(value)) return false;
        var trimmed = value.trim();
        return trimmed.regionMatches(true, 0, "Bearer ", 0, 7) || JWT.matcher(trimmed).matches();
    }

    private UxInspectorCapture.Ancestor normalizeAncestor(UxInspectorCapture.Ancestor value, Set<String> redactions) {
        require(value != null && value.depth() > 0 && value.depth() <= 4096, "ancestor.depth is invalid");
        require(matches(value.tag(), "^[A-Za-z][A-Za-z0-9-]{0,39}$"), "ancestor.tag is invalid");
        return new UxInspectorCapture.Ancestor(value.depth(), value.tag().toLowerCase(Locale.ROOT),
                sanitizeText(value.role(), 40, redactions), sanitizeText(value.accessibleName(), 140, redactions),
                sanitizeText(value.text(), 180, redactions), attributes(value.stableAttributes()));
    }

    private UxInspectorCapture.Bounds normalizeBounds(UxInspectorCapture.Bounds bounds) {
        if (bounds == null) return new UxInspectorCapture.Bounds(0, 0, 0, 0);
        return new UxInspectorCapture.Bounds(safeNumber(bounds.x()), safeNumber(bounds.y()),
                Math.max(0, safeNumber(bounds.width())), Math.max(0, safeNumber(bounds.height())));
    }

    private UxInspectorCapture.Traversal normalizeTraversal(UxInspectorCapture.Traversal value, int emittedAncestors) {
        require(value != null, "traversal is required");
        var depth = boundedInt(value.observedDepth(), 1, 4096);
        var emitted = boundedInt(value.emittedNodeCount(), 1, 25);
        require(emitted == emittedAncestors + 1, "traversal.emittedNodeCount is inconsistent");
        return new UxInspectorCapture.Traversal(depth, emitted, boundedInt(value.omittedNodeCount(), 0, 4096),
                value.reachedDocumentRoot());
    }

    private UxInspectorCapture.Signals normalizeSignals(UxInspectorCapture.Signals value, Set<String> redactions) {
        require(value != null, "signals is required");
        require(Set.of("TOP_LEVEL", "SAME_ORIGIN_FRAME").contains(value.frame()), "signals.frame is invalid");
        return new UxInspectorCapture.Signals(boundedInt(value.shadowBoundaryCount(), 0, 64), value.frame(), List.copyOf(redactions));
    }

    private Map<String, String> attributes(Map<String, String> values) {
        var result = new LinkedHashMap<String, String>();
        if (values != null) values.forEach((name, value) -> {
            var key = name != null ? name.toLowerCase(Locale.ROOT) : "";
            if (ATTRIBUTES.contains(key) && safeIdentifier(value)) result.put(key, value.trim());
        });
        return Map.copyOf(result);
    }

    private String sanitizeText(String value, int max, Set<String> redactions) {
        if (!StringUtils.hasText(value)) return null;
        var normalized = Normalizer.normalize(value, Normalizer.Form.NFC).replaceAll("\\s+", " ").trim();
        var redacted = EMAIL.matcher(normalized).replaceAll("[EMAIL]");
        redacted = UUID.matcher(redacted).replaceAll("[ID]");
        redacted = LONG_NUMBER.matcher(redacted).replaceAll("[NUMBER]");
        if (!redacted.equals(normalized) && redactions != null) redactions.add("BACKEND_TEXT_REDACTED");
        if (SENSITIVE.matcher(redacted).find()) {
            if (redactions != null) redactions.add("BACKEND_SENSITIVE_TEXT_REMOVED");
            return null;
        }
        return redacted.length() <= max ? redacted : redacted.substring(0, max);
    }

    private String sanitizePath(String value) {
        var path = StringUtils.hasText(value) ? value.trim() : "/";
        require(path.startsWith("/") && !path.contains("?") && !path.contains("#"), "page.path is invalid");
        return path.length() <= 500 ? path : path.substring(0, 500);
    }

    private List<String> limitCodes(List<String> values, int max) {
        return (values != null ? values : List.<String>of()).stream().filter(this::safeCode).distinct().limit(max).toList();
    }

    private boolean safeSelector(String value) {
        return value != null && value.length() <= 240 && SELECTOR.matcher(value).matches();
    }
    private String nullableIdentifier(String value) {
        return safeIdentifier(value) ? value.trim() : null;
    }
    private boolean safeCode(String value) { return value != null && value.matches("^[A-Z][A-Z0-9_]{1,79}$"); }
    private boolean safeIdentifier(String value) { return value != null && value.length() <= 100
            && SAFE_IDENTIFIER.matcher(value.trim()).matches() && !SENSITIVE.matcher(value).find(); }
    private boolean matches(String value, String regex) { return value != null && value.matches(regex); }
    private String bounded(String value, int max, String fallback) {
        return StringUtils.hasText(value) && value.trim().length() <= max ? value.trim() : fallback;
    }
    private int boundedInt(int value, int min, int max) {
        require(value >= min && value <= max, "capture numeric limit exceeded"); return value;
    }
    private double safeNumber(double value) { return Double.isFinite(value) ? Math.max(-1_000_000, Math.min(1_000_000, value)) : 0; }
    private UxInspectorCapture.TargetState emptyState() {
        return new UxInspectorCapture.TargetState(false, false, false, false, false, null, null, false);
    }
    private int serializedBytes(UxInspectorCapture value) {
        try { return objectMapper.writeValueAsString(value).getBytes(StandardCharsets.UTF_8).length; }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Capture cannot be serialized", exception); }
    }
    private String nullToEmpty(String value) { return value != null ? value : ""; }
    private void require(boolean condition, String message) { if (!condition) throw new IllegalArgumentException(message); }
}
