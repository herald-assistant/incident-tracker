package pl.mkn.tdw.api.files;

import java.util.List;

public record FilePreviewResponse(String name, String mimeType, long size, String status,
                                  boolean validationOnly, String message, List<Image> images) {
    public FilePreviewResponse { images = List.copyOf(images); }
    public record Image(String name, String mimeType, long size, String data) {}
}
