package pl.mkn.tdw.localworkspace.analysisruns;

import com.fasterxml.jackson.databind.JsonNode;

public interface LocalAnalysisRunResultUpdateHandler {

    String feature();

    LocalAnalysisRunChatResult applyResultUpdate(
            LocalAnalysisRunIndexEntry indexEntry,
            LocalAnalysisRunRecord record,
            String messageId,
            JsonNode aiResponse
    );

    LocalAnalysisRunChatResult rejectResultUpdate(
            LocalAnalysisRunIndexEntry indexEntry,
            LocalAnalysisRunRecord record,
            String messageId,
            JsonNode aiResponse
    );
}
