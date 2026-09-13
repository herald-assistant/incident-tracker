package pl.mkn.tdw.features.operationalcontextassistance.job.localworkspace;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobSnapshot;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobStartRequest;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunIndexEntry;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunRecord;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;

import java.net.URI;

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
            OperationalContextAssistanceJobStartRequest request
    ) {
        if (snapshot == null) {
            return;
        }
        var record = LocalAnalysisRunRecord.v1(
                objectMapper.valueToTree(OperationalContextAssistanceExportEnvelope.from(snapshot, request)),
                null
        );
        var index = new LocalAnalysisRunIndexEntry(
                snapshot.jobId(),
                LocalAnalysisRunRecord.SCHEMA,
                LocalAnalysisRunRecord.VERSION,
                "runs/" + snapshot.jobId() + "/run.json",
                FEATURE,
                displayName(request),
                snapshot.status().name(),
                snapshot.createdAt(),
                snapshot.updatedAt(),
                snapshot.completedAt()
        );
        localAnalysisRunStore.save(index, record);
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
