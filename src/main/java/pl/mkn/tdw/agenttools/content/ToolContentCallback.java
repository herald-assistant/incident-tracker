package pl.mkn.tdw.agenttools.content;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

/** Optional rich result, independent of the agent runtime or transport. */
public interface ToolContentCallback extends ToolCallback {
    ToolContentResult callWithContent(String input, ToolContext context);

    @Override default String call(String input) { return callWithContent(input, null).text(); }
    @Override default String call(String input, ToolContext context) { return callWithContent(input, context).text(); }
}
