package pl.mkn.tdw.aiplatform.copilot.runtime;

import com.github.copilot.rpc.ModelInfo;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuth;

import java.util.List;

public interface CopilotSdkModelLister {

    List<ModelInfo> listModels(CopilotRunAuth auth);
}
