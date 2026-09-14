package pl.mkn.tdw.features.operationalcontextassistance.job.localworkspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobSnapshot;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobStartRequest;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobStatus;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class OperationalContextAssistanceLocalRunPersister
        implements OperationalContextAssistanceLocalRunPersistence {

    public static final String FEATURE = "operational-context-assistance";

    private final ObjectMapper objectMapper;
    private final LocalAnalysisRunStore localAnalysisRunStore;

    @Override
    public void persistRunSnapshot(
            OperationalContextAssistanceJobSnapshot snapshot,
            OperationalContextAssistanceJobStartRequest request,
            Set<String> requiredRepositoryScopeIds
    ) {
        if (snapshot == null) {
            return;
        }
        var previous = localAnalysisRunStore.findById(snapshot.jobId())
                .map(LocalAnalysisRunRecord::exportEnvelope)
                .map(value -> {
                    try {
                        if (!OperationalContextAssistanceExportEnvelope.SCHEMA.equals(value.path("schema").asText())
                                || value.path("version").asInt() != OperationalContextAssistanceExportEnvelope.VERSION) {
                            return null;
                        }
                        return objectMapper.treeToValue(value, OperationalContextAssistanceExportEnvelope.class);
                    } catch (Exception exception) {
                        return null;
                    }
                }).orElse(null);
        var record = LocalAnalysisRunRecord.v1(
                objectMapper.valueToTree(OperationalContextAssistanceExportEnvelope.from(
                        snapshot, request, requiredRepositoryScopeIds, previous)),
                null
        );
        var runs = localAnalysisRunStore.listRuns();
        var existingName = (runs != null ? runs : List.<LocalAnalysisRunIndexEntry>of()).stream()
                .filter(entry -> snapshot.jobId().equals(entry.analysisId()) && FEATURE.equals(entry.feature()))
                .map(LocalAnalysisRunIndexEntry::name).findFirst().orElse(null);
        var index = new LocalAnalysisRunIndexEntry(
                snapshot.jobId(),
                LocalAnalysisRunRecord.SCHEMA,
                LocalAnalysisRunRecord.VERSION,
                "runs/" + snapshot.jobId() + "/run.json",
                FEATURE,
                existingName != null ? existingName : displayName(request),
                snapshot.status().name(),
                snapshot.createdAt(),
                snapshot.updatedAt(),
                snapshot.completedAt()
        );
        localAnalysisRunStore.save(index, record);
    }

    @Override
    public Optional<OperationalContextAssistanceStoredRun> findRestorable(String jobId) {
        var runs = localAnalysisRunStore.listRuns();
        var indexed = (runs != null ? runs : List.<LocalAnalysisRunIndexEntry>of()).stream()
                .anyMatch(entry -> jobId.equals(entry.analysisId()) && FEATURE.equals(entry.feature()));
        if (!indexed) return Optional.empty();
        var record = localAnalysisRunStore.findById(jobId).orElse(null);
        if (record == null) return Optional.empty();
        var envelope = record.exportEnvelope();
        if (!OperationalContextAssistanceExportEnvelope.SCHEMA.equals(envelope.path("schema").asText())
                || envelope.path("version").asInt() != OperationalContextAssistanceExportEnvelope.VERSION
                || !jobId.equals(envelope.path("job").path("jobId").asText())) return Optional.empty();
        var draftNode = envelope.path("job").path("draft");
        if (draftNode.has("questions")) return Optional.empty();
        for (var proposalNode : draftNode.path("proposals")) {
            if (proposalNode.has("questions")) return Optional.empty();
        }
        try {
            var snapshot = objectMapper.treeToValue(envelope.path("job"), OperationalContextAssistanceJobSnapshot.class);
            if (snapshot.status() != OperationalContextAssistanceJobStatus.COMPLETED
                    && snapshot.status() != OperationalContextAssistanceJobStatus.PARTIAL)
                return Optional.empty();
            if (snapshot.draft() == null || snapshot.draft().proposals().isEmpty()
                    || snapshot.steps().stream().noneMatch(step -> step.code().equals(snapshot.currentStepCode())))
                return Optional.empty();
            var scopesNode = envelope.get("requiredRepositoryScopeIds");
            if (scopesNode == null || !scopesNode.isArray()) return Optional.empty();
            var scopes = new java.util.LinkedHashSet<String>();
            for (var scope : scopesNode) {
                if (!scope.isTextual() || scope.asText().isBlank()) return Optional.empty();
                scopes.add(scope.asText());
            }
            return Optional.of(new OperationalContextAssistanceStoredRun(snapshot, scopes));
        } catch (Exception exception) {
            return Optional.empty();
        }
    }

    private String displayName(OperationalContextAssistanceJobStartRequest request) {
        if (request == null || request.mode() == null) {
            return "Asysta AI Operational Context";
        }
        var mode = switch (request.mode()) {
            case CREATE_AREA -> "Utwórz lub uzupełnij katalog";
            case IMPROVE_ENTITY -> "Uzupełnij wpis";
            case RESOLVE_FINDING -> "Rozwiąż finding";
        };
        if (request.target() != null) {
            return mode + " · " + request.target().entityType() + "/" + request.target().entityId();
        }
        if (request.gitLabSource() != null) {
            if (request.gitLabSource().project() != null) {
                return mode + " · " + request.gitLabSource().project();
            }
            if (request.gitLabSource().projectUrl() != null) {
                var path = URI.create(request.gitLabSource().projectUrl()).getPath();
                if (path != null && !path.isBlank()) {
                    return mode + " · " + path.replaceFirst("^/", "");
                }
            }
        }
        return mode;
    }
}
