package pl.mkn.tdw.aiplatform.copilot.tools.report;

import java.util.List;

public record CopilotReportManifest(
        String header,
        String subHeader,
        TextFingerprint markdownSummary,
        List<SectionManifest> sections,
        MetaManifest meta,
        Validation validation,
        String reportSha256
) {

    public CopilotReportManifest {
        sections = sections != null ? List.copyOf(sections) : List.of();
    }

    public record TextFingerprint(
            int characters,
            String sha256,
            String preview
    ) {
    }

    public record SectionManifest(
            String id,
            String title,
            Integer order,
            TextFingerprint markdown,
            MetaManifest meta
    ) {
    }

    public record MetaManifest(
            int references,
            int visibilityLimits,
            int openQuestions,
            int gaps,
            String confidence,
            int warnings,
            String sha256
    ) {
    }

    public record Validation(
            boolean complete,
            boolean headerPresent,
            boolean markdownSummaryPresent,
            List<String> presentSectionIds,
            List<String> missingAllowedSectionIds,
            List<String> unexpectedSectionIds,
            List<String> emptySectionIds,
            List<String> duplicateSectionIds
    ) {

        public Validation {
            presentSectionIds = copy(presentSectionIds);
            missingAllowedSectionIds = copy(missingAllowedSectionIds);
            unexpectedSectionIds = copy(unexpectedSectionIds);
            emptySectionIds = copy(emptySectionIds);
            duplicateSectionIds = copy(duplicateSectionIds);
        }

        private static List<String> copy(List<String> values) {
            return values != null ? List.copyOf(values) : List.of();
        }
    }
}
