package pl.mkn.tdw.api.files;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.mkn.tdw.common.documents.DocumentAttachmentPreparer;
import pl.mkn.tdw.common.documents.DocxContentReader;

import java.io.IOException;
import java.util.Base64;
import java.util.List;

/** Stateless operator preview: no model, session, conversation or file persistence. */
@RestController
@RequestMapping("/api/files")
public class FilePreviewController {
    @PostMapping(value = "/preview", consumes = "application/octet-stream")
    public ResponseEntity<FilePreviewResponse> preview(@RequestParam String name, HttpServletRequest request) throws IOException {
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        var mime = DocumentAttachmentPreparer.mimeTypeForName(name);
        if (name.isBlank() || name.chars().anyMatch(Character::isISOControl) || mime == null)
            return ResponseEntity.badRequest().body(new FilePreviewResponse(name, "", 0, "FAILED", false,
                    "Wybierz pliki PNG, JPEG, WebP, GIF, PDF lub DOCX.", List.of()));
        var bytes = request.getInputStream().readAllBytes();
        var docx = mime.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        try {
            if (bytes.length == 0) throw new IllegalArgumentException("Pusty plik.");
            if (docx) {
                DocxContentReader.read(bytes);
                return ResponseEntity.ok(new FilePreviewResponse(name, mime, bytes.length, "READY", true,
                        "DOCX można odczytać i przygotować jako załącznik AI. Sprawdzenie techniczne nie potwierdza interpretacji przez konkretny model.", List.of()));
            }
            var images = DocumentAttachmentPreparer.prepare(name, mime, bytes).stream()
                    .map(image -> new FilePreviewResponse.Image(image.name(), image.mimeType(), image.content().length,
                            Base64.getEncoder().encodeToString(image.content()))).toList();
            return ResponseEntity.ok(new FilePreviewResponse(name, mime, bytes.length, "READY", false,
                    mime.equals("application/pdf") ? "Przygotowano wszystkie strony PDF jako obrazy dla AI."
                            : "Obraz jest przekazywany do AI bez zmiany zawartości.", images));
        } catch (IllegalArgumentException failure) {
            return ResponseEntity.unprocessableEntity().body(new FilePreviewResponse(name, mime, bytes.length, "FAILED", docx,
                    "Nie można odczytać pliku. Sprawdź jego zawartość i format.", List.of()));
        }
    }
}
