package pl.mkn.tdw.common.documents;

import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Provider-neutral preparation of conversation file attachments. */
public final class DocumentAttachmentPreparer {
    private static final Map<String, String> MEDIA_TYPES = Map.of(
            "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg", "webp", "image/webp",
            "gif", "image/gif", "bmp", "image/bmp", "pdf", "application/pdf",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private DocumentAttachmentPreparer() {}

    public record Attachment(String name, String mimeType, byte[] content) {}

    public static String mimeTypeForName(String name) {
        if (name == null) return null;
        return MEDIA_TYPES.get(name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT));
    }

    public static List<Attachment> prepare(String name, String mimeType, byte[] content) {
        if ("image/bmp".equals(mimeType)) return List.of(bmpAsPng(name, content));
        if (!"application/pdf".equals(mimeType)) return List.of(new Attachment(name, mimeType, content));
        // All models receive every PDF page, including scans and diagrams, at the same resolution.
        return PdfPageRenderer.render(name, content).stream()
                .map(page -> new Attachment(page.name(), "image/jpeg", page.content())).toList();
    }

    private static Attachment bmpAsPng(String name, byte[] content) {
        var reader = ImageIO.getImageReadersByFormatName("BMP").next();
        var bytes = new ByteArrayOutputStream();
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(content));
             var output = new MemoryCacheImageOutputStream(bytes)) {
            reader.setInput(input);
            var image = reader.read(0);
            if (!ImageIO.write(image, "png", output)) throw new IOException("PNG encoder unavailable");
            output.flush();
            return new Attachment(name.replaceFirst("(?i)\\.bmp$", "") + ".png", "image/png", bytes.toByteArray());
        } catch (IOException | RuntimeException exception) {
            throw new IllegalArgumentException("Nie można przygotować obrazu BMP „" + name + "”.", exception);
        } finally {
            reader.dispose();
        }
    }
}
