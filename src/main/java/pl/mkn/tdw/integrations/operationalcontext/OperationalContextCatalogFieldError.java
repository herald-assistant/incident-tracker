package pl.mkn.tdw.integrations.operationalcontext;

import org.springframework.util.StringUtils;

public record OperationalContextCatalogFieldError(String pointer, String message) {

    public OperationalContextCatalogFieldError {
        pointer = StringUtils.hasText(pointer) ? pointer : "/payload";
        message = StringUtils.hasText(message) ? message : "Invalid value";
    }
}
