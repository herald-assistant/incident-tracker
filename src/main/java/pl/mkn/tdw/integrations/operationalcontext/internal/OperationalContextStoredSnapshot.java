package pl.mkn.tdw.integrations.operationalcontext.internal;

import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextImmutableValues;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextSnapshot;

import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextRelationIndex.ValidationFinding;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class OperationalContextStoredSnapshot {

    private final OperationalContextRawDocuments rawDocuments;
    private final Map<String, Map<String, Object>> decodedDocuments;
    private final List<ValidationFinding> validationFindings;
    private final Instant loadedAt;
    private final OperationalContextSnapshot readSnapshot;

    public OperationalContextStoredSnapshot(
            OperationalContextRawDocuments rawDocuments,
            Map<String, Map<String, Object>> decodedDocuments,
            OperationalContextCatalog catalog
    ) {
        this(rawDocuments, decodedDocuments, catalog, List.of(), Instant.now());
    }

    public OperationalContextStoredSnapshot(
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

    public OperationalContextRawDocuments rawDocuments() {
        return rawDocuments;
    }

    public Map<String, Map<String, Object>> decodedDocuments() {
        return decodedDocuments;
    }

    public List<ValidationFinding> validationFindings() {
        return validationFindings;
    }

    public Instant loadedAt() {
        return loadedAt;
    }

    public OperationalContextSnapshot readSnapshot() {
        return readSnapshot;
    }
}
