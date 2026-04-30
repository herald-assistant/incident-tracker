package pl.mkn.incidenttracker.analysis.ai.copilot.runtime;

import com.github.copilot.sdk.json.ModelInfo;

import java.util.List;

public interface CopilotSdkModelLister {

    List<ModelInfo> listModels();
}
