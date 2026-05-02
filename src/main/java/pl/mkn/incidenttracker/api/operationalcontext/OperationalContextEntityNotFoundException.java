package pl.mkn.incidenttracker.api.operationalcontext;

public class OperationalContextEntityNotFoundException extends RuntimeException {

    public OperationalContextEntityNotFoundException(String type, String id) {
        super("Operational context entity not found: " + type + "/" + id);
    }
}
