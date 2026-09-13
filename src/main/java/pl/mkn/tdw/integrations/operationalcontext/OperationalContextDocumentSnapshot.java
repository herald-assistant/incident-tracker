package pl.mkn.tdw.integrations.operationalcontext;

import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;

import java.util.Map;
import java.util.Objects;

/** A complete decoded document set and read model captured from one published catalog snapshot. */
public record OperationalContextDocumentSnapshot(
        String contentDigest,
        String source,
        OperationalContextCatalog catalog,
        Map<String, Map<String, Object>> documents
) {

    public OperationalContextDocumentSnapshot {
        contentDigest = Objects.requireNonNull(contentDigest, "contentDigest");
        source = Objects.requireNonNull(source, "source");
        catalog = Objects.requireNonNull(catalog, "catalog");
        documents = OperationalContextImmutableValues.copyDocuments(documents);
    }
}
