package pl.mkn.tdw.aiplatform.copilot.runtime;

import com.github.copilot.generated.rpc.Model;
import pl.mkn.tdw.aiplatform.copilot.runtime.auth.CopilotRunAuth;

import java.util.List;

public interface CopilotSdkModelLister {

    List<Model> listModels(CopilotRunAuth auth);
}
