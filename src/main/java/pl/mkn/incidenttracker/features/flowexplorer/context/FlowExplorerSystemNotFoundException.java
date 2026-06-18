package pl.mkn.incidenttracker.features.flowexplorer.context;

public class FlowExplorerSystemNotFoundException extends RuntimeException {

    public FlowExplorerSystemNotFoundException(String systemId) {
        super("Flow Explorer system not found: " + systemId);
    }
}
