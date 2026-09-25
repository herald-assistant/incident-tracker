package pl.mkn.tdw.shared.ai.report;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class AnalysisReportDigest {
    private AnalysisReportDigest() {
    }

    static String sha256(AnalysisReport report) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            add(digest, report.reportId());
            add(digest, report.header());
            add(digest, report.subHeader());
            add(digest, report.markdownSummary());
            add(digest, report.meta().toString());
            if (report.manualEdit() != null) {
                add(digest, Long.toString(report.manualEdit().revision()));
                add(digest, report.manualEdit().editedAt() != null
                        ? report.manualEdit().editedAt().toString() : null);
                for (var part : report.manualEdit().changedParts()) add(digest, part);
            }
            for (var section : report.sections()) {
                add(digest, section.id());
                add(digest, section.title());
                add(digest, section.order() != null ? section.order().toString() : null);
                add(digest, section.markdown());
                add(digest, section.meta().toString());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void add(MessageDigest digest, String value) {
        var bytes = (value != null ? value : "").getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }
}
