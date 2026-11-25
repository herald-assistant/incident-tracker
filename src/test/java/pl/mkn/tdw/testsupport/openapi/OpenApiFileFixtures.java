package pl.mkn.tdw.testsupport.openapi;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Fictional documents generated for HTTP, MCP and opt-in Copilot tests. */
public final class OpenApiFileFixtures {
    private OpenApiFileFixtures() {}

    public static String yaml(String baseUrl) {
        var content = new StringBuilder();
        for (var mime : OpenApiFile.EXTENSIONS.keySet())
            content.append("            ").append(mime).append(": {schema: {type: string, format: binary}}\n");
        return """
                openapi: 3.1.0
                info: {title: Fikcyjne pliki, version: '1'}
                servers: [{url: '%s'}]
                paths:
                  /file/{format}:
                    get:
                      operationId: getFile
                      x-tdw-operation-effect: READ_ONLY
                      parameters:
                        - {name: format, in: path, required: true, schema: {type: string}}
                      responses:
                        '200':
                          description: Plik
                          content:
                %s        default: {description: Błąd, content: {application/json: {schema: {}}}}
                """.formatted(baseUrl, content);
    }

    public static BufferedImage image(String code) {
        var image = new BufferedImage(1000, 250, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, 1000, 250);
        graphics.setColor(Color.BLACK); graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 40));
        graphics.drawString("FILE-CODE: " + code, 30, 100);
        graphics.setColor(Color.BLUE); graphics.fillRect(30, 150, 100, 60);
        graphics.dispose(); return image;
    }

    public static byte[] png(String code) throws Exception {
        try (var bytes = new ByteArrayOutputStream()) {
            ImageIO.write(image(code), "png", bytes); return bytes.toByteArray();
        }
    }

    public static byte[] pdf(String code) throws Exception {
        try (var document = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            var page = new PDPage(new PDRectangle(500, 125)); document.addPage(page);
            try (var content = new PDPageContentStream(document, page)) {
                content.drawImage(LosslessFactory.createFromImage(document, image(code)), 0, 0, 500, 125);
            }
            document.save(bytes); return bytes.toByteArray();
        }
    }

    public static byte[] docx(String code) throws Exception {
        try (var bytes = new ByteArrayOutputStream(); var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">"
                    + "<w:body><w:p><w:r><w:t>FILE-CODE: " + code + "</w:t></w:r></w:p></w:body></w:document>")
                    .getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("word/media/image1.png")); zip.write(png("EMBEDDED-831")); zip.closeEntry();
            zip.finish(); return bytes.toByteArray();
        }
    }
}
