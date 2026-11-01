package pl.mkn.tdw.features.releasesummary.job;

import pl.mkn.tdw.features.releasesummary.contract.ReleaseSummaryEvidence;
import pl.mkn.tdw.features.releasesummary.contract.ReleaseSummaryScope;
import pl.mkn.tdw.shared.evidence.*;
import java.util.*;

/** Operator projection of collected sources; never added back to the model's input. */
public final class ReleaseSummaryProgressEvidence {
    public static final AnalysisEvidenceReference FILES = new AnalysisEvidenceReference("GitLab", "Zmiany Liquibase per plik");
    public static final AnalysisEvidenceReference GAPS = new AnalysisEvidenceReference("GitLab", "Luki zbierania źródeł");
    private ReleaseSummaryProgressEvidence() {}

    public static List<AnalysisEvidenceSection> sections(ReleaseSummaryScope scope, ReleaseSummaryEvidence evidence) {
        var sources = new HashMap<String, ReleaseSummaryEvidence.Source>();
        evidence.sources().forEach(source -> sources.put(source.sourceRef(), source));
        var files = new LinkedHashMap<FileKey, ReleaseSummaryEvidence.Unit>();
        evidence.units().forEach(unit -> files.putIfAbsent(new FileKey(unit.repositoryId(), unit.path()), unit));
        var items = new ArrayList<AnalysisEvidenceItem>();
        for (var unit : files.values()) {
            var attributes = new ArrayList<AnalysisEvidenceAttribute>();
            var before = sources.get(unit.baselineSource());
            var after = sources.get(unit.targetSource());
            attributes.add(attribute("Rodzaj zmiany", changeLabel(unit.changeType())));
            scope.repositories().stream().filter(repo -> repo.repositoryId().equals(unit.repositoryId())).findFirst().ifPresent(repo -> {
                attributes.add(attribute("Punkt wejścia", repo.baselineRef() + " · " + repo.baselineCommit()));
                attributes.add(attribute("Stan docelowy", repo.targetRef() + " · " + repo.targetCommit()));
            });
            attributes.add(attribute("Odczyt — punkt wejścia", sourceStatus(unit.baselineSource(), before)));
            attributes.add(attribute("Odczyt — stan docelowy", sourceStatus(unit.targetSource(), after)));
            // An absent side is empty only when the manifest declares no source for that side.
            // An unreadable source must not masquerade as a removed file.
            if ((unit.baselineSource() == null || before != null) && (unit.targetSource() == null || after != null)) {
                attributes.add(attribute("beforeContent", before != null ? before.content() : ""));
                attributes.add(attribute("afterContent", after != null ? after.content() : ""));
                if (before != null && after != null && before.content().equals(after.content())) {
                    attributes.add(attribute("Uwagi", "Treść pliku bez zmian. Zmieniło się dołączenie lub zależność migracji."));
                }
            }
            items.add(new AnalysisEvidenceItem(unit.repositoryId() + " / " + unit.path(), attributes));
        }
        var sections = new ArrayList<AnalysisEvidenceSection>();
        sections.add(new AnalysisEvidenceSection(FILES.provider(), FILES.category(), items));
        if (!evidence.gaps().isEmpty()) {
            sections.add(new AnalysisEvidenceSection(GAPS.provider(), GAPS.category(), evidence.gaps().stream().map(gap ->
                    new AnalysisEvidenceItem(gap.repositoryId() + " / " + gap.path(), List.of(
                            attribute("Strona", gap.side()), attribute("Ograniczenie", gap.detail()),
                            attribute("Blokuje analizę", gap.blocking() ? "Tak" : "Nie")))).toList()));
        }
        return List.copyOf(sections);
    }

    private static String sourceStatus(String reference, ReleaseSummaryEvidence.Source source) {
        if (reference == null) return "Brak pliku w zakresie Liquibase tej wersji.";
        if (source == null) return "Brak zweryfikowanego odczytu — treść niedostępna.";
        return source.content().isEmpty() ? "Zweryfikowany pusty plik." : "Zweryfikowana pełna treść.";
    }
    private static String changeLabel(String type) {
        return switch (type) {
            case "ADDED" -> "Dodanie do zakresu Liquibase";
            case "REMOVED" -> "Usunięcie z zakresu Liquibase (nie oznacza wykonania DROP w bazie)";
            case "MODIFIED" -> "Zmiana treści pliku";
            case "INCLUSION_CHANGED" -> "Zmiana dołączenia migracji";
            case "DEPENDENCY_CHANGED" -> "Zmiana zależności migracji";
            default -> type;
        };
    }
    private static AnalysisEvidenceAttribute attribute(String name, String value) { return new AnalysisEvidenceAttribute(name, value); }
    private record FileKey(String repositoryId, String path) {}
}
