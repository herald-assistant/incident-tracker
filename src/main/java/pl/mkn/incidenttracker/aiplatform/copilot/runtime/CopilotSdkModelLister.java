package pl.mkn.incidenttracker.aiplatform.copilot.runtime;

import com.github.copilot.sdk.json.ModelInfo;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.auth.CopilotRunAuth;

import java.util.List;

public interface CopilotSdkModelLister {

    List<ModelInfo> listModels(CopilotRunAuth auth);
}
