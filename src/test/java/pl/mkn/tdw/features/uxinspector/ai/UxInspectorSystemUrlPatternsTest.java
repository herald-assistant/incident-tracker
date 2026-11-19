package pl.mkn.tdw.features.uxinspector.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UxInspectorSystemUrlPatternsTest {
    @Test
    void includesEveryActiveInternalCandidateEvenWhenPatternsOverlap() throws Exception {
        var systems = new ArrayList<Map<String, Object>>();
        var pattern = "https://crm.example.com/api/contacts/**";
        for (int i = 1; i <= 21; i++) {
            systems.add(system("crm-contacts-" + i, "internal-service", "active", List.of(pattern)));
        }
        systems.add(system("crm-archived", "internal-service", "retired", List.of(pattern)));
        systems.add(system("crm-partner", "external-system", "active", List.of(pattern)));
        systems.add(system("crm-worker", "internal-service", "active", List.of()));
        var catalog = OperationalContextDtos.catalogFromRaw(List.of(), List.of(), systems, List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of());
        var mapper = new ObjectMapper();
        var manifest = new UxInspectorSystemUrlPatterns(ignored -> catalog, mapper).render();
        assertThat(manifest).contains("UNTRUSTED_CATALOG_DATA", "Odczyt katalogu: ")
                .doesNotContain("crm-archived", "crm-partner", "crm-worker", "serviceNames", "description");
        var rows = mapper.readTree(manifest.lines().filter(line -> line.startsWith("[")).findFirst().orElseThrow());
        assertThat(rows.size()).isEqualTo(21);
        assertThat(rows.get(20).path("systemId").asText()).isEqualTo("crm-contacts-21");
        assertThat(rows.get(20).path("urlPatterns").get(0).asText()).isEqualTo(pattern);
    }

    @Test
    void missingCatalogProducesAVisibleGap() {
        var unavailable = new UxInspectorSystemUrlPatterns(ignored -> {
            throw new IllegalStateException("CRM catalog offline");
        }, new ObjectMapper());
        assertThat(unavailable.render()).contains("Nie udało się odczytać wzorców URL");
    }

    private Map<String, Object> system(String id, String type, String lifecycleStatus, List<String> patterns) {
        return Map.of("id", id, "name", "CRM Contacts", "systemType", type, "lifecycleStatus", lifecycleStatus,
                "matchSignals", Map.of("strong", Map.of("urlPatterns", patterns), "exact", Map.of("serviceNames", List.of(id))));
    }
}
