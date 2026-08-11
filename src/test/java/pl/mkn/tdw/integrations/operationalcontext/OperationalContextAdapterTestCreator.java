package pl.mkn.tdw.integrations.operationalcontext;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.core.io.DefaultResourceLoader;

public final class OperationalContextAdapterTestCreator {

    private OperationalContextAdapterTestCreator() {
    }

    public static OperationalContextAdapter create(OperationalContextProperties properties) {
        try {
            properties.setStorageDirectory(java.nio.file.Files.createTempDirectory("crm-operational-context-").resolve("operational-context").toString());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot create anonymous CRM operational context directory", exception);
        }
        var classpathSource = new ClasspathOperationalContextDocumentSource(properties);
        var codec = new OperationalContextCatalogCodec();
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var validationService = new OperationalContextCatalogValidationService(
                new OperationalContextValidationBaselineLoader(mapper, new DefaultResourceLoader())
        );
        var localStore = new LocalOperationalContextStore(
                properties,
                classpathSource,
                codec,
                new OperationalContextAtomicMover(),
                validationService
        );
        var snapshotStore = new DefaultOperationalContextSnapshotStore(localStore);
        return new OperationalContextAdapter(
                snapshotStore,
                new OperationalContextCatalogQueryService()
        );
    }
}
