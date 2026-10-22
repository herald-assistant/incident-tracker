package pl.mkn.tdw.features.incidentanalysis.evidence.provider.operationalcontext;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.operationalcontext.config.OperationalContextProperties;
import pl.mkn.tdw.integrations.operationalcontext.service.OperationalContextNavigationTestCreator;
import pl.mkn.tdw.integrations.operationalcontext.service.OperationalContextOwnershipResolver;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class OperationalContextNavigationMatchingTest {
    @Test
    void oneSidedFactsKeepIncidentTargetsScoresAndActionableEvidence() {
        var matcher = new OperationalContextCatalogMatcher(new OperationalContextProperties(), OperationalContextNavigationTestCreator.navigation());
        var signals = new OperationalContextIncidentSignals("crm-contacts", Set.of("crm-contacts"), Set.of(), Set.of("crm-contacts"));
        var one = OperationalContextNavigationTestCreator.catalog(false, 1);
        var two = OperationalContextNavigationTestCreator.catalog(true, 1);
        var first = matcher.match(one, signals);
        var second = matcher.match(two, signals);
        assertEquals(scores(first.systemMatches()), scores(second.systemMatches()));
        assertEquals(scores(first.repositoryMatches()), scores(second.repositoryMatches()));
        assertEquals(scores(first.boundedContextMatches()), scores(second.boundedContextMatches()));
        assertFalse(first.boundedContextMatches().isEmpty());
        var mapper = new OperationalContextEvidenceMapper(new OperationalContextOwnershipResolver(), OperationalContextNavigationTestCreator.navigation());
        var section = mapper.toEvidenceSection(first, one);
        assertTrue(section.items().stream().flatMap(item -> item.attributes().stream())
                .anyMatch(attribute -> attribute.name().equals("navigation") && attribute.value().contains("include=[navigation]")));
    }

    private List<String> scores(List<? extends OperationalContextMatchedEntry<?>> matches) {
        return matches.stream().map(match -> ((pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextEntry) match.entry()).id() + ":" + match.score().score()).toList();
    }
}
