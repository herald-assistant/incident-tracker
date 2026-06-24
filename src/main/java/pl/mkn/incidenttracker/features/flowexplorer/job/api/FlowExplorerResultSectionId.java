package pl.mkn.incidenttracker.features.flowexplorer.job.api;

public enum FlowExplorerResultSectionId {
    FUNCTIONAL_FLOW("Functional flow", FlowExplorerFocusArea.FUNCTIONAL_FLOW),
    VALIDATIONS("Validations", FlowExplorerFocusArea.VALIDATIONS),
    PERSISTENCE("Persistence", FlowExplorerFocusArea.PERSISTENCE),
    INTEGRATIONS("Integrations", FlowExplorerFocusArea.INTEGRATIONS);

    private final String title;
    private final FlowExplorerFocusArea focusArea;

    FlowExplorerResultSectionId(String title, FlowExplorerFocusArea focusArea) {
        this.title = title;
        this.focusArea = focusArea;
    }

    public String title() {
        return title;
    }

    public FlowExplorerFocusArea focusArea() {
        return focusArea;
    }
}
