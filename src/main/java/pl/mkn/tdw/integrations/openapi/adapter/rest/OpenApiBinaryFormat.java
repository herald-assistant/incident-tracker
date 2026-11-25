package pl.mkn.tdw.integrations.openapi.adapter.rest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipInputStream;

/** Identifies supported HTTP files; document readers still validate their contents. */
final class OpenApiBinaryFormat {
    private OpenApiBinaryFormat() {}

    static String detect(byte[] bytes) {
        if (startsWith(bytes, "%PDF-")) return "application/pdf";
        if (bytes.length >= 8 && bytes[0] == (byte) 0x89 && startsWith(bytes, 1, "PNG\r\n\u001a\n"))
            return "image/png";
        if (bytes.length >= 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 && bytes[2] == (byte) 0xff)
            return "image/jpeg";
        if (startsWith(bytes, "GIF87a") || startsWith(bytes, "GIF89a")) return "image/gif";
        if (startsWith(bytes, "RIFF") && startsWith(bytes, 8, "WEBP")) return "image/webp";
        if (startsWith(bytes, "PK\u0003\u0004") && containsWordDocument(bytes))
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        return null;
    }

    private static boolean containsWordDocument(byte[] bytes) {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (!entry.isDirectory() && entry.getName().equals("word/document.xml")) return true;
            }
        } catch (IOException | IllegalArgumentException invalidZip) {
            return false;
        }
        return false;
    }

    private static boolean startsWith(byte[] bytes, String prefix) {
        return startsWith(bytes, 0, prefix);
    }

    private static boolean startsWith(byte[] bytes, int offset, String prefix) {
        var expected = prefix.getBytes(StandardCharsets.ISO_8859_1);
        if (bytes.length < offset + expected.length) return false;
        for (var i = 0; i < expected.length; i++) {
            if (bytes[offset + i] != expected[i]) return false;
        }
        return true;
    }
}
