package pl.mkn.tdw.aiplatform.copilot.runtime;

public record CopilotRuntimeVersionInfo(
        String sdkVersion,
        String cliVersion,
        int protocolVersion,
        String minimumCliVersion,
        boolean compatible
) {
}
