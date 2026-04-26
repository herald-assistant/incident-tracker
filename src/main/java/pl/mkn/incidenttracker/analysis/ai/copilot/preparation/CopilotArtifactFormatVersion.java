package pl.mkn.incidenttracker.analysis.ai.copilot.preparation;

public enum CopilotArtifactFormatVersion {
    V2("copilot-artifacts-v2");

    private final String value;

    CopilotArtifactFormatVersion(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
