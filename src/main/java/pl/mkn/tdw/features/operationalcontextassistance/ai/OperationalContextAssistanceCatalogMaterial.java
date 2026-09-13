package pl.mkn.tdw.features.operationalcontextassistance.ai;

import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Full runtime catalog and the packaged maintenance guidance used for one AI run. */
public record OperationalContextAssistanceCatalogMaterial(
        String contentDigest,
        OperationalContextCatalog catalog,
        Map<String, Map<String, Object>> documents,
        Map<String, String> guidance
) {

    public OperationalContextAssistanceCatalogMaterial {
        contentDigest = Objects.requireNonNull(contentDigest, "contentDigest");
        catalog = Objects.requireNonNull(catalog, "catalog");
        documents = Objects.requireNonNull(documents, "documents");
        guidance = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(guidance, "guidance")));
    }
}
