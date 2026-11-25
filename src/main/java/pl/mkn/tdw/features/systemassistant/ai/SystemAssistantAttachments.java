package pl.mkn.tdw.features.systemassistant.ai;

import com.github.copilot.rpc.BlobAttachment;
import pl.mkn.tdw.common.documents.DocumentAttachmentPreparer;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

final class SystemAssistantAttachments {
    private SystemAssistantAttachments() {}

    static List<BlobAttachment> prepare(List<SystemAssistantAiPort.Attachment> files) {
        var result = new ArrayList<BlobAttachment>();
        for (var file : files) {
            for (var prepared : DocumentAttachmentPreparer.prepare(file.name(), file.mimeType(), file.content()))
                result.add(blob(prepared.name(), prepared.mimeType(), prepared.content()));
        }
        return List.copyOf(result);
    }

    private static BlobAttachment blob(String name, String mimeType, byte[] content) {
        return new BlobAttachment().setDisplayName(name).setMimeType(mimeType)
                .setData(Base64.getEncoder().encodeToString(content));
    }
}
