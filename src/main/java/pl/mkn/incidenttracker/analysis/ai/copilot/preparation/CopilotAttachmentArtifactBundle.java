package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

import com.github.copilot.sdk.json.Attachment;
import org.springframework.util.FileSystemUtils;

import java.nio.file.Path;
import java.util.List;

public record CopilotAttachmentArtifactBundle(
        List<Attachment> attachments,
        Path stagingDirectory
) implements AutoCloseable {

    public static CopilotAttachmentArtifactBundle empty() {
        return new CopilotAttachmentArtifactBundle(List.of(), null);
    }

    @Override
    public void close() {
        if (stagingDirectory == null) {
            return;
        }

        try {
            FileSystemUtils.deleteRecursively(stagingDirectory);
        } catch (Exception ignored) {
            // Cleanup is best-effort. The analysis result should not fail because temp files
            // could not be removed after the SDK call completed.
        }
    }
}
