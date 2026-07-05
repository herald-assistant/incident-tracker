package pl.mkn.tdw.api.operationalcontext;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.ExplainableAggregateDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.ExplainableBreakdownGroupDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OpenQuestionDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextDetailSectionDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextEntityDetailDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextEntityRelationsReadModelDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextExplainabilitySectionDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextProfiledReadModelDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextReadModelLinkDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextReadModelNextReadDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextReadModelTruncationDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextSearchResultDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.OperationalContextSummaryDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.ValidationFindingDto;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCodeSearchReadModel;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.EntityKey;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.EntityRef;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.Provenance;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.ReadModelRelation;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.SourceRef;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.ValidationFinding;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class OperationalContextProfiledReadModelMapper {

    private static final String PROFILE_DEFAULT = "default";
    private static final String PROFILE_EXPANDED = "expanded";
    private static final int DEFAULT_ITEMS = 12;

    boolean expandedProfile(String profile) {
        return !StringUtils.hasText(profile) || PROFILE_EXPANDED.equals(normalizeProfile(profile));
    }

    OperationalContextProfiledReadModelDto summary(OperationalContextSummaryDto expanded, String requestedProfile) {
        var profile = normalizeProfile(requestedProfile);
        var healthCards = limit(expanded.healthCards().stream()
                .map(this::aggregateSummary)
                .toList(), DEFAULT_ITEMS);
        var data = map(
                "counts", map(
                        "systems", expanded.systems(),
                        "repositories", expanded.repositories(),
                        "codeSearchScopes", expanded.codeSearchScopes(),
                        "processes", expanded.processes(),
                        "integrations", expanded.integrations(),
                        "boundedContexts", expanded.boundedContexts(),
                        "teams", expanded.teams(),
                        "glossaryTerms", expanded.glossaryTerms(),
                        "handoffRules", expanded.handoffRules(),
                        "openQuestions", expanded.openQuestions()
                ),
                "catalogStatus", expanded.catalogStatus(),
                "validationFindings", expanded.validationFindings(),
                "healthCards", healthCards
        );
        return envelope(
                "operational-context.summary",
                profile,
                null,
                data,
                List.of(
                        link("self", "/api/operational-context/summary?profile=default", PROFILE_DEFAULT, "Compact catalog summary."),
                        link("expanded", "/api/operational-context/summary?profile=expanded", PROFILE_EXPANDED, "Full catalog summary.")
                ),
                List.of("profile=expanded"),
                List.of("Use search or entity detail to select a system, repository, process, bounded context or integration."),
                List.of("opctx_search", "opctx_get_entity"),
                "Expanded profile returns full health-card inventories.",
                omitted(expanded.healthCards().size(), healthCards.size(), "healthCards"),
                truncation("summary compacted", healthCards.size(), expanded.healthCards().size()),
                confidenceFromValidation(expanded.validationFindings()),
                List.of("Summary is catalog-level orientation; use entity reads for details."),
                List.of(),
                List.of()
        );
    }

    OperationalContextProfiledReadModelDto search(
            String query,
            List<OperationalContextSearchResultDto> expanded,
            String requestedProfile
    ) {
        var profile = normalizeProfile(requestedProfile);
        var results = limit(expanded, DEFAULT_ITEMS).stream()
                .map(result -> map(
                        "type", result.type(),
                        "id", result.id(),
                        "label", result.label(),
                        "subtitle", result.subtitle(),
                        "confidence", result.confidence(),
                        "matchedFields", result.matchedFields(),
                        "why", result.why(),
                        "links", entityLinks(result.type(), result.id())
                ))
                .toList();
        return envelope(
                "operational-context.search",
                profile,
                map("query", query),
                map(
                        "query", query,
                        "totalResults", expanded.size(),
                        "returnedResults", results.size(),
                        "results", results
                ),
                List.of(link(
                        "expanded",
                        "/api/operational-context/search?q=" + encode(query) + "&profile=expanded",
                        PROFILE_EXPANDED,
                        "Full search result list."
                )),
                List.of("profile=expanded"),
                List.of("Open the best matching entity before choosing repositories or handoff decision."),
                List.of("opctx_get_entity"),
                "Expanded profile returns all search results.",
                omitted(expanded.size(), results.size(), "results"),
                truncation("search compacted", results.size(), expanded.size()),
                expanded.stream().findFirst().map(OperationalContextSearchResultDto::confidence).orElse("unknown"),
                List.of("Search is lexical/ranked; entity detail contains provenance and relations."),
                List.of(),
                List.of()
        );
    }

    OperationalContextProfiledReadModelDto entity(OperationalContextEntityDetailDto expanded, String requestedProfile) {
        var profile = normalizeProfile(requestedProfile);
        var overview = limit(expanded.overviewSections().stream()
                .map(this::overviewSectionSummary)
                .toList(), DEFAULT_ITEMS);
        var related = limit(expanded.relatedEntities().stream()
                .map(this::groupSummary)
                .toList(), DEFAULT_ITEMS);
        var signals = limit(expanded.recognitionSignals().stream()
                .map(this::groupSummary)
                .toList(), DEFAULT_ITEMS);
        var explainability = limit(expanded.explainabilitySections().stream()
                .map(this::explainabilitySummary)
                .toList(), DEFAULT_ITEMS);
        var validation = limit(expanded.validationFindings().stream()
                .map(this::validationDto)
                .toList(), DEFAULT_ITEMS);
        var openQuestions = limit(expanded.openQuestions().stream()
                .map(this::openQuestionSummary)
                .toList(), DEFAULT_ITEMS);
        var sourceRefs = limit(expanded.sourceReferences().stream()
                .map(ref -> map("file", ref.file(), "path", ref.path(), "entityId", ref.entityId()))
                .toList(), DEFAULT_ITEMS);

        return envelope(
                "operational-context.entity-detail",
                profile,
                ref(expanded.type(), expanded.id(), expanded.title(), null, expanded.subtitle()),
                map(
                        "entity", map(
                                "type", expanded.type(),
                                "id", expanded.id(),
                                "title", expanded.title(),
                                "subtitle", expanded.subtitle()
                        ),
                        "overviewSections", overview,
                        "relatedEntities", related,
                        "recognitionSignals", signals,
                        "explainability", explainability,
                        "validationFindings", validation,
                        "openQuestions", openQuestions
                ),
                entityLinks(expanded.type(), expanded.id()),
                List.of("profile=expanded", "relations", "code-search"),
                List.of(
                        "Read relations to move to adjacent systems, repositories, processes or teams.",
                        "Read code-search when code repositories must be selected."
                ),
                List.of("opctx_get_entity", "opctx_search"),
                "Expanded profile returns raw source preview and full groups.",
                List.of("rawSourcePreview is omitted from compact profile."),
                truncation(
                        "entity detail compacted",
                        overview.size() + related.size() + signals.size(),
                        expanded.overviewSections().size() + expanded.relatedEntities().size() + expanded.recognitionSignals().size()
                ),
                confidenceFromValidationDto(expanded.validationFindings()),
                List.of("Entity detail is catalog context, not runtime evidence."),
                sourceRefs,
                validation
        );
    }

    OperationalContextProfiledReadModelDto relations(
            OperationalContextEntityRelationsReadModelDto expanded,
            String requestedProfile
    ) {
        var profile = normalizeProfile(requestedProfile);
        var outgoing = limit(expanded.outgoingRelations().stream().map(this::relationSummary).toList(), DEFAULT_ITEMS);
        var incoming = limit(expanded.incomingRelations().stream().map(this::relationSummary).toList(), DEFAULT_ITEMS);
        var neighbors = limit(expanded.neighbors().stream().map(this::refSummary).toList(), DEFAULT_ITEMS);
        var validation = validationSummaries(expanded.validationFindings());
        return envelope(
                expanded.contract(),
                profile,
                refSummary(expanded.analysisTarget()),
                map(
                        "analysisTarget", refSummary(expanded.analysisTarget()),
                        "outgoingRelations", outgoing,
                        "incomingRelations", incoming,
                        "neighbors", neighbors,
                        "validationFindings", validation
                ),
                readModelLinks(expanded.analysisTarget(), "relations"),
                List.of("profile=expanded"),
                List.of("Use neighbors to continue analysis in the next system, process, team or repository."),
                List.of("opctx_get_entity", "opctx_search"),
                "Expanded profile returns full relation lists.",
                List.of(),
                truncation(
                        "relations compacted",
                        outgoing.size() + incoming.size() + neighbors.size(),
                        expanded.outgoingRelations().size() + expanded.incomingRelations().size() + expanded.neighbors().size()
                ),
                confidenceFromValidation(validation),
                List.of(),
                List.of(),
                validation
        );
    }

    OperationalContextProfiledReadModelDto codeSearch(
            OperationalContextCodeSearchReadModel expanded,
            String requestedProfile
    ) {
        var profile = normalizeProfile(requestedProfile);
        var scopes = limit(expanded.scopes().stream()
                .map(scope -> map(
                        "scope", refSummary(scope.scope()),
                        "scopeType", scope.scopeType(),
                        "target", refSummary(scope.target()),
                        "repositories", refs(scope.repositories()),
                        "limitations", scope.limitations(),
                        "provenance", provenanceSummary(scope.provenance())
                ))
                .toList(), DEFAULT_ITEMS);
        var repositories = limit(expanded.repositories().stream()
                .map(repository -> map(
                        "repository", refSummary(repository.repository()),
                        "role", repository.role(),
                        "priority", repository.priority(),
                        "reason", repository.reason(),
                        "readFor", repository.readFor(),
                        "searchMode", repository.searchMode(),
                        "pathPrefixes", repository.pathPrefixes(),
                        "git", map(
                                "provider", repository.git().provider(),
                                "group", repository.git().group(),
                                "project", repository.git().project(),
                                "projectPath", repository.git().projectPath(),
                                "defaultBranch", repository.git().defaultBranch(),
                                "url", repository.git().url()
                        ),
                        "provenance", provenanceSummary(repository.provenance())
                ))
                .toList(), DEFAULT_ITEMS);
        var validation = validationSummaries(expanded.validationFindings());
        return envelope(
                expanded.contract(),
                profile,
                refSummary(expanded.analysisTarget()),
                map(
                        "analysisTarget", refSummary(expanded.analysisTarget()),
                        "scopes", scopes,
                        "repositories", repositories,
                        "limitations", expanded.limitations(),
                        "validationFindings", validation
                ),
                readModelLinks(expanded.analysisTarget(), "code-search"),
                List.of("profile=expanded"),
                List.of("Use returned repositories as the GitLab search/read scope and respect repository searchMode/pathPrefixes."),
                List.of("gitlab_list_available_repositories", "gitlab_search_repository_candidates"),
                "Expanded profile returns all repositories and scopes.",
                List.of(),
                truncation(
                        "code-search compacted",
                        scopes.size() + repositories.size(),
                        expanded.scopes().size() + expanded.repositories().size()
                ),
                confidenceFromValidation(validation),
                expanded.limitations(),
                List.of(),
                validation
        );
    }

    private OperationalContextProfiledReadModelDto envelope(
            String contract,
            String profile,
            Object analysisTarget,
            Map<String, Object> data,
            List<OperationalContextReadModelLinkDto> links,
            List<String> availableExpansions,
            List<String> suggestedNextReads,
            List<String> suggestedTools,
            String reasonToExpand,
            List<String> omittedBecause,
            OperationalContextReadModelTruncationDto truncation,
            String confidence,
            List<String> limitations,
            List<?> sourceRefs,
            List<?> validationFindings
    ) {
        return new OperationalContextProfiledReadModelDto(
                contract,
                1,
                profile,
                analysisTarget,
                data,
                links,
                availableExpansions,
                suggestedNextReads,
                nextReadsFrom(links),
                suggestedTools,
                reasonToExpand,
                omittedBecause,
                truncation,
                relevanceScore(confidence),
                confidence,
                limitations,
                null,
                objects(sourceRefs),
                objects(validationFindings)
        );
    }

    private List<OperationalContextReadModelNextReadDto> nextReadsFrom(List<OperationalContextReadModelLinkDto> links) {
        return links.stream()
                .filter(link -> !"self".equals(link.rel()))
                .map(link -> new OperationalContextReadModelNextReadDto(
                        link.rel(),
                        link.rel(),
                        link.href(),
                        link.profile(),
                        toolFor(link),
                        Map.of(),
                        link.reason()
                ))
                .toList();
    }

    private String toolFor(OperationalContextReadModelLinkDto link) {
        return switch (link.rel()) {
            case "relations", "code-search", "entity", "expanded" -> "opctx_get_entity";
            default -> null;
        };
    }

    private List<OperationalContextReadModelLinkDto> entityLinks(String type, String id) {
        var links = new ArrayList<OperationalContextReadModelLinkDto>();
        links.add(link("self", entityHref(type, id, PROFILE_DEFAULT), PROFILE_DEFAULT, "Compact entity detail."));
        links.add(link("expanded", entityHref(type, id, PROFILE_EXPANDED), PROFILE_EXPANDED, "Full entity detail."));
        links.add(link("relations", readModelHref(type, id, "relations", PROFILE_DEFAULT), PROFILE_DEFAULT, "Relation graph."));
        links.add(link("code-search", readModelHref(type, id, "code-search", PROFILE_DEFAULT), PROFILE_DEFAULT, "Repository search scope."));
        return List.copyOf(links);
    }

    private List<OperationalContextReadModelLinkDto> readModelLinks(EntityRef target, String current) {
        return List.of(
                link("self", readModelHref(target.type(), target.id(), current, PROFILE_DEFAULT), PROFILE_DEFAULT, "Compact " + current + " view."),
                link("expanded", readModelHref(target.type(), target.id(), current, PROFILE_EXPANDED), PROFILE_EXPANDED, "Full " + current + " view."),
                link("entity", entityHref(target.type(), target.id(), PROFILE_DEFAULT), PROFILE_DEFAULT, "Entity detail.")
        );
    }

    private OperationalContextReadModelLinkDto link(String rel, String href, String profile, String reason) {
        return new OperationalContextReadModelLinkDto(rel, href, profile, reason);
    }

    private String entityHref(String type, String id, String profile) {
        return "/api/operational-context/entities/" + encode(type) + "/" + encode(id) + "?profile=" + profile;
    }

    private String readModelHref(String type, String id, String model, String profile) {
        return "/api/operational-context/read-model/entities/" + encode(type) + "/" + encode(id)
                + "/" + model + "?profile=" + profile;
    }

    private Map<String, Object> overviewSectionSummary(OperationalContextDetailSectionDto section) {
        return map("title", section.title(), "fields", section.fields());
    }

    private Map<String, Object> groupSummary(ExplainableBreakdownGroupDto group) {
        return map(
                "label", group.label(),
                "count", group.count(),
                "items", limit(group.items().stream()
                        .map(item -> map(
                                "id", item.id(),
                                "label", item.label(),
                                "kind", item.kind(),
                                "reason", item.reason(),
                                "status", item.status()
                        ))
                        .toList(), DEFAULT_ITEMS)
        );
    }

    private Map<String, Object> aggregateSummary(ExplainableAggregateDto aggregate) {
        return map(
                "label", aggregate.label(),
                "count", aggregate.count(),
                "severity", aggregate.severity(),
                "confidence", aggregate.confidence(),
                "detailsType", aggregate.detailsType(),
                "detailsIds", aggregate.detailsIds()
        );
    }

    private Map<String, Object> explainabilitySummary(OperationalContextExplainabilitySectionDto section) {
        return map(
                "title", section.title(),
                "summary", section.summary(),
                "confidence", section.confidence(),
                "warnings", section.warnings()
        );
    }

    private Map<String, Object> openQuestionSummary(OpenQuestionDto question) {
        return map(
                "id", question.id(),
                "entityType", question.entityType(),
                "entityId", question.entityId(),
                "question", question.question(),
                "severity", question.severity(),
                "status", question.status()
        );
    }

    private Map<String, Object> validationDto(ValidationFindingDto finding) {
        return map(
                "id", finding.id(),
                "severity", finding.severity(),
                "category", finding.category(),
                "entityType", finding.entityType(),
                "entityId", finding.entityId(),
                "title", finding.title(),
                "detail", finding.detail()
        );
    }

    private Map<String, Object> relationSummary(ReadModelRelation relation) {
        return map(
                "relationType", relation.relationType(),
                "direction", relation.direction(),
                "source", keySummary(relation.source()),
                "target", keySummary(relation.target()),
                "role", relation.role(),
                "canonicalOwner", keySummary(relation.canonicalOwner()),
                "derived", relation.derived(),
                "provenance", provenanceSummary(relation.provenance())
        );
    }

    private Map<String, Object> refSummary(EntityRef ref) {
        if (ref == null) {
            return Map.of();
        }
        return ref(ref.type(), ref.id(), ref.label(), ref.lifecycleStatus(), ref.summary());
    }

    private Map<String, Object> ref(String type, String id, String label, String lifecycleStatus, String summary) {
        return map(
                "type", type,
                "id", id,
                "label", label,
                "lifecycleStatus", lifecycleStatus,
                "summary", summary
        );
    }

    private Map<String, Object> keySummary(EntityKey key) {
        if (key == null) {
            return Map.of();
        }
        return map("type", key.type(), "id", key.id());
    }

    private List<Map<String, Object>> refs(List<EntityRef> refs) {
        return refs.stream().map(this::refSummary).toList();
    }

    private Map<String, Object> provenanceSummary(Provenance provenance) {
        if (provenance == null) {
            return Map.of();
        }
        return map(
                "canonical", provenance.canonical(),
                "derivation", provenance.derivation(),
                "confidence", provenance.confidence(),
                "sourceRefs", sourceRefs(provenance.sourceRefs()),
                "warnings", provenance.warnings()
        );
    }

    private List<Map<String, Object>> sourceRefs(List<SourceRef> refs) {
        return refs.stream()
                .map(ref -> map(
                        "file", ref.file(),
                        "entityType", ref.entityType(),
                        "entityId", ref.entityId(),
                        "fieldPath", ref.fieldPath(),
                        "relationRole", ref.relationRole()
                ))
                .toList();
    }

    private List<Object> validationSummaries(List<ValidationFinding> findings) {
        return findings.stream()
                .map(finding -> (Object) map(
                        "severity", finding.severity(),
                        "code", finding.code(),
                        "message", finding.message(),
                        "sourceRefs", sourceRefs(finding.sourceRefs())
                ))
                .toList();
    }

    private String confidenceFromValidation(Map<String, Integer> validationFindings) {
        if (validationFindings.getOrDefault("error", 0) > 0) {
            return "low";
        }
        if (validationFindings.getOrDefault("warning", 0) > 0) {
            return "medium";
        }
        return "high";
    }

    private String confidenceFromValidationDto(List<ValidationFindingDto> findings) {
        if (findings.stream().anyMatch(finding -> "error".equalsIgnoreCase(finding.severity()))) {
            return "low";
        }
        if (findings.stream().anyMatch(finding -> "warning".equalsIgnoreCase(finding.severity()))) {
            return "medium";
        }
        return "high";
    }

    private String confidenceFromValidation(List<?> findings) {
        var text = findings.toString().toLowerCase();
        if (text.contains("severity=error") || text.contains("\"severity\" : \"error\"")) {
            return "low";
        }
        if (text.contains("severity=warning") || text.contains("\"severity\" : \"warning\"")) {
            return "medium";
        }
        return "high";
    }

    private List<Object> objects(List<?> values) {
        return values == null ? List.of() : values.stream().map(Object.class::cast).toList();
    }

    private Double relevanceScore(String confidence) {
        return switch (StringUtils.hasText(confidence) ? confidence.toLowerCase() : "unknown") {
            case "high" -> 0.9;
            case "medium" -> 0.6;
            case "low" -> 0.3;
            default -> 0.5;
        };
    }

    private OperationalContextReadModelTruncationDto truncation(String reason, int returned, int total) {
        var truncated = returned < total;
        return new OperationalContextReadModelTruncationDto(
                truncated,
                truncated ? reason : null,
                Map.of("items", returned),
                truncated ? Map.of("items", total - returned) : Map.of()
        );
    }

    private List<String> omitted(int total, int returned, String label) {
        return returned < total ? List.of(label + " limited in compact profile.") : List.of();
    }

    private <T> List<T> limit(List<T> values, int limit) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().limit(limit).toList();
    }

    private Map<String, Object> map(Object... keysAndValues) {
        var values = new LinkedHashMap<String, Object>();
        for (var index = 0; index + 1 < keysAndValues.length; index += 2) {
            var key = (String) keysAndValues[index];
            var value = keysAndValues[index + 1];
            if (value != null) {
                values.put(key, value);
            }
        }
        return Map.copyOf(values);
    }

    private String normalizeProfile(String profile) {
        return StringUtils.hasText(profile) ? profile.trim().toLowerCase() : PROFILE_DEFAULT;
    }

    private String encode(String value) {
        return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8);
    }
}
