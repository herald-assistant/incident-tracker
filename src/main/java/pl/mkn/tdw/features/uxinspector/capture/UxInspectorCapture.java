package pl.mkn.tdw.features.uxinspector.capture;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record UxInspectorCapture(
        @NotNull String schema,
        int version,
        @NotNull String captureId,
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING) Instant capturedAt,
        @NotNull CaptureProfile captureProfile,
        @NotNull @Valid Page page,
        @NotNull @Valid Target target,
        @NotNull @Valid List<@NotNull Ancestor> ancestors,
        @NotNull @Valid Traversal traversal,
        @NotNull @Valid Signals signals,
        @NotNull List<@NotNull String> limits,
        @NotNull @Valid Client client
) {
    public static final String SCHEMA = "tdw.ux-inspector-capture";
    public static final int VERSION = 1;

    public UxInspectorCapture {
        ancestors = ancestors == null ? null : List.copyOf(ancestors);
        limits = limits == null ? null : List.copyOf(limits);
    }

    public record Page(@NotNull String origin, @NotNull String path, String title, String language,
                       @NotNull List<@NotNull String> queryParameterNames) {
        public Page { queryParameterNames = queryParameterNames == null ? null : List.copyOf(queryParameterNames); }
    }
    public record Target(@NotNull String tag, String role, String accessibleName, String text,
                         @NotNull @Valid DomFingerprint domFingerprint, @NotNull @Valid TargetState state,
                         @NotNull @Valid Bounds bounds) {}
    public record DomFingerprint(@NotNull Map<String, @NotNull String> stableAttributes,
                                 @NotNull List<@NotNull String> selectorCandidates,
                                 @NotNull List<@NotNull String> componentBoundaryTags, String labelFor) {
        public DomFingerprint {
            stableAttributes = stableAttributes == null ? null : Map.copyOf(stableAttributes);
            selectorCandidates = selectorCandidates == null ? null : List.copyOf(selectorCandidates);
            componentBoundaryTags = componentBoundaryTags == null ? null : List.copyOf(componentBoundaryTags);
        }
    }
    public record Ancestor(int depth, @NotNull String tag, String role, String accessibleName, String text,
                           @NotNull Map<String, @NotNull String> stableAttributes) {
        public Ancestor { stableAttributes = stableAttributes == null ? null : Map.copyOf(stableAttributes); }
    }
    public record TargetState(boolean disabled, boolean ariaDisabled, boolean readOnly, boolean required,
                              boolean invalid, Boolean checked, Boolean expanded, boolean hidden) {}
    public record Bounds(double x, double y, double width, double height) {}
    public enum CaptureProfile { ELEMENT_CONTEXT, FORM_DIAGNOSTICS }
    public record Traversal(int observedDepth, int emittedNodeCount, int omittedNodeCount, boolean reachedDocumentRoot) {}
    public record Signals(int shadowBoundaryCount, @NotNull String frame, @NotNull List<@NotNull String> redactions) {
        public Signals { redactions = redactions == null ? null : List.copyOf(redactions); }
    }
    public record Client(@NotNull String name, @NotNull String version, @NotNull String featureId) {}
}
