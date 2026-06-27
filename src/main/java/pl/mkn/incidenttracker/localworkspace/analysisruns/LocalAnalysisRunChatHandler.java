package pl.mkn.incidenttracker.localworkspace.analysisruns;

public interface LocalAnalysisRunChatHandler {

    String feature();

    LocalAnalysisRunChatResult continueRun(
            LocalAnalysisRunIndexEntry indexEntry,
            LocalAnalysisRunRecord record,
            String message
    );
}
