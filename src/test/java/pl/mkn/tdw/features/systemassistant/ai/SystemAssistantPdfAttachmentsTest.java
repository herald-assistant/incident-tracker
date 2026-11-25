package pl.mkn.tdw.features.systemassistant.ai;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class SystemAssistantPdfAttachmentsTest {
    @Test void rendersAllScannedPagesInOrderAndKeepsOtherAttachmentsUnchanged() throws Exception {
        byte[] pdf;
        try (var document = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            for (var color : List.of(Color.RED, Color.BLUE)) {
                var page = new PDPage(new PDRectangle(50, 80));
                document.addPage(page);
                var scan = new BufferedImage(50, 80, BufferedImage.TYPE_INT_RGB);
                var graphics = scan.createGraphics();
                graphics.setColor(color); graphics.fillRect(0, 0, 50, 80); graphics.dispose();
                try (var content = new PDPageContentStream(document, page)) {
                    content.drawImage(LosslessFactory.createFromImage(document, scan), 0, 0, 50, 80);
                }
            }
            document.save(bytes); pdf = bytes.toByteArray();
        }
        var prepared = SystemAssistantAttachments.prepare(List.of(
                new SystemAssistantAiPort.Attachment("scan.pdf", "application/pdf", pdf),
                new SystemAssistantAiPort.Attachment("image.png", "image/png", new byte[]{1, 2, 3}),
                new SystemAssistantAiPort.Attachment("report.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", new byte[]{4, 5})));
        assertThat(prepared).hasSize(4);
        for (var page = 0; page < 2; page++) {
            var blob = prepared.get(page);
            assertThat(blob.getDisplayName()).isEqualTo("scan.pdf — strona " + (page + 1) + "/2.jpg");
            assertThat(blob.getMimeType()).isEqualTo("image/jpeg");
            var image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(blob.getData())));
            assertThat(image.getWidth()).isEqualTo(100);
            assertThat(image.getHeight()).isEqualTo(160);
            var color = new Color(image.getRGB(50, 80));
            assertThat(color.getGreen()).isLessThan(15);
            if (page == 0) {
                assertThat(color.getRed()).isGreaterThan(240);
                assertThat(color.getBlue()).isLessThan(15);
            } else {
                assertThat(color.getBlue()).isGreaterThan(240);
                assertThat(color.getRed()).isLessThan(15);
            }
        }
        assertThat(Base64.getDecoder().decode(prepared.get(2).getData())).containsExactly(1, 2, 3);
        assertThat(prepared.get(2).getDisplayName()).isEqualTo("image.png");
        assertThat(prepared.get(2).getMimeType()).isEqualTo("image/png");
        assertThat(Base64.getDecoder().decode(prepared.get(3).getData())).containsExactly(4, 5);
        assertThat(prepared.get(3).getMimeType()).isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    }

    @Test void rejectsUnreadableAndEmptyPdfsInsteadOfSilentlyDroppingTheirContent() throws Exception {
        byte[] empty;
        try (var document = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            document.save(bytes); empty = bytes.toByteArray();
        }
        for (var content : List.of(new byte[]{1, 2, 3}, empty)) {
            assertThatThrownBy(() -> SystemAssistantAttachments.prepare(List.of(
                    new SystemAssistantAiPort.Attachment("invalid.pdf", "application/pdf", content))))
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("invalid.pdf");
        }
    }
}
