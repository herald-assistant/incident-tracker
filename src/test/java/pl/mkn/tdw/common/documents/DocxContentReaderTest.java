package pl.mkn.tdw.common.documents;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.testsupport.openapi.OpenApiFileFixtures;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.assertj.core.api.Assertions.*;

class DocxContentReaderTest {
    @Test void extractsTextAndEmbeddedImages() throws Exception {
        var content = DocxContentReader.read(OpenApiFileFixtures.docx("WORD-682"));
        assertThat(content.text()).contains("FILE-CODE: WORD-682");
        assertThat(content.images()).singleElement().satisfies(image -> {
            assertThat(image.mimeType()).isEqualTo("image/png");
            assertThat(image.content()).containsExactly(OpenApiFileFixtures.png("EMBEDDED-831"));
        });
    }

    @Test void rejectsInvalidArchivesAndDocumentsWithDtd() throws Exception {
        assertThatThrownBy(() -> DocxContentReader.read(new byte[]{1, 2, 3})).isInstanceOf(IllegalArgumentException.class);
        byte[] xml;
        try (var bytes = new ByteArrayOutputStream(); var zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<!DOCTYPE document [<!ENTITY xxe SYSTEM 'http://example.invalid/private'>]><document><t>&xxe;</t></document>".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry(); zip.finish(); xml = bytes.toByteArray();
        }
        assertThatThrownBy(() -> DocxContentReader.read(xml)).isInstanceOf(IllegalArgumentException.class);
    }
}
