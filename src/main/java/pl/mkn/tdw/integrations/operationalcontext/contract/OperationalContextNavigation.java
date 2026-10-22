package pl.mkn.tdw.integrations.operationalcontext.contract;

import java.util.List;
import java.util.Map;

import static pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextRelationIndex.*;

/** Read-only discovery. Neither indirect paths nor reverse navigation grant ownership or access. */
public record OperationalContextNavigation(Map<EntityKey, List<Link>> links) {
    public OperationalContextNavigation {
        links = copyListMap(links);
    }

    public static String entityType(String type) {
        return switch (type) {
            case "glossary-term", "glossaryTerm" -> "term";
            case "boundedContext" -> "bounded-context";
            case "codeSearchScope" -> "code-search-scope";
            case "handoffRule" -> "handoff-rule";
            default -> type;
        };
    }

    public List<Link> forEntity(String type, String id) {
        return links.getOrDefault(new EntityKey(entityType(type), id), List.of());
    }

    public List<String> relatedIds(String type, String id, String targetType) {
        return forEntity(type, id).stream()
                .filter(link -> targetType.equals(link.target().type()))
                .filter(link -> !link.indirect() || link.via().stream().allMatch(ref -> "code-search-scope".equals(ref.type())))
                .map(link -> link.target().id()).distinct().sorted().toList();
    }

    /** Only explicit references and participant membership can select semantic repository scopes. */
    public List<String> scopeTargetIds(String type, String id, String targetType) {
        return forEntity(type, id).stream().filter(link -> !link.indirect())
                .filter(link -> targetType.equals(link.target().type()))
                .filter(link -> link.path().get(0).relationType().startsWith("references-")
                        || java.util.Set.of("primary-system", "supporting-system", "external-system", "platform-component",
                                "source-system", "target-system", "intermediary-system", "final-target-system").contains(link.path().get(0).relationType()))
                .map(link -> link.target().id()).distinct().sorted().toList();
    }

    public OperationalContextDtos.OperationalContextReferences references(String type, String id) {
        return new OperationalContextDtos.OperationalContextReferences(
                relatedIds(type, id, "system"), relatedIds(type, id, "repository"), relatedIds(type, id, "process"),
                relatedIds(type, id, "bounded-context"), relatedIds(type, id, "integration"), relatedIds(type, id, "term"),
                relatedIds(type, id, "team"), relatedIds(type, id, "handoff-rule"));
    }

    public record Link(
            EntityRef target,
            String relationType,
            String direction,
            boolean indirect,
            List<EntityRef> via,
            List<ReadModelRelation> path,
            String reason,
            List<SourceRef> sourceRefs
    ) {
        public Link {
            via = List.copyOf(via);
            path = List.copyOf(path);
            sourceRefs = List.copyOf(sourceRefs);
        }
    }
}
