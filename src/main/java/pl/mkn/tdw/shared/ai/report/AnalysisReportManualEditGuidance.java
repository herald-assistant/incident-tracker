package pl.mkn.tdw.shared.ai.report;

public final class AnalysisReportManualEditGuidance {
    private AnalysisReportManualEditGuidance() {
    }

    public static String forFollowUp(AnalysisReport report) {
        if (report == null || report.manualEdit() == null) {
            return "";
        }
        return "\n\nOperator recznie zmienil zapisany raport (rewizja "
                + report.manualEdit().revision() + ", zmienione czesci: "
                + String.join(", ", report.manualEdit().changedParts()) + "). "
                + "Przed odpowiedzia odczytaj aktualny raport przez report_get_current. "
                + "Kazda zmieniona sekcje odczytaj w calosci przez report_read_section_chunk "
                + "od chunkIndex 0, kontynuuj wedlug nextChunkIndex az hasMore=false. "
                + "Zweryfikuj, ze digest jest ten sam we wszystkich fragmentach. "
                + "Dla markdownSummary uzyj tego samego toola z sectionId=markdownSummary. "
                + "Nie opieraj odpowiedzi na poprzedniej tresci raportu z historii rozmowy.";
    }
}
