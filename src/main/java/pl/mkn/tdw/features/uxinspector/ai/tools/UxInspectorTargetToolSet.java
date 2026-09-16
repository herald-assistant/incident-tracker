package pl.mkn.tdw.features.uxinspector.ai.tools;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

public record UxInspectorTargetToolSet(UxInspectorTargetTools tools, List<ToolCallback> callbacks) {
    public UxInspectorTargetToolSet {
        callbacks = callbacks != null ? List.copyOf(callbacks) : List.of();
    }
}

