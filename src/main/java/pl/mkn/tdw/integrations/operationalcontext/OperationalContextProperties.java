package pl.mkn.tdw.integrations.operationalcontext;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "analysis.operational-context")
public class OperationalContextProperties {

    private boolean enabled;
    private String resourceRoot = "operational-context";
    private String storageDirectory = "tdw-data/operational-context";
    private int maxItemsPerType = 2;
    private int maxGlossaryTerms = 3;
    private int maxHandoffRules = 2;

    public Path resolvedStorageDirectory() {
        var path = Path.of(storageDirectory).toAbsolutePath().normalize();
        if (path.getParent() == null) {
            throw new OperationalContextStoreException(
                    OperationalContextStoreException.Code.INVALID_STORAGE_PATH,
                    "Operational context storage directory cannot be a filesystem root"
            );
        }
        return path;
    }

}
