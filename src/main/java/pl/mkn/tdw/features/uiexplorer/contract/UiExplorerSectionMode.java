package pl.mkn.tdw.features.uiexplorer.contract;

public enum UiExplorerSectionMode {
    OFF("Pominieta", "The section is not included in the analysis."),
    COMPACT("Skrocona", "The section contains the most important business-readable facts and limits."),
    DEEP("Poglebiona", "The section receives deeper source grounding and structured findings.");

    private final String label;
    private final String description;

    UiExplorerSectionMode(String label, String description) {
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

