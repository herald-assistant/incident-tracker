package pl.mkn.tdw.integrations.operationalcontext;

public class OperationalContextStoreException extends IllegalStateException {

    public enum Code {
        INVALID_STORAGE_PATH,
        LOCAL_COPY_UNAVAILABLE,
        CORRUPT_STORE,
        INVALID_CANDIDATE
    }

    private final Code code;

    public OperationalContextStoreException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public OperationalContextStoreException(Code code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
