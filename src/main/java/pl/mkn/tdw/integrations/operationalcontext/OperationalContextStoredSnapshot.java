package pl.mkn.tdw.integrations.operationalcontext;

import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.ValidationFinding;

import java.time.Instant;
import java.util.List;
import java.util.Map;

final class OperationalContextStoredSnapshot {

    private final OperationalContextRawDocuments rawDocuments;
    private final Map<String, Map<String, Object>> decodedDocuments;
    private final List<ValidationFinding> validationFindings;
    private final Instant loadedAt;
    private final OperationalContextSnapshot readSnapshot;

    OperationalContextStoredSnapshot(
            OperationalContextRawDocuments rawDocuments,
            Map<String, Map<String, Object>> decodedDocuments,
            OperationalContextCatalog catalog
    ) {
        this(rawDocuments, decodedDocuments, catalog, List.of(), Instant.now());
    }

    OperationalContextStoredSnapshot(
            OperationalContextRawDocuments rawDocuments,
            Map<String, Map<String, Object>> decodedDocuments,
            OperationalContextCatalog catalog,
            List<ValidationFinding> validationFindings,
            Instant loadedAt
    ) {
        this.rawDocuments = rawDocuments;
        this.decodedDocuments = OperationalContextImmutableValues.copyDocuments(decodedDocuments);
        this.validationFindings = validationFindings == null ? List.of() : List.copyOf(validationFindings);
        this.loadedAt = loadedAt != null ? loadedAt : Instant.now();
        this.readSnapshot = new OperationalContextSnapshot(
                rawDocuments.contentDigest(),
                rawDocuments.logicalSource(),
                catalog
        );
    }

    OperationalContextRawDocuments rawDocuments() {
        return rawDocuments;
    }

    Map<String, Map<String, Object>> decodedDocuments() {
        return decodedDocuments;
    }

    List<ValidationFinding> validationFindings() {
        return validationFindings;
    }

    Instant loadedAt() {
        return loadedAt;
    }

    OperationalContextSnapshot readSnapshot() {
        return readSnapshot;
    }
}
