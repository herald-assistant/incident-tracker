package pl.mkn.tdw.integrations.operationalcontext;

import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

record OperationalContextRawDocuments(
        String logicalSource,
        String contentDigest,
        Map<String, String> contents
) {

    OperationalContextRawDocuments {
        logicalSource = StringUtils.hasText(logicalSource) ? logicalSource : "unknown";
        contents = contents == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(contents));
        contentDigest = StringUtils.hasText(contentDigest)
                ? contentDigest
                : logicalSource + "-" + contentDigest(contents);
    }

    OperationalContextRawDocuments(String logicalSource, Map<String, String> contents) {
        this(logicalSource, null, contents);
    }

    String content(String logicalDocument) {
        return contents.getOrDefault(logicalDocument, "");
    }

    static String contentDigest(Map<String, String> contents) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            new TreeMap<>(contents).forEach((name, content) -> {
                digest.update(name.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(content.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
