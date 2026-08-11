package pl.mkn.tdw.integrations.operationalcontext;

import java.util.List;

public class OperationalContextCatalogMaintenanceException extends RuntimeException {

    public enum Code {
        ENTITY_TYPE_UNSUPPORTED,
        ENTITY_NOT_FOUND,
        DUPLICATE_ID,
        ID_MISMATCH,
        VALIDATION_FAILED,
        DELETE_RESTRICTED
    }

    private final Code code;
    private final List<OperationalContextCatalogFieldError> fieldErrors;

    public OperationalContextCatalogMaintenanceException(
            Code code,
            String message,
            List<OperationalContextCatalogFieldError> fieldErrors
    ) {
        super(message);
        this.code = code;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public Code code() {
        return code;
    }

    public List<OperationalContextCatalogFieldError> fieldErrors() {
        return fieldErrors;
    }

    static OperationalContextCatalogMaintenanceException invalidType(String value) {
        return new OperationalContextCatalogMaintenanceException(
                Code.ENTITY_TYPE_UNSUPPORTED,
                "Unsupported operational context entity type: " + String.valueOf(value),
                List.of(new OperationalContextCatalogFieldError("/type", "Unsupported entity type"))
        );
    }

    static OperationalContextCatalogMaintenanceException notFound(OperationalContextCatalogEntityType type, String id) {
        return new OperationalContextCatalogMaintenanceException(
                Code.ENTITY_NOT_FOUND,
                "Operational context " + type.externalName() + " not found: " + id,
                List.of()
        );
    }

    public static OperationalContextCatalogMaintenanceException validation(
            String message,
            List<OperationalContextCatalogFieldError> fieldErrors
    ) {
        return new OperationalContextCatalogMaintenanceException(Code.VALIDATION_FAILED, message, fieldErrors);
    }
}
