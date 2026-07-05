package pl.mkn.tdw.api.operationalcontext;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextProfiledReadModelDto;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static pl.mkn.tdw.api.operationalcontext.OperationalContextApiTestFixtures.port;
import static pl.mkn.tdw.api.operationalcontext.OperationalContextApiTestFixtures.typicalCatalog;

class OperationalContextReadModelContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldKeepReadModelPayloadsFocusedOnRelationsAndCodeSearch() {
        var service = new OperationalContextViewService(port(typicalCatalog()));

        var relations = service.entityRelationsReadModel("system", "crm-consent-service");
        var codeSearch = service.codeSearchReadModel("system", "crm-consent-service");

        assertEquals("operational-context.entity-relations", relations.contract());
        assertEquals(List.of(
                "contract",
                "contractVersion",
                "analysisTarget",
                "outgoingRelations",
                "incomingRelations",
                "neighbors",
                "validationFindings"
        ), fieldNames(relations));

        assertEquals("operational-context.code-search", codeSearch.contract());
        assertEquals(List.of(
                "contract",
                "contractVersion",
                "profile",
                "analysisTarget",
                "scopes",
                "repositories",
                "limitations",
                "validationFindings"
        ), fieldNames(codeSearch));
        assertTrue(codeSearch.scopes().stream()
                .anyMatch(scope -> scope.scope().id().equals("crm-consent-service-scope")));
        assertTrue(codeSearch.repositories().stream()
                .anyMatch(repository -> repository.repository().id().equals("crm-consent-repo")));
    }

    @Test
    void shouldExposeCompactProfilesWithoutLegacyReadModelNames() throws Exception {
        var service = new OperationalContextViewService(port(typicalCatalog()));

        var compactEntity = (OperationalContextProfiledReadModelDto) service.entity(
                "system",
                "crm-consent-service",
                "default"
        );
        var compactRelations = (OperationalContextProfiledReadModelDto) service.entityRelationsReadModel(
                "system",
                "crm-consent-service",
                "default"
        );
        var compactCodeSearch = (OperationalContextProfiledReadModelDto) service.codeSearchReadModel(
                "system",
                "crm-consent-service",
                "default"
        );

        assertEquals("default", compactEntity.profile());
        assertEquals("operational-context.entity-detail", compactEntity.contract());
        assertTrue(compactEntity.nextReads().stream().anyMatch(read -> read.rel().equals("relations")));
        assertTrue(compactEntity.nextReads().stream().anyMatch(read -> read.rel().equals("code-search")));

        assertEquals("default", compactRelations.profile());
        assertEquals("operational-context.entity-relations", compactRelations.contract());
        assertTrue(compactRelations.data().containsKey("neighbors"));

        assertEquals("default", compactCodeSearch.profile());
        assertEquals("operational-context.code-search", compactCodeSearch.contract());
        assertTrue(compactCodeSearch.data().containsKey("repositories"));

        assertEquals(
                List.of("profile=expanded", "relations", "code-search"),
                compactEntity.availableExpansions()
        );
        assertTrue(objectMapper.writeValueAsString(List.of(compactEntity, compactRelations, compactCodeSearch))
                .contains("operational-context.code-search"));
    }

    @Test
    void shouldKeepNoProfileReadModelsEquivalentToExpandedProfile() {
        var service = new OperationalContextViewService(port(typicalCatalog()));

        assertEquals(
                objectMapper.valueToTree(service.entityRelationsReadModel("system", "crm-consent-service")),
                objectMapper.valueToTree(service.entityRelationsReadModel("system", "crm-consent-service", "expanded"))
        );
        assertEquals(
                objectMapper.valueToTree(service.codeSearchReadModel("system", "crm-consent-service")),
                objectMapper.valueToTree(service.codeSearchReadModel("system", "crm-consent-service", "expanded"))
        );
    }

    private List<String> fieldNames(Record record) {
        return Arrays.stream(record.getClass().getRecordComponents())
                .map(component -> component.getName())
                .toList();
    }
}
