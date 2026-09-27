package pl.mkn.tdw.integrations.operationalcontext.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.DefaultResourceLoader;
import java.util.List;

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

    public static OperationalContextCatalogValidationService withEmptyBaseline() {
        return new OperationalContextCatalogValidationService(() ->
                new OperationalContextValidationBaseline(1,
                        OperationalContextCatalogValidationService.FINGERPRINT_ALGORITHM, List.of()));
    }
}
