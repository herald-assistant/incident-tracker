package pl.mkn.tdw.integrations.operationalcontext.service;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextGlossaryTerm;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OperationalContextCatalogSearchTest {

    @Test
    void removingMirroredReferencesPreservesSearchScoresAndOwnership() {
        var one = OperationalContextNavigationTestCreator.catalog(false, 1);
        var two = OperationalContextNavigationTestCreator.catalog(true, 1);
        var search = OperationalContextNavigationTestCreator.search();
        for (var query : List.of("crm-contacts", "crm-context-0", "crm-contacts-repo")) {
            var first = search.find(one, query).stream().map(match -> match.type() + ":" + match.id() + ":" + match.score()).toList();
            var second = search.find(two, query).stream().map(match -> match.type() + ":" + match.id() + ":" + match.score()).toList();
            assertEquals(first, second);
        }
        var request = new pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextOwnershipRequest(
                null, List.of(), List.of("crm-context-0"), List.of(), List.of(), null);
        var resolver = new OperationalContextOwnershipResolver();
        assertEquals(resolver.resolve(one, request), resolver.resolve(two, request));
        assertEquals("CRM Contacts Team", resolver.resolve(one, request).primaryOwners().get(0).ownerLabel());
        var code = new OperationalContextCodeSearchReadModelBuilder();
        assertEquals(code.buildForEntity(one, "system", "crm-contacts").scopes(),
                code.buildForEntity(two, "system", "crm-contacts").scopes());
    }

    @Test
    void shouldFindPolishCatalogTermWithoutDiacritics() {
        var term = new OperationalContextGlossaryTerm("customer-request", "Żądanie klienta", null,
                "Prośba o zmianę danych klienta.", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        var catalog = new OperationalContextCatalog(List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(term), List.of(), List.of(), List.of());

        var match = OperationalContextNavigationTestCreator.search().find(catalog, "zadanie klienta").get(0);

        assertEquals("glossaryTerm", match.type());
        assertEquals("customer-request", match.id());
        assertEquals(100, match.score());
    }
}
