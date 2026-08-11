package pl.mkn.tdw.integrations.operationalcontext;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.DefaultResourceLoader;

public final class OperationalContextValidationTestCreator {

    private OperationalContextValidationTestCreator() {
    }

    public static OperationalContextCatalogValidationService create() {
        return new OperationalContextCatalogValidationService(
                new OperationalContextValidationBaselineLoader(
                        new ObjectMapper(),
                        new DefaultResourceLoader()
                )
        );
    }
}
