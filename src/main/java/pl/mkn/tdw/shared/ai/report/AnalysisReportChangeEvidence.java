package pl.mkn.tdw.shared.ai.report;

import pl.mkn.tdw.shared.evidence.AnalysisEvidenceAttribute;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceItem;
import pl.mkn.tdw.shared.evidence.AnalysisEvidenceSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** A compact, message-owned before/after snapshot of accepted report edits. */
public final class AnalysisReportChangeEvidence {
    public static final String PROVIDER = "report";
    public static final String CATEGORY = "report-change";

    private AnalysisReportChangeEvidence() {
    }

    public static AnalysisEvidenceSection between(AnalysisReport before, AnalysisReport after) {
        if (before == null || after == null || Objects.equals(before, after)) {
            return new AnalysisEvidenceSection(PROVIDER, CATEGORY, List.of());
        }
        var changes = new ArrayList<AnalysisEvidenceItem>();
        add(changes, "Nagłówek", before.header(), after.header());
        add(changes, "Podtytuł", before.subHeader(), after.subHeader());
        add(changes, "Podsumowanie", before.markdownSummary(), after.markdownSummary());

        Map<String, AnalysisReportSection> oldSections = new LinkedHashMap<>();
        before.sections().forEach(section -> oldSections.put(section.id(), section));
        Map<String, AnalysisReportSection> newSections = new LinkedHashMap<>();
        after.sections().forEach(section -> newSections.put(section.id(), section));
        for (var entry : newSections.entrySet()) {
            var oldSection = oldSections.remove(entry.getKey());
            var newSection = entry.getValue();
            var title = newSection.title() != null && !newSection.title().isBlank()
                    ? newSection.title() : "Sekcja " + newSection.id();
            add(changes, title, oldSection != null ? oldSection.markdown() : "", newSection.markdown());
            add(changes, title + " — tytuł", oldSection != null ? oldSection.title() : "", newSection.title());
            add(changes, title + " — kolejność",
                    oldSection != null ? formatOrder(oldSection.order()) : "",
                    formatOrder(newSection.order()));
            add(changes, title + " — informacje dodatkowe",
                    oldSection != null ? formatMeta(oldSection.meta()) : "", formatMeta(newSection.meta()));
        }
        for (var removed : oldSections.values()) {
            add(changes, removed.title() != null ? removed.title() : "Sekcja " + removed.id(),
                    removed.markdown(), "");
        }
        add(changes, "Informacje dodatkowe raportu", formatMeta(before.meta()), formatMeta(after.meta()));
        return new AnalysisEvidenceSection(PROVIDER, CATEGORY, changes);
    }

    public static List<AnalysisEvidenceSection> append(
            List<AnalysisEvidenceSection> evidence, AnalysisReport before, AnalysisReport after
    ) {
        var change = between(before, after);
        if (!change.hasItems()) {
            return evidence;
        }
        var updated = new ArrayList<>(evidence);
        updated.add(change);
        return List.copyOf(updated);
    }

    private static void add(List<AnalysisEvidenceItem> changes, String title, String before, String after) {
        if (Objects.equals(before, after)) {
            return;
        }
        changes.add(new AnalysisEvidenceItem(title, List.of(
                new AnalysisEvidenceAttribute("before", before != null ? before : ""),
                new AnalysisEvidenceAttribute("after", after != null ? after : "")
        )));
    }

    private static String formatMeta(AnalysisReportMeta meta) {
        if (meta == null) {
            return "";
        }
        var lines = new ArrayList<String>();
        for (var reference : meta.references()) {
            var parts = new ArrayList<String>();
            addPart(parts, "Nazwa", reference.label());
            addPart(parts, "Rodzaj", reference.type());
            addPart(parts, "Cel", reference.target());
            addPart(parts, "Opis", reference.description());
            lines.add("Źródło: " + String.join(" · ", parts));
        }
        meta.visibilityLimits().forEach(value -> lines.add("Ograniczenie widoczności: " + value));
        meta.openQuestions().forEach(value -> lines.add("Otwarte pytanie: " + value));
        meta.gaps().forEach(value -> lines.add("Luka: " + value));
        if (meta.confidence() != null && !meta.confidence().isBlank()) {
            lines.add("Pewność: " + meta.confidence());
        }
        meta.warnings().forEach(value -> lines.add("Uwaga: " + value));
        return String.join("\n", lines);
    }

    private static void addPart(List<String> parts, String label, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(label + ": " + value);
        }
    }

    private static String formatOrder(Integer order) {
        return order != null ? order.toString() : "";
    }
}
