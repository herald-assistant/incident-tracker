package pl.mkn.tdw.common.documents;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** In-memory rendering shared by uploaded documents and HTTP tool responses. */
public final class PdfPageRenderer {
    private PdfPageRenderer() {}
    public record Page(String name, byte[] content) {}

    public static List<Page> render(String name, byte[] content) {
        var result = new ArrayList<Page>();
        try (var document = Loader.loadPDF(content)) {
            if (document.getNumberOfPages() == 0) throw new IOException("PDF has no pages");
            var renderer = new PDFRenderer(document);
            for (var page = 0; page < document.getNumberOfPages(); page++) {
                var image = renderer.renderImageWithDPI(page, 144, ImageType.RGB);
                try (var bytes = new ByteArrayOutputStream()) {
                    ImageIO.write(image, "jpeg", bytes);
                    result.add(new Page(name + " — strona " + (page + 1) + "/"
                            + document.getNumberOfPages() + ".jpg", bytes.toByteArray()));
                } finally { image.flush(); }
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("Nie można odczytać załączonego PDF: " + name, exception);
        }
        return List.copyOf(result);
    }
}
