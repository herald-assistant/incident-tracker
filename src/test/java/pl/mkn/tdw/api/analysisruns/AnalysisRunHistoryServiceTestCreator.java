package pl.mkn.tdw.api.analysisruns;

import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotSessionCleanup;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunChatHandler;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;

import java.util.List;

final class AnalysisRunHistoryServiceTestCreator {

    private AnalysisRunHistoryServiceTestCreator() {
    }

    static AnalysisRunHistoryService create(LocalAnalysisRunStore localAnalysisRunStore) {
        return create(localAnalysisRunStore, List.of());
    }

    static AnalysisRunHistoryService create(
            LocalAnalysisRunStore localAnalysisRunStore,
            List<LocalAnalysisRunChatHandler> chatHandlers
    ) {
        return create(localAnalysisRunStore, chatHandlers, CopilotSessionCleanup.NO_OP);
    }

    static AnalysisRunHistoryService create(
            LocalAnalysisRunStore localAnalysisRunStore,
            List<LocalAnalysisRunChatHandler> chatHandlers,
            CopilotSessionCleanup copilotSessionCleanup
    ) {
        return new AnalysisRunHistoryService(
                localAnalysisRunStore,
                chatHandlers,
                copilotSessionCleanup
        );
    }
}
