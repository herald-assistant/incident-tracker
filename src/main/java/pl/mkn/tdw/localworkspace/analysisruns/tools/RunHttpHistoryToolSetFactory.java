package pl.mkn.tdw.localworkspace.analysisruns.tools;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.localworkspace.analysisruns.LocalAnalysisRunStore;
import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RunHttpHistoryToolSetFactory {
    private final LocalAnalysisRunStore runs;
    public List<ToolCallback> create(String runId) {
        if (runId == null || !runId.matches("[A-Za-z0-9._-]{1,128}")) return List.of();
        try {
            if (!runs.findById(runId).map(record -> record.httpHistorySnapshot())
                    .map(snapshot -> snapshot.available()).orElse(false)) return List.of();
            return Arrays.asList(MethodToolCallbackProvider.builder()
                    .toolObjects(new RunHttpHistoryTools(runs, runId)).build().getToolCallbacks());
        } catch (RuntimeException exception) { return List.of(); }
    }
}
