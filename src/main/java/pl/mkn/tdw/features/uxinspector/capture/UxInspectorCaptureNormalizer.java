package pl.mkn.tdw.features.uxinspector.capture;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
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
    private static final Pattern SELECTOR = Pattern.compile("^(#[A-Za-z][A-Za-z0-9_.:-]*|[a-z][a-z0-9-]{0,39}\\[(data-testid|data-test|data-cy|formcontrolname|name|aria-label)=\"[A-Za-z][A-Za-z0-9_.:-]*\"\\]|[a-z][a-z0-9-]{0,39}\\[class~=\"[A-Za-z][A-Za-z0-9_.:-]*\"\\])$");
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
        var ancestors = capture.ancestors().stream().map(value -> normalizeAncestor(value, redactions)).toList();
        var limits = new LinkedHashSet<>(limitCodes(capture.limits(), 32));
        var traversal = normalizeTraversal(capture.traversal(), ancestors.size());
        var signals = normalizeSignals(capture.signals(), redactions);
        var normalized = new UxInspectorCapture(UxInspectorCapture.SCHEMA, UxInspectorCapture.VERSION,
                capture.captureId(), capture.capturedAt(), capture.captureProfile(), page, target, ancestors,
                traversal, signals,
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
                .distinct().toList();
        require(boundaries.size() == value.componentBoundaryTags().size(), "domFingerprint.componentBoundaryTags are invalid");
        var labelFor = nullableIdentifier(value.labelFor());
        require(value.labelFor() == null || labelFor != null, "domFingerprint.labelFor is invalid");
        return new UxInspectorCapture.DomFingerprint(attributes(value.stableAttributes()), selectors, boundaries, labelFor);
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
        var emitted = boundedInt(value.emittedNodeCount(), 1, 4096);
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
