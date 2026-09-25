package pl.mkn.tdw.localworkspace.analysisruns;

import pl.mkn.tdw.shared.ai.report.AnalysisReportEditRequest;

public interface LocalAnalysisRunChatHandler {

    String feature();

    default boolean canContinue(LocalAnalysisRunIndexEntry indexEntry, LocalAnalysisRunRecord record) {
        return record != null && record.continuation() != null && record.continuation().enabled();
    }

    LocalAnalysisRunChatResult continueRun(
            LocalAnalysisRunIndexEntry indexEntry,
            LocalAnalysisRunRecord record,
            String message
    );

    default LocalAnalysisRunChatResult editReport(LocalAnalysisRunIndexEntry indexEntry,
                                                  LocalAnalysisRunRecord record,
                                                  AnalysisReportEditRequest request) {
        throw LocalAnalysisRunContinuationException.unavailable("This run does not support report editing.");
    }
}
