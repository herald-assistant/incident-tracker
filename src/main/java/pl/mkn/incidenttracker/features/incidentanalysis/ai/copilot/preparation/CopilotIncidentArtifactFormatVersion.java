package pl.mkn.incidenttracker.features.incidentanalysis.ai.copilot.preparation;

public enum CopilotIncidentArtifactFormatVersion {
    V2("copilot-artifacts-v2");

    private final String value;

    CopilotIncidentArtifactFormatVersion(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
