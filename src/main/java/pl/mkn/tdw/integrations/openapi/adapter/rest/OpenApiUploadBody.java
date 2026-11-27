package pl.mkn.tdw.integrations.openapi.adapter.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiArguments;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiException;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiFile;
import pl.mkn.tdw.integrations.openapi.contract.OpenApiUpload;

import java.nio.charset.StandardCharsets;
import java.util.Map;

final class OpenApiUploadBody {
    private OpenApiUploadBody() {}

    static void apply(RestClient.RequestBodySpec request, OpenApiUpload upload, OpenApiArguments arguments,
                      ObjectMapper mapper) throws JsonProcessingException {
        var body = arguments.json().get("body");
        if (!upload.rawMediaTypes().isEmpty()) {
            var file = file(body, arguments.files());
            var mime = upload.rawMediaTypes().contains(file.mimeType()) ? file.mimeType() : MediaType.APPLICATION_OCTET_STREAM_VALUE;
            if (!upload.rawMediaTypes().contains(mime)) throw incompatible();
            request.contentType(MediaType.parseMediaType(mime)).body(file.content());
            return;
        }
        var multipart = new LinkedMultiValueMap<String, HttpEntity<byte[]>>();
        var entries = body.fields();
        while (entries.hasNext()) {
            var entry = entries.next();
            var name = entry.getKey();
            var part = upload.parts().get(name);
            var value = entry.getValue();
            var mediaType = MediaType.parseMediaType(part.mediaType());
            switch (part.kind()) {
                case FILE -> multipart.add(name, filePart(name, mediaType, file(value, arguments.files())));
                case FILE_ARRAY -> {
                    for (var reference : value)
                        multipart.add(name, filePart(name, mediaType, file(reference, arguments.files())));
                }
                case JSON -> multipart.add(name, part(name, null, mediaType, mapper.writeValueAsBytes(value)));
                case TEXT -> {
                    if (value.isNull()) throw new OpenApiException("Część tekstowa multipart wymaga wartości różnej od null.");
                    multipart.add(name, part(name, null, mediaType, value.asText().getBytes(StandardCharsets.UTF_8)));
                }
            }
        }
        request.contentType(MediaType.MULTIPART_FORM_DATA).body(multipart);
    }

    private static OpenApiFile file(JsonNode reference, Map<String, OpenApiFile> files) {
        var file = files.get(reference.path("attachmentId").asText(""));
        if (file == null) throw new OpenApiException("Brak oryginału wskazanego pliku w tym wywołaniu.");
        return file;
    }

    private static HttpEntity<byte[]> filePart(String name, MediaType mediaType, OpenApiFile file) {
        if (!MediaType.APPLICATION_OCTET_STREAM.equals(mediaType) && !mediaType.toString().equals(file.mimeType()))
            throw incompatible();
        return part(name, file.name(), mediaType, file.content());
    }

    private static OpenApiException incompatible() {
        return new OpenApiException("Typ MIME załącznika nie pasuje do kontraktu operacji.");
    }

    private static HttpEntity<byte[]> part(String name, String filename, MediaType mediaType, byte[] bytes) {
        var headers = new HttpHeaders();
        var disposition = ContentDisposition.formData().name(name);
        if (filename != null) {
            if (filename.isBlank() || filename.chars().anyMatch(Character::isISOControl) || filename.contains("/") || filename.contains("\\"))
                throw new OpenApiException("Nieprawidłowa nazwa pliku.");
            disposition.filename(filename);
        }
        headers.setContentDisposition(disposition.build());
        headers.setContentType(mediaType);
        return new HttpEntity<>(bytes, headers);
    }
}
