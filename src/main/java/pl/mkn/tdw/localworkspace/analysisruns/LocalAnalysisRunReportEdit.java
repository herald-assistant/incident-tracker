package pl.mkn.tdw.localworkspace.analysisruns;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;

import java.time.Instant;

/** Changes only the current report projection; the private continuation record stays intact. */
public final class LocalAnalysisRunReportEdit {
    private LocalAnalysisRunReportEdit() {
    }

    public static LocalAnalysisRunChatResult update(LocalAnalysisRunRecord record, ObjectMapper mapper,
                                                     AnalysisReport report, Object result) {
        if (!(record.exportEnvelope().deepCopy() instanceof ObjectNode envelope)
                || !(envelope.path("payload") instanceof ObjectNode payload)
                || !(payload.path("job") instanceof ObjectNode job)) {
            throw LocalAnalysisRunContinuationException.corrupted("Local run has no editable result snapshot.", null);
        }
        var updatedAt = Instant.now();
        job.set("report", mapper.valueToTree(report));
        job.set("result", mapper.valueToTree(result));
        job.put("updatedAt", updatedAt.toString());
        if (envelope.has("storedAt")) envelope.put("storedAt", updatedAt.toString());
        if (envelope.has("exportedAt")) envelope.put("exportedAt", updatedAt.toString());
        return new LocalAnalysisRunChatResult(LocalAnalysisRunRecord.v1(envelope, record.continuation())
                .withStoreSnapshot(record.storeSnapshot()), updatedAt);
    }
}
