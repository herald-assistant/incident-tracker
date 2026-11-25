package pl.mkn.tdw.integrations.openapi.contract;

/** In-memory HTTP file response; never included in the textual tool result. */
public record OpenApiFile(String name, String mimeType, byte[] content) {
    public static final java.util.Map<String, String> EXTENSIONS = java.util.Map.of(
            "image/png", "png", "image/jpeg", "jpg", "image/webp", "webp", "image/gif", "gif",
            "application/pdf", "pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");
}
