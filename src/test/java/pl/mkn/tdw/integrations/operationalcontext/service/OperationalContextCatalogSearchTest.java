package pl.mkn.tdw.integrations.operationalcontext.service;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextGlossaryTerm;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OperationalContextCatalogSearchTest {

    @Test
    void shouldFindPolishCatalogTermWithoutDiacritics() {
        var term = new OperationalContextGlossaryTerm("customer-request", "Żądanie klienta", null,
                "Prośba o zmianę danych klienta.", List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        var catalog = new OperationalContextCatalog(List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(term), List.of(), List.of());

        var match = OperationalContextCatalogSearch.search(catalog, "zadanie klienta").get(0);

        assertEquals("glossaryTerm", match.type());
        assertEquals("customer-request", match.id());
        assertEquals(100, match.score());
    }
}
