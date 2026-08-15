package pl.mkn.tdw.features.uiexplorer.contract;

public enum UiExplorerSectionId {
    OVERVIEW("Cel i kontekst widoku", "Purpose, users and business-process context of the screen."),
    NAVIGATION_AND_ACCESS("Nawigacja i dostep", "Entry paths, route parameters, guards and visible access conditions."),
    SCREEN_STRUCTURE("Struktura widoku", "Forms, tables, summaries, messages and custom visual elements."),
    ACTIONS_AND_OUTCOMES("Akcje i rezultaty", "User actions, availability conditions, outcomes and transitions."),
    FORMS_AND_RULES("Formularze i reguly", "Fields, validation, calculations and dynamic form behavior."),
    DATA_AND_SERVICES("Dane i uslugi", "Presented and modified data, sources, refresh and backend operations."),
    STATE_AND_SYNCHRONIZATION("Stan i synchronizacja", "Local and shared state plus refresh and recalculation triggers."),
    VARIANTS_AND_FAILURES("Warianty i sytuacje wyjatkowe", "Role, data, process-status, empty and failure variants.");

    private final String label;
    private final String description;

    UiExplorerSectionId(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }
}

