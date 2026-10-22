package pl.mkn.tdw.integrations.operationalcontext.service;

import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class OperationalContextNavigationServiceTest {
    private final OperationalContextNavigationService service =
            new OperationalContextNavigationService(new OperationalContextRelationIndexBuilder());

    @Test
    void explicitOwnerTeamIdsProvideCanonicalNavigationFromBothEndsWithoutTeamReferences() {
        var catalog = OperationalContextDtos.catalogFromRaw(List.of(Map.of("id", "crm-domain-team")), List.of(),
                List.of(Map.of("id", "crm-contact-core", "ownership", Map.of("ownerTeamIds", List.of("crm-domain-team"),
                        "ownershipStatus", "explicit", "confidence", "medium"))), List.of(), List.of(), List.of(),
                List.of(Map.of("id", "crm-preferences", "ownership", Map.of("ownerTeamIds", List.of("crm-domain-team"),
                        "ownershipStatus", "explicit", "confidence", "high"))), List.of(), List.of(), List.of());
        var navigation = service.build(catalog);
        var team = navigation.forEntity("team", "crm-domain-team");
        assertEquals(2, team.size());
        assertTrue(team.stream().allMatch(link -> !link.indirect() && "incoming".equals(link.direction())));
        for (var link : team) {
            assertEquals("owned-by-team", link.relationType());
            var edge = link.path().get(0);
            assertEquals("owner", edge.role());
            assertEquals(edge.source(), edge.canonicalOwner());
            assertTrue(edge.provenance().canonical());
            assertFalse(edge.derived());
            assertTrue(link.sourceRefs().get(0).fieldPath().endsWith(".ownership.ownerTeamIds[id=crm-domain-team]"));
            assertTrue(navigation.forEntity(link.target().type(), link.target().id()).stream()
                    .anyMatch(reverse -> reverse.target().id().equals("crm-domain-team")
                            && "outgoing".equals(reverse.direction()) && reverse.path().equals(link.path())));
        }
        assertEquals("medium", navigation.forEntity("system", "crm-contact-core").get(0).path().get(0).provenance().confidence());
    }

    @Test
    void inferredOwnerIdsAndMatchingOwnerLabelsDoNotCreateConfirmedTeamEdges() {
        var catalog = OperationalContextDtos.catalogFromRaw(List.of(Map.of("id", "crm-domain-team", "name", "CRM Domain Team")),
                List.of(), List.of(Map.of("id", "crm-contact-core", "ownership", Map.of(
                        "ownerTeamIds", List.of("crm-domain-team"), "ownershipStatus", "inferred")),
                        Map.of("id", "crm-label-only", "ownership", Map.of("ownerLabel", "CRM Domain Team", "ownershipStatus", "explicit"))),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        assertTrue(service.build(catalog).forEntity("team", "crm-domain-team").isEmpty());
    }

    @Test
    void oneSidedReferenceIsNavigableFromBothEndsWithOriginalProvenance() {
        var navigation = service.build(catalog(false));
        var fromSystem = navigation.forEntity("system", "crm-contacts").stream()
                .filter(link -> link.target().id().equals("crm-profile") && !link.indirect()).findFirst().orElseThrow();
        var fromContext = navigation.forEntity("bounded-context", "crm-profile").get(0);
        assertEquals("incoming", fromSystem.direction());
        assertEquals("outgoing", fromContext.direction());
        assertEquals(fromContext.path(), fromSystem.path());
        assertEquals("bounded-contexts.yml", fromSystem.sourceRefs().get(0).file());
    }

    @Test
    void duplicateReverseReferenceDoesNotChangeRelatedTargets() {
        var one = service.build(catalog(false));
        var two = service.build(catalog(true));
        assertEquals(one.relatedIds("system", "crm-contacts", "bounded-context"),
                two.relatedIds("system", "crm-contacts", "bounded-context"));
        assertEquals(1, two.forEntity("system", "crm-contacts").stream().filter(link -> !link.indirect()).count());
    }

    @Test
    void integrationPathDoesNotBecomeDirectContextAssignment() {
        var catalog = OperationalContextDtos.catalogFromRaw(List.of(), List.of(),
                List.of(Map.of("id", "crm-contacts"), Map.of("id", "crm-notifications")),
                List.of(Map.of("id", "crm-contact-events", "participants", Map.of(
                        "source", Map.of("system", "crm-contacts"),
                        "targets", List.of(Map.of("system", "crm-notifications", "boundedContext", "crm-messaging"))))),
                List.of(), List.of(), List.of(Map.of("id", "crm-messaging")), List.of(), List.of(), List.of());
        var navigation = service.build(catalog);
        assertTrue(navigation.relatedIds("system", "crm-contacts", "bounded-context").isEmpty());
        var link = navigation.forEntity("system", "crm-contacts").stream()
                .filter(item -> item.target().id().equals("crm-messaging")).findFirst().orElseThrow();
        assertTrue(link.indirect());
        assertEquals(2, link.path().size());
        assertEquals("crm-contact-events", link.via().get(0).id());
        assertTrue(link.reason().contains("does not establish ownership"));
    }

    @Test
    void semanticRolesRemainDistinctAndCyclesStopAtOneIntermediate() {
        var catalog = OperationalContextDtos.catalogFromRaw(List.of(), List.of(), List.of(
                Map.of("id", "crm-contacts", "relations", List.of(
                        Map.of("type", "supports", "targetType", "system", "target", "crm-portal"),
                        Map.of("type", "supplies", "targetType", "system", "target", "crm-portal"),
                        Map.of("type", "consumes", "targetType", "system", "target", "crm-portal"))),
                Map.of("id", "crm-portal", "references", Map.of("systems", List.of("crm-contacts")))),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        var links = service.build(catalog).forEntity("system", "crm-contacts");
        assertEquals(4, links.size());
        assertTrue(links.stream().allMatch(link -> link.path().size() == 1));
        assertEquals(links, service.build(catalog).forEntity("system", "crm-contacts"));
    }

    @Test
    void distinctParticipantRolesForTheSameTargetRemainVisible() {
        var catalog = OperationalContextDtos.catalogFromRaw(List.of(), List.of(), List.of(Map.of("id", "crm-contacts")),
                List.of(Map.of("id", "crm-contact-events", "participants", Map.of("targets", List.of(
                        Map.of("system", "crm-contacts", "role", "profile-consumer"),
                        Map.of("system", "crm-contacts", "role", "preferences-consumer"))))),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        assertEquals(2, service.build(catalog).forEntity("integration", "crm-contact-events").stream()
                .filter(link -> link.target().id().equals("crm-contacts")).count());
    }

    @Test
    void sameSystemParticipatingThroughDistinctContextsIsNotARepeatedFact() {
        var catalog = OperationalContextDtos.catalogFromRaw(List.of(), List.of(),
                List.of(Map.of("id", "crm-contacts")),
                List.of(Map.of("id", "crm-contact-events", "participants", Map.of("targets", List.of(
                        Map.of("system", "crm-contacts", "boundedContext", "crm-profile", "role", "target"),
                        Map.of("system", "crm-contacts", "boundedContext", "crm-preferences", "role", "target"))))),
                List.of(), List.of(), List.of(Map.of("id", "crm-profile"), Map.of("id", "crm-preferences")),
                List.of(), List.of(), List.of());
        assertFalse(new OperationalContextReadModelValidator().validate(catalog).stream()
                .anyMatch(finding -> "DUPLICATE_RELATION_MERGED".equals(finding.code())));
        var navigation = service.build(catalog).forEntity("integration", "crm-contact-events");
        assertEquals(2, navigation.stream().filter(link -> "bounded-context".equals(link.target().type())).count());
        assertTrue(navigation.stream().filter(link -> "system".equals(link.target().type()))
                .flatMap(link -> link.sourceRefs().stream()).allMatch(ref -> ref.fieldPath().contains("boundedContext=")));
    }

    @Test
    void codeSearchKeepsScopesAfterRemovingRepositoryAndDependencyCopies() {
        var builder = new OperationalContextCodeSearchReadModelBuilder();
        for (var target : List.of(List.of("system", "crm-portal"), List.of("repository", "crm-contacts-repo"))) {
            assertEquals(builder.buildForEntity(codeCatalog(true), target.get(0), target.get(1)).scopes(),
                    builder.buildForEntity(codeCatalog(false), target.get(0), target.get(1)).scopes());
            assertFalse(builder.buildForEntity(codeCatalog(false), target.get(0), target.get(1)).scopes().isEmpty());
        }
    }

    private OperationalContextDtos.OperationalContextCatalog codeCatalog(boolean copies) {
        return OperationalContextDtos.catalogFromRaw(List.of(), List.of(), List.of(
                        copies ? Map.of("id", "crm-contacts", "references", Map.of("repositories", List.of("crm-contacts-repo")))
                                : Map.of("id", "crm-contacts"),
                        copies ? Map.of("id", "crm-portal", "dependencies", Map.of("downstream", List.of("crm-contacts")))
                                : Map.of("id", "crm-portal")),
                List.of(Map.of("id", "crm-portal-contacts", "participants", Map.of("source", Map.of("system", "crm-portal"),
                        "targets", List.of(Map.of("system", "crm-contacts"))))),
                List.of(Map.of("id", "crm-contacts-repo")),
                List.of(Map.of("id", "crm-contacts-code", "target", Map.of("type", "system", "id", "crm-contacts"),
                        "repositories", List.of(Map.of("repoId", "crm-contacts-repo", "role", "primary", "searchMode", "whole-repository")))),
                List.of(), List.of(), List.of(), List.of());
    }

    private OperationalContextDtos.OperationalContextCatalog catalog(boolean reverse) {
        return OperationalContextDtos.catalogFromRaw(List.of(), List.of(), List.of(reverse
                        ? Map.of("id", "crm-contacts", "references", Map.of("boundedContexts", List.of("crm-profile")))
                        : Map.of("id", "crm-contacts")),
                List.of(), List.of(), List.of(),
                List.of(Map.of("id", "crm-profile", "references", Map.of("systems", List.of("crm-contacts")))),
                List.of(), List.of(), List.of());
    }
}
