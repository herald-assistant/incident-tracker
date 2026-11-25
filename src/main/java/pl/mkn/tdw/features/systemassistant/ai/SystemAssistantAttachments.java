package pl.mkn.tdw.features.systemassistant.ai;

import com.github.copilot.rpc.BlobAttachment;
import pl.mkn.tdw.common.documents.PdfPageRenderer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

final class SystemAssistantAttachments {
    private SystemAssistantAttachments() {}

    static List<BlobAttachment> prepare(List<SystemAssistantAiPort.Attachment> files) {
        var result = new ArrayList<BlobAttachment>();
        for (var file : files) {
            if (!"application/pdf".equals(file.mimeType())) {
                result.add(blob(file.name(), file.mimeType(), file.content()));
                continue;
            }
            // Native PDFs are omitted by the GPT tool-calling path in Copilot CLI.
            // Render every page for all models, preserving diagrams and scanned content.
            for (var page : PdfPageRenderer.render(file.name(), file.content()))
                result.add(blob(page.name(), "image/jpeg", page.content()));
        }
        return List.copyOf(result);
    }

    private static BlobAttachment blob(String name, String mimeType, byte[] content) {
        return new BlobAttachment().setDisplayName(name).setMimeType(mimeType)
                .setData(Base64.getEncoder().encodeToString(content));
    }
}
