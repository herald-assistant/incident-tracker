package pl.mkn.incidenttracker.aiplatform.copilot.runtime;

import com.github.copilot.sdk.json.ModelInfo;

import java.util.List;

public interface CopilotSdkModelLister {

    List<ModelInfo> listModels();
}
