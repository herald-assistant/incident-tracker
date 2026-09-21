package pl.mkn.tdw.localworkspace.analysisruns;

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
}
