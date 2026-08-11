package pl.mkn.tdw.integrations.operationalcontext;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OperationalContextStoredSnapshotTest {

    @Test
    void shouldKeepDecodedDocumentsAndCatalogPayloadDeeplyImmutable() {
        var rawDocuments = documents(systemDocument("crm-customer-service"));
        var decoded = new OperationalContextCatalogCodec().decode(rawDocuments);
        var stored = new OperationalContextStoredSnapshot(
                rawDocuments,
                decoded.decodedDocuments(),
                decoded.catalog()
        );

        var decodedSystem = firstSystem(stored.decodedDocuments());
        var decodedOwnership = map(decodedSystem.get("ownership"));
        var decodedAliases = list(decodedSystem.get("aliases"));
        var catalogPayload = stored.readSnapshot().catalog().systems().get(0).payload();
        var catalogOwnership = map(catalogPayload.get("ownership"));
        var catalogAliases = list(catalogPayload.get("aliases"));

        assertThrows(UnsupportedOperationException.class, () -> decodedSystem.put("summary", "changed"));
        assertThrows(UnsupportedOperationException.class, () -> decodedOwnership.put("ownerLabel", "changed"));
        assertThrows(UnsupportedOperationException.class, () -> decodedAliases.add("changed"));
        assertThrows(UnsupportedOperationException.class, () -> catalogPayload.put("summary", "changed"));
        assertThrows(UnsupportedOperationException.class, () -> catalogOwnership.put("ownerLabel", "changed"));
        assertThrows(UnsupportedOperationException.class, () -> catalogAliases.add("changed"));
    }

    @Test
    void shouldExposeOnlyReadFacingMetadataAndCatalog() {
        var rawDocuments = documents(systemDocument("crm-customer-service"));
        var decoded = new OperationalContextCatalogCodec().decode(rawDocuments);
        var stored = new OperationalContextStoredSnapshot(
                rawDocuments,
                decoded.decodedDocuments(),
                decoded.catalog()
        );

        var snapshot = stored.readSnapshot();
        var componentNames = Arrays.stream(OperationalContextSnapshot.class.getRecordComponents())
                .map(component -> component.getName())
                .collect(Collectors.toSet());

        assertEquals(Set.of("contentDigest", "source", "catalog"), componentNames);
        assertEquals("classpath", snapshot.source());
        assertSame(decoded.catalog(), snapshot.catalog());
    }

    @Test
    void shouldDeriveStableContentDigestFromAllLogicalDocuments() {
        var first = documents(systemDocument("crm-customer-service"));
        var same = documents(systemDocument("crm-customer-service"));
        var changed = documents(systemDocument("crm-customer-profile-service"));

        assertEquals(first.contentDigest(), same.contentDigest());
        assertNotEquals(first.contentDigest(), changed.contentDigest());
    }

    private static OperationalContextRawDocuments documents(String systems) {
        return new OperationalContextRawDocuments(
                "classpath",
                Map.of("systems.yml", systems)
        );
    }

    private static String systemDocument(String systemId) {
        return """
                schemaVersion: 1
                catalogKind: operational-context-systems
                systems:
                  - id: %s
                    name: CRM Customer Service
                    aliases:
                      - crm-customer
                    ownership:
                      ownerTeamIds:
                        - crm-customer-operations
                      ownershipStatus: explicit
                """.formatted(systemId);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> firstSystem(Map<String, Map<String, Object>> documents) {
        var systems = (List<Map<String, Object>>) documents.get("systems.yml").get("systems");
        return systems.get(0);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object value) {
        return (List<Object>) value;
    }
}
