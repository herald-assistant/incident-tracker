package pl.mkn.tdw.common.documents;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Provider-neutral preparation of conversation file attachments. */
public final class DocumentAttachmentPreparer {
    private static final Map<String, String> MEDIA_TYPES = Map.of(
            "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg", "webp", "image/webp",
            "gif", "image/gif", "pdf", "application/pdf",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private DocumentAttachmentPreparer() {}

    public record Attachment(String name, String mimeType, byte[] content) {}

    public static String mimeTypeForName(String name) {
        if (name == null) return null;
        return MEDIA_TYPES.get(name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT));
    }

    public static List<Attachment> prepare(String name, String mimeType, byte[] content) {
        if (!"application/pdf".equals(mimeType)) return List.of(new Attachment(name, mimeType, content));
        // All models receive every PDF page, including scans and diagrams, at the same resolution.
        return PdfPageRenderer.render(name, content).stream()
                .map(page -> new Attachment(page.name(), "image/jpeg", page.content())).toList();
    }
}
