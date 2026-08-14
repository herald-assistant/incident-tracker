package pl.mkn.tdw.aiplatform.copilot.runtime;

public class CopilotSkillCatalogException extends RuntimeException {

    public enum Code {
        SKILL_NOT_FOUND,
        INVALID_CONTENT,
        NAME_MISMATCH,
        DEFAULT_UNAVAILABLE,
        STORAGE_UNAVAILABLE
    }

    private final Code code;

    public CopilotSkillCatalogException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public CopilotSkillCatalogException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
