package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.github.copilot.sdk.json.ModelInfo;

import java.util.List;

public interface CopilotSdkModelLister {

    List<ModelInfo> listModels();
}
