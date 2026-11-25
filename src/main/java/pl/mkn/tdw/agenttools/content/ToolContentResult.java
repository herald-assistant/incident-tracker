package pl.mkn.tdw.agenttools.content;

import java.util.List;

/** Text for logging/history and binary content for the current model request only. */
public record ToolContentResult(String text, List<Binary> binaries) {
    public ToolContentResult { binaries = List.copyOf(binaries); }
    public record Binary(String name, String mimeType, byte[] content) {}
}
