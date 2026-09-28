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
public class RunStoreToolSetFactory {
    private final LocalAnalysisRunStore runs;

    public List<ToolCallback> create(String runId) {
        if (!available(runId)) return List.of();
        return Arrays.asList(MethodToolCallbackProvider.builder()
                .toolObjects(new RunStoreTools(runs)).build().getToolCallbacks());
    }

    public boolean available(String runId) {
        if (runId == null || !runId.matches("[A-Za-z0-9._-]{1,128}")) return false;
        try {
            return runs.findById(runId).map(record -> record.storeSnapshot() != null).orElse(false);
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
