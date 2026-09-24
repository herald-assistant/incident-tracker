package pl.mkn.tdw.aiplatform.copilot.tools.report;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.shared.ai.report.AnalysisReport;
import pl.mkn.tdw.shared.ai.report.AnalysisReportMeta;
import pl.mkn.tdw.shared.ai.report.AnalysisReportSection;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class CopilotReportManifestFactory {

    private static final int PREVIEW_CHARACTERS = 240;

    private CopilotReportManifestFactory() {
    }

    static CopilotReportManifest create(AnalysisReport report, List<String> allowedSectionIds) {
        var allowedIds = allowedSectionIds != null ? List.copyOf(allowedSectionIds) : List.<String>of();
        var sections = safeSections(report).stream()
                .map(CopilotReportManifestFactory::sectionManifest)
                .toList();
        var validation = validation(report, allowedIds);
        return new CopilotReportManifest(
                report != null ? report.header() : null,
                report != null ? report.subHeader() : null,
                fingerprint(report != null ? report.markdownSummary() : null),
                sections,
                metaManifest(report != null ? report.meta() : null),
                validation,
                reportDigest(report)
        );
    }

    private static CopilotReportManifest.SectionManifest sectionManifest(AnalysisReportSection section) {
        if (section == null) {
            return new CopilotReportManifest.SectionManifest(null, null, null, fingerprint(null), metaManifest(null));
        }
        return new CopilotReportManifest.SectionManifest(
                section.id(),
                section.title(),
                section.order(),
                fingerprint(section.markdown()),
                metaManifest(section.meta())
        );
    }

    private static CopilotReportManifest.MetaManifest metaManifest(AnalysisReportMeta meta) {
        var effective = meta != null ? meta : AnalysisReportMeta.empty();
        return new CopilotReportManifest.MetaManifest(
                safe(effective.references()).size(),
                safe(effective.visibilityLimits()).size(),
                safe(effective.openQuestions()).size(),
                safe(effective.gaps()).size(),
                effective.confidence(),
                safe(effective.warnings()).size(),
                metaDigest(effective)
        );
    }

    private static CopilotReportManifest.Validation validation(AnalysisReport report, List<String> allowedIds) {
        var presentIds = new ArrayList<String>();
        var emptyIds = new ArrayList<String>();
        for (var section : safeSections(report)) {
            if (section == null || !StringUtils.hasText(section.id())) {
                continue;
            }
            var id = section.id().trim();
            presentIds.add(id);
            if (!StringUtils.hasText(section.markdown())) {
                emptyIds.add(id);
            }
        }

        var presentSet = new LinkedHashSet<>(presentIds);
        var duplicateIds = presentIds.stream()
                .filter(id -> java.util.Collections.frequency(presentIds, id) > 1)
                .distinct()
                .toList();
        var allowedSet = new LinkedHashSet<>(allowedIds);
        var missingIds = allowedSet.stream().filter(id -> !presentSet.contains(id)).toList();
        var unexpectedIds = presentSet.stream().filter(id -> !allowedSet.contains(id)).toList();
        var headerPresent = report != null && StringUtils.hasText(report.header());
        var summaryPresent = report != null && StringUtils.hasText(report.markdownSummary());
        var complete = headerPresent
                && missingIds.isEmpty()
                && unexpectedIds.isEmpty()
                && emptyIds.isEmpty()
                && duplicateIds.isEmpty();
        return new CopilotReportManifest.Validation(
                complete,
                headerPresent,
                summaryPresent,
                presentIds,
                missingIds,
                unexpectedIds,
                emptyIds,
                duplicateIds
        );
    }

    private static CopilotReportManifest.TextFingerprint fingerprint(String value) {
        var effective = value != null ? value : "";
        var preview = effective.length() <= PREVIEW_CHARACTERS
                ? effective
                : effective.substring(0, PREVIEW_CHARACTERS) + "...";
        return new CopilotReportManifest.TextFingerprint(effective.length(), digestStrings(List.of(effective)), preview);
    }

    static String markdownSha256(String markdown) {
        return fingerprint(markdown).sha256();
    }

    private static String reportDigest(AnalysisReport report) {
        if (report == null) {
            return digestStrings(List.of());
        }
        var values = new ArrayList<String>();
        values.add(report.reportId());
        values.add(report.header());
        values.add(report.subHeader());
        values.add(report.markdownSummary());
        for (var section : safeSections(report)) {
            if (section == null) {
                values.add(null);
                continue;
            }
            values.add(section.id());
            values.add(section.title());
            values.add(section.order() != null ? section.order().toString() : null);
            values.add(section.markdown());
            values.add(metaDigest(section.meta()));
        }
        values.add(metaDigest(report.meta()));
        return digestStrings(values);
    }

    private static String metaDigest(AnalysisReportMeta meta) {
        var effective = meta != null ? meta : AnalysisReportMeta.empty();
        var values = new ArrayList<String>();
        for (var reference : safe(effective.references())) {
            if (reference == null) {
                values.add(null);
                continue;
            }
            values.add(reference.type());
            values.add(reference.label());
            values.add(reference.target());
            values.add(reference.description());
        }
        values.addAll(safe(effective.visibilityLimits()));
        values.addAll(safe(effective.openQuestions()));
        values.addAll(safe(effective.gaps()));
        values.add(effective.confidence());
        values.addAll(safe(effective.warnings()));
        return digestStrings(values);
    }

    private static String digestStrings(List<String> values) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (var value : values) {
                if (value == null) {
                    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
                    continue;
                }
                var bytes = value.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static List<AnalysisReportSection> safeSections(AnalysisReport report) {
        return report != null && report.sections() != null ? report.sections() : List.of();
    }

    private static <T> List<T> safe(List<T> values) {
        return values != null ? values : List.of();
    }
}
