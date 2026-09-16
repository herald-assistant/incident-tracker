package pl.mkn.tdw.features.uxinspector.ai.tools;

import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.uxinspector.context.UxInspectorTargetContext;

import java.util.Arrays;

@Component
public class UxInspectorTargetToolSetFactory {
    public UxInspectorTargetToolSet create(String runId, UxInspectorTargetContext context) {
        var tools = new UxInspectorTargetTools(runId, context);
        var provider = MethodToolCallbackProvider.builder().toolObjects(tools).build();
        return new UxInspectorTargetToolSet(tools, Arrays.asList(provider.getToolCallbacks()));
    }
}
