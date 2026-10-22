package pl.mkn.tdw.integrations.operationalcontext.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextNavigationPort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndexPort;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextNavigation;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextNavigation.Link;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextRelationIndex.*;

@Service
@RequiredArgsConstructor
public class OperationalContextNavigationService implements OperationalContextNavigationPort {
    private static final Set<String> INTERMEDIATES = Set.of("integration", "code-search-scope", "process-step");
    private final OperationalContextRelationIndexPort relationIndex;

    @Override
    public OperationalContextNavigation build(OperationalContextCatalog catalog) {
        return fromIndex(relationIndex.build(catalog));
    }

    static OperationalContextNavigation fromIndex(
            pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextRelationIndex index
    ) {
        var result = new LinkedHashMap<EntityKey, List<Link>>();
        for (var entity : index.entities().keySet()) {
            var links = new LinkedHashMap<String, Link>();
            for (var edge : incident(index, entity)) {
                var target = other(edge, entity);
                if (!index.entities().containsKey(target)) {
                    continue;
                }
                if (!"process-step".equals(target.type())) {
                    add(links, link(index.entities().get(target), List.of(), List.of(edge), entity));
                }
                if (!INTERMEDIATES.contains(target.type())) {
                    continue;
                }
                for (var next : incident(index, target)) {
                    var destination = other(next, target);
                    if (destination.equals(entity) || "process-step".equals(destination.type()) || !index.entities().containsKey(destination)) {
                        continue;
                    }
                    add(links, link(index.entities().get(destination), List.of(index.entities().get(target)),
                            List.of(edge, next), entity));
                }
            }
            result.put(entity, links.values().stream()
                    .sorted(Comparator.comparing(Link::indirect).thenComparing(link -> link.target().type())
                            .thenComparing(link -> link.target().id()).thenComparing(Link::relationType)
                            .thenComparing(link -> link.via().toString()))
                    .toList());
        }
        return new OperationalContextNavigation(result);
    }

    private static List<ReadModelRelation> incident(
            pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextRelationIndex index, EntityKey key
    ) {
        return Stream.concat(index.outgoingRelations().getOrDefault(key, List.of()).stream(),
                index.incomingRelations().getOrDefault(key, List.of()).stream()).toList();
    }

    private static EntityKey other(ReadModelRelation edge, EntityKey key) {
        return edge.source().equals(key) ? edge.target() : edge.source();
    }

    private static Link link(EntityRef target, List<EntityRef> via, List<ReadModelRelation> path, EntityKey origin) {
        var edge = path.get(0);
        var indirect = !via.isEmpty();
        var relation = indirect ? "related-via-" + via.get(0).type() : edge.relationType();
        var direction = edge.source().equals(origin) ? "outgoing" : "incoming";
        if (indirect && "integration".equals(via.get(0).type()) && "system".equals(origin.type()) && "system".equals(target.type())) {
            var next = path.get(1);
            if ("source-system".equals(edge.relationType()) && Set.of("target-system", "intermediary-system").contains(next.relationType())) {
                relation = "integration-downstream-system";
            } else if (Set.of("target-system", "intermediary-system").contains(edge.relationType()) && "source-system".equals(next.relationType())) {
                relation = "integration-upstream-system";
            }
        }
        var reason = indirect
                ? "Read " + target.key().value() + " for " + readPurpose(target.type()) + " via " + via.get(0).key().value()
                    + "; this path does not establish ownership, implementation or runtime flow."
                : "Read " + target.key().value() + " for " + readPurpose(target.type()) + "; " + edge.relationType() + " (" + direction
                    + "); the fact is maintained on " + edge.canonicalOwner().value() + ".";
        return new Link(target, relation, direction, indirect, via, path, reason,
                path.stream().flatMap(item -> item.provenance().sourceRefs().stream()).distinct().toList());
    }

    private static String readPurpose(String type) {
        return switch (type) {
            case "system" -> "system responsibilities and recognition signals";
            case "bounded-context" -> "domain boundaries, local language and resolved ownership";
            case "process" -> "business stages, participants and completion conditions";
            case "integration" -> "participant roles, direction and handoff context";
            case "repository" -> "repository purpose and source identity";
            case "code-search-scope" -> "repositories, their roles and permitted search boundaries";
            case "team" -> "team identity; resolve responsibility through a system or bounded context";
            case "term" -> "domain meaning and disambiguation";
            case "handoff-rule" -> "required evidence and the first handoff action";
            default -> "related catalog context";
        };
    }

    private static void add(Map<String, Link> links, Link link) {
        // Generic references are symmetric navigation; semantic role edges remain distinct.
        var kind = link.relationType().startsWith("references-") ? "reference" : link.relationType();
        var roles = link.path().stream().map(edge -> java.util.Objects.toString(edge.role(), "")).toList();
        var key = link.target().key().value() + "|" + kind + "|" + roles + "|" + link.via();
        links.merge(key, link, (left, right) -> {
            var preferred = canonicalPriority(left) <= canonicalPriority(right) ? left : right;
            var refs = new ArrayList<>(left.sourceRefs());
            refs.addAll(right.sourceRefs());
            return new Link(preferred.target(), preferred.relationType(), preferred.direction(), preferred.indirect(),
                    preferred.via(), preferred.path(), preferred.reason(), refs.stream().distinct().toList());
        });
    }

    private static int canonicalPriority(Link link) {
        var edge = link.path().get(0);
        var types = new java.util.HashSet<>(List.of(edge.source().type(), edge.target().type()));
        var ownerType = types.contains("integration") ? "integration"
                : types.contains("repository") ? "repository"
                : types.contains("bounded-context") && types.contains("system") ? "bounded-context"
                : types.contains("process") ? "process" : edge.source().type();
        return ownerType.equals(edge.canonicalOwner().type()) ? 0 : 1;
    }
}
