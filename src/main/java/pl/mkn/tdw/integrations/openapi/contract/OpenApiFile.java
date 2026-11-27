package pl.mkn.tdw.integrations.openapi.contract;

/** Original bytes of an HTTP file, carried separately from JSON tool arguments and results. */
public record OpenApiFile(String name, String mimeType, byte[] content) {
    public static final java.util.Map<String, String> EXTENSIONS = java.util.Map.of(
            "image/png", "png", "image/jpeg", "jpg", "image/webp", "webp", "image/gif", "gif",
            "image/bmp", "bmp", "application/pdf", "pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "docx");
}
