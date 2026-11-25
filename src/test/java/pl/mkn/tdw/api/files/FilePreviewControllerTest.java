package pl.mkn.tdw.api.files;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import pl.mkn.tdw.common.documents.DocumentAttachmentPreparer;
import pl.mkn.tdw.testsupport.openapi.OpenApiFileFixtures;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FilePreviewController.class)
class FilePreviewControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test void returnsEveryPdfPageIdenticallyToConversationAttachmentsWithoutAi() throws Exception {
        byte[] pdf;
        try (var document = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.addPage(new PDPage());
            document.save(bytes);
            pdf = bytes.toByteArray();
        }
        var result = mvc.perform(post("/api/files/preview").param("name", "CRM-831.pdf")
                        .contentType("application/octet-stream").content(pdf))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.validationOnly").value(false))
                .andExpect(jsonPath("$.images.length()").value(2)).andReturn();
        var response = mapper.readTree(result.getResponse().getContentAsByteArray());
        var prepared = DocumentAttachmentPreparer.prepare("CRM-831.pdf", "application/pdf", pdf);
        for (var page = 0; page < 2; page++) {
            var image = response.path("images").get(page);
            assertThat(image.path("name").asText()).isEqualTo("CRM-831.pdf — strona " + (page + 1) + "/2.jpg");
            assertThat(image.path("mimeType").asText()).isEqualTo("image/jpeg");
            assertThat(Base64.getDecoder().decode(image.path("data").asText())).containsExactly(prepared.get(page).content());
        }
    }

    @Test void preservesImageBytesAndNormalizesTheFileName() throws Exception {
        var image = OpenApiFileFixtures.png("CRM-215");
        mvc.perform(post("/api/files/preview").param("name", "../../CRM-215.PNG")
                        .contentType("application/octet-stream").content(image))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("CRM-215.PNG"))
                .andExpect(jsonPath("$.mimeType").value("image/png"))
                .andExpect(jsonPath("$.images[0].data").value(Base64.getEncoder().encodeToString(image)));
    }

    @Test void validatesDocxWithoutReturningItsTextImagesOrOriginalBytes() throws Exception {
        var docx = OpenApiFileFixtures.docx("CRM-PRIVATE-682");
        var result = mvc.perform(post("/api/files/preview").param("name", "CRM.docx")
                        .contentType("application/octet-stream").content(docx))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.validationOnly").value(true))
                .andExpect(jsonPath("$.images").isEmpty()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("CRM-PRIVATE-682", "documentText",
                Base64.getEncoder().encodeToString(docx), "EMBEDDED-831");
    }

    @Test void rejectsUnreadablePdfDocxAndEmptyFiles() throws Exception {
        for (var name : new String[]{"CRM.pdf", "CRM.docx", "CRM.png"}) {
            mvc.perform(post("/api/files/preview").param("name", name).contentType("application/octet-stream"))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.status").value("FAILED"))
                    .andExpect(jsonPath("$.images").isEmpty());
        }
        for (var name : new String[]{"CRM.pdf", "CRM.docx"}) {
            mvc.perform(post("/api/files/preview").param("name", name)
                            .contentType("application/octet-stream").content(new byte[]{1, 2, 3}))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.status").value("FAILED"));
        }
    }

    @Test void rejectsZipWithWrongWordRootAndExternalEntities() throws Exception {
        for (var xml : new String[]{"<crm>not a Word document</crm>",
                "<!DOCTYPE document [<!ENTITY xxe SYSTEM 'http://example.invalid/private'>]><document><t>&xxe;</t></document>"}) {
            var bytes = new ByteArrayOutputStream();
            try (var zip = new ZipOutputStream(bytes)) {
                zip.putNextEntry(new ZipEntry("word/document.xml"));
                zip.write(xml.getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            mvc.perform(post("/api/files/preview").param("name", "CRM.docx")
                            .contentType("application/octet-stream").content(bytes.toByteArray()))
                    .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.validationOnly").value(true))
                    .andExpect(jsonPath("$.images").isEmpty());
        }
    }

    @Test void rejectsUnsupportedFormatsMissingNamesAndOtherRequestMediaTypes() throws Exception {
        mvc.perform(post("/api/files/preview").param("name", "CRM.zip")
                        .contentType("application/octet-stream").content(new byte[]{1}))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value("FAILED"));
        mvc.perform(post("/api/files/preview").contentType("application/octet-stream").content(new byte[]{1}))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/files/preview").param("name", "CRM.png").contentType("image/png").content(new byte[]{1}))
                .andExpect(status().isUnsupportedMediaType());
    }
}
