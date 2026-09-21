package pl.mkn.tdw.features.uiexplorer.job.localworkspace;

import pl.mkn.tdw.features.uiexplorer.context.UiExplorerScreenReachabilityContext;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerSourceScope;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerCoverageStatus;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerScreenIdentity;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceRevision;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStartRequest;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStateSnapshot;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobRequestSnapshot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

public record UiExplorerContinuationSnapshot(
        String schema,
        int version,
        Instant storedAt,
        String jobId,
        UiExplorerJobRequestSnapshot request,
        String systemId,
        String systemLabel,
        UiExplorerSourceScope sourceScope,
        UiExplorerScreenIdentity screen,
        UiExplorerSourceRevision sourceRevision,
        String fingerprint
) {

    public static final String SCHEMA = "tdw.ui-explorer-continuation";
    public static final int VERSION = 1;

    public static UiExplorerContinuationSnapshot from(
            UiExplorerJobStateSnapshot snapshot,
            UiExplorerScreenReachabilityContext context,
            Instant storedAt
    ) {
        return new UiExplorerContinuationSnapshot(
                SCHEMA,
                VERSION,
                storedAt != null ? storedAt : Instant.now(),
                snapshot.jobId(),
                snapshot.request(),
                context.systemId(),
                context.systemLabel(),
                context.sourceScope(),
                context.screen(),
                context.sourceRevision(),
                fingerprint(snapshot, context)
        );
    }

    public boolean matches(UiExplorerJobStateSnapshot snapshot) {
        return snapshot != null
                && Objects.equals(jobId, snapshot.jobId())
                && Objects.equals(request, snapshot.request())
                && Objects.equals(fingerprint, fingerprint(snapshot, toContext()));
    }

    public UiExplorerScreenReachabilityContext toContext() {
        return new UiExplorerScreenReachabilityContext(
                systemId,
                systemLabel,
                sourceScope,
                screen,
                "RESTORED",
                false,
                List.of(),
                List.of(),
                List.of(),
                null,
                sourceRevision,
                UiExplorerCoverageStatus.READY,
                null,
                List.of(),
                null,
                List.of(),
                List.of()
        );
    }

    public UiExplorerJobStartRequest toStartRequest() {
        var modes = new java.util.EnumMap<pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId,
                pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionMode>(
                pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId.class);
        request.sectionModes().forEach(assignment -> modes.put(assignment.sectionId(), assignment.mode()));
        return new UiExplorerJobStartRequest(
                request.systemId(), request.branch(), request.screenId(), request.sourceRevision(), modes,
                request.scenarioDescription(), request.aiModel(), request.reasoningEffort()
        );
    }

    private static String fingerprint(
            UiExplorerJobStateSnapshot snapshot,
            UiExplorerScreenReachabilityContext context
    ) {
        var reportId = snapshot.report() != null ? snapshot.report().reportId() : null;
        var material = String.join("\n",
                value(snapshot.jobId()),
                value(snapshot.request() != null ? snapshot.request().systemId() : null),
                value(snapshot.request() != null ? snapshot.request().branch() : null),
                value(snapshot.request() != null ? snapshot.request().screenId() : null),
                value(snapshot.request() != null ? snapshot.request().sourceRevision() : null),
                value(reportId),
                value(context.sourceScope() != null ? context.sourceScope().gitLabGroup() : null),
                value(context.sourceScope() != null ? context.sourceScope().projectName() : null),
                value(context.sourceScope() != null ? context.sourceScope().ref() : null),
                context.sourceScope() != null ? String.join("\u001f", context.sourceScope().pathPrefixes()) : "",
                value(context.sourceRevision() != null ? context.sourceRevision().branch() : null),
                value(context.sourceRevision() != null ? context.sourceRevision().revision() : null));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static String value(String value) {
        return value != null ? value : "";
    }
}
