package pl.mkn.tdw.aiplatform.copilot.runtime;

public record CopilotSessionTarget(
        Type type,
        String sessionId
) {

    public CopilotSessionTarget {
        if (type == null) {
            throw new IllegalArgumentException("session target type must not be null");
        }
        sessionId = hasText(sessionId) ? sessionId.trim() : null;
        if (type == Type.EXISTING && !hasText(sessionId)) {
            throw new IllegalArgumentException("existing session target requires sessionId");
        }
    }

    public static CopilotSessionTarget newSession() {
        return new CopilotSessionTarget(Type.NEW, null);
    }

    public static CopilotSessionTarget existing(String sessionId) {
        return new CopilotSessionTarget(Type.EXISTING, sessionId);
    }

    public boolean existing() {
        return type == Type.EXISTING;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public enum Type {
        NEW,
        EXISTING
    }
}
