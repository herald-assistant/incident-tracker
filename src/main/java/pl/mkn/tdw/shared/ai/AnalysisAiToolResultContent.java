package pl.mkn.tdw.shared.ai;

public record AnalysisAiToolResultContent(
        Format format,
        Object value,
        boolean truncated,
        int originalCharacters,
        int retainedCharacters,
        int omittedEntries,
        int truncatedStrings
) {

    public enum Format {
        JSON,
        TEXT
    }
}
