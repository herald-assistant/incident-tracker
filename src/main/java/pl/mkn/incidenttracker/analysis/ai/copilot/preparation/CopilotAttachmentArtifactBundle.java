package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.github.copilot.sdk.json.MessageAttachment;

import java.nio.file.Path;
import java.util.List;

public record CopilotAttachmentArtifactBundle(
        List<MessageAttachment> attachments
) implements AutoCloseable {

    public static CopilotAttachmentArtifactBundle empty() {
        return new CopilotAttachmentArtifactBundle(List.of());
    }

    public CopilotAttachmentArtifactBundle(List<MessageAttachment> attachments, Path ignoredStagingDirectory) {
        this(attachments);
    }

    @Override
    public void close() {
        // Inline blob attachments do not require filesystem cleanup.
    }
}
