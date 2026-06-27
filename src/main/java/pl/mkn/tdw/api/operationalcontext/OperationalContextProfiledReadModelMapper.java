package pl.mkn.tdw.api.operationalcontext;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.ExplainableAggregateDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.ExplainableBreakdownGroupDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.ExplainableBreakdownItemDto;
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
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.SourceReferenceDto;
import pl.mkn.tdw.api.operationalcontext.dto.OperationalContextDtos.ValidationFindingDto;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextBlastRadiusReadModel;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextBlastRadiusReadModel.FlowImpactView;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextBlastRadiusReadModel.ImpactNodeView;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextBlastRadiusReadModel.ImplementationImpactView;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextBlastRadiusReadModel.StepImpactView;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCodeSearchReadModel;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCodeSearchReadModel.CodeSearchHints;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCodeSearchReadModel.CodeSearchScopeView;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCodeSearchReadModel.ModuleView;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCodeSearchReadModel.RepositoryView;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextFlowReadModel;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextFlowReadModel.FlowEdgeView;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextFlowReadModel.FlowStepView;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextFlowReadModel.ImplementationRef;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextImplementationReadModel;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextImplementationReadModel.ImplementationView;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.EntityKey;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.EntityRef;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.Provenance;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.ReadModelRelation;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.SourceRef;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.ValidationFinding;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

class OperationalContextProfiledReadModelMapper {

    private static final String PROFILE_INDEX = "index";
    private static final String PROFILE_SUMMARY = "summary";
    private static final String PROFILE_DEFAULT = "default";
    private static final String PROFILE_EXPANDED = "expanded";

    boolean expandedProfile(String profile) {
        return !StringUtils.hasText(profile) || PROFILE_EXPANDED.equals(normalizeProfile(profile));
    }

    OperationalContextProfiledReadModelDto summary(OperationalContextSummaryDto expanded, String requestedProfile) {
        var profile = normalizeProfile(requestedProfile);
        var budget = budget(profile);
        var healthCards = limit(expanded.healthCards().stream()
                .map(this::healthCardSummary)
                .toList(), budget.summaryCards());
        var sourceRefs = sourceRefsFromAggregates(expanded.healthCards(), budget.sourceRefs());
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
                1,
                profile,
                null,
                data,
                summaryLinks(),
                List.of("profile=expanded"),
                List.of(
                        "Use /api/operational-context/search?q=<term>&profile=default to pick a target.",
                        "Use entity detail with profile=default before reading heavy read-model projections."
                ),
                List.of("opctx_search", "opctx_get_entity"),
                "Expand summary when UI or diagnostics need full health-card inventories.",
                List.of("Full health-card item inventories stay in expanded profile."),
                truncation("summary health-card inventories compacted", counts(
                        "healthCards", healthCards.size(),
                        "sourceRefs", sourceRefs.size()
                ), counts(
                        "healthCards", expanded.healthCards().size(),
                        "sourceRefs", countAggregateSourceRefs(expanded.healthCards())
                )),
                1.0,
                confidenceFromValidation(expanded.validationFindings()),
                List.of(),
                null,
                sourceRefs,
                List.of()
        );
    }

    OperationalContextProfiledReadModelDto search(
            String query,
            List<OperationalContextSearchResultDto> expanded,
            String requestedProfile
    ) {
        var profile = normalizeProfile(requestedProfile);
        var budget = budget(profile);
        var results = limit(expanded, budget.searchResults()).stream()
                .map(result -> map(
                        "type", result.type(),
                        "id", result.id(),
                        "label", result.label(),
                        "subtitle", result.subtitle(),
                        "confidence", result.confidence(),
                        "matchedFields", limitText(result.matchedFields(), budget.hints()),
                        "why", result.why(),
                        "links", entityLinks(result.type(), result.id())
                ))
                .toList();
        var sourceRefs = results.stream()
                .map(result -> sourceRefFor(String.valueOf(result.get("type")), String.valueOf(result.get("id"))))
                .filter(Objects::nonNull)
                .map(item -> (Object) item)
                .toList();
        return envelope(
                "operational-context.search",
                1,
                profile,
                map("query", query),
                map(
                        "query", query,
                        "totalResults", expanded.size(),
                        "returnedResults", results.size(),
                        "results", results
                ),
                List.of(new OperationalContextReadModelLinkDto(
                        "expanded",
                        "/api/operational-context/search?q=" + encode(query) + "&profile=expanded",
                        PROFILE_EXPANDED,
                        "Return full search result list for UI or exhaustive diagnostics."
                )),
                List.of("profile=expanded"),
                List.of("Open the best matching entity detail with profile=default before choosing heavier read models."),
                List.of("opctx_get_entity"),
                "Expand search when recall matters more than a short ranked candidate set.",
                List.of("Default search returns top candidates and omits the long tail."),
                truncation("search results limited for default profile", counts(
                        "results", results.size(),
                        "sourceRefs", sourceRefs.size()
                ), counts(
                        "results", expanded.size(),
                        "sourceRefs", expanded.size()
                )),
                relevanceScore(expanded.stream().findFirst().map(OperationalContextSearchResultDto::confidence).orElse(null)),
                expanded.stream().findFirst().map(OperationalContextSearchResultDto::confidence).orElse("unknown"),
                List.of("Search results are lexical/ranked hints; use entity reads for provenance and relations."),
                null,
                sourceRefs,
                List.of()
        );
    }

    OperationalContextProfiledReadModelDto entity(OperationalContextEntityDetailDto expanded, String requestedProfile) {
        var profile = normalizeProfile(requestedProfile);
        var budget = budget(profile);
        var relatedEntities = compactGroups(expanded.relatedEntities(), budget.groups(), budget.groupItems());
        var recognitionSignals = compactGroups(expanded.recognitionSignals(), budget.groups(), budget.groupItems());
        var explainability = limit(expanded.explainabilitySections().stream()
                .map(section -> explainabilitySummary(section, budget))
                .toList(), budget.groups());
        var validation = validationDtos(expanded.validationFindings(), budget.validationFindings());
        var openQuestions = limit(expanded.openQuestions().stream()
                .map(this::openQuestionSummary)
                .toList(), budget.validationFindings());
        var sourceRefs = sourceRefsFromDto(expanded.sourceReferences(), budget.sourceRefs());
        var data = map(
                "entity", map(
                        "type", expanded.type(),
                        "id", expanded.id(),
                        "title", expanded.title(),
                        "subtitle", expanded.subtitle()
                ),
                "overviewSections", limit(expanded.overviewSections().stream()
                        .map(this::overviewSectionSummary)
                        .toList(), budget.groups()),
                "relatedEntities", relatedEntities,
                "recognitionSignals", recognitionSignals,
                "explainability", explainability,
                "validationSummary", validationSummaryDto(expanded.validationFindings()),
                "openQuestions", openQuestions
        );
        var omitted = new ArrayList<String>();
        omitted.add("rawSourcePreview is available only in expanded profile.");
        if (hasTruncation(expanded.relatedEntities().size(), relatedEntities.size())) {
            omitted.add("Related entity groups were limited to the highest-signal groups.");
        }
        if (hasTruncation(expanded.recognitionSignals().size(), recognitionSignals.size())) {
            omitted.add("Recognition signal groups were limited to reduce repeated labels.");
        }
        var availableExpansions = supportsReadModels(expanded.type())
                ? List.of("profile=expanded", "relations", "code-search", "implementations", "flow", "blast-radius")
                : List.of("profile=expanded");
        return envelope(
                "operational-context.entity-detail",
                1,
                profile,
                ref(expanded.type(), expanded.id(), expanded.title(), null, expanded.subtitle()),
                data,
                entityLinks(expanded.type(), expanded.id()),
                availableExpansions,
                entityNextReads(expanded.type(), expanded.id()),
                List.of("opctx_get_entity", "opctx_search"),
                "Expand entity detail when FE or maintenance needs rawSourcePreview or full explainability groups.",
                omitted,
                truncation("entity detail compacted for analysis", counts(
                        "relatedEntityGroups", relatedEntities.size(),
                        "recognitionSignalGroups", recognitionSignals.size(),
                        "sourceRefs", sourceRefs.size(),
                        "validationFindings", validation.size()
                ), counts(
                        "relatedEntityGroups", expanded.relatedEntities().size(),
                        "recognitionSignalGroups", expanded.recognitionSignals().size(),
                        "sourceRefs", expanded.sourceReferences().size(),
                        "validationFindings", expanded.validationFindings().size()
                )),
                1.0,
                confidenceFromValidationDto(expanded.validationFindings()),
                List.of("Entity default is an envelope; use read-model endpoints for relation, flow and blast-radius reasoning."),
                null,
                sourceRefs,
                validation
        );
    }

    OperationalContextProfiledReadModelDto relations(
            OperationalContextEntityRelationsReadModelDto expanded,
            String requestedProfile
    ) {
        var profile = normalizeProfile(requestedProfile);
        var budget = budget(profile);
        var sortedOutgoingRelations = expanded.outgoingRelations().stream()
                .sorted(this::compareRelations)
                .toList();
        var sortedIncomingRelations = expanded.incomingRelations().stream()
                .sorted(this::compareRelations)
                .toList();
        var relationScoresByNeighbor = relationScoresByNeighbor(
                expanded.analysisTarget(),
                combine(expanded.outgoingRelations(), expanded.incomingRelations())
        );
        var outgoing = limit(sortedOutgoingRelations.stream()
                .map(relation -> relationSummary(relation, expanded.analysisTarget()))
                .toList(), budget.relationsPerDirection());
        var incoming = limit(sortedIncomingRelations.stream()
                .map(relation -> relationSummary(relation, expanded.analysisTarget()))
                .toList(), budget.relationsPerDirection());
        var neighbors = limit(expanded.neighbors().stream()
                .sorted((left, right) -> compareEntityRefsByRelationScore(left, right, relationScoresByNeighbor))
                .map(this::entityRefSummary)
                .toList(), budget.neighbors());
        var sourceRefs = sourceRefsFromRelations(combine(expanded.outgoingRelations(), expanded.incomingRelations()), budget.sourceRefs());
        var validation = validationFindings(expanded.validationFindings(), budget.validationFindings());
        var topRelations = limit(combine(sortedOutgoingRelations, sortedIncomingRelations), 4);
        return envelope(
                expanded.contract(),
                expanded.contractVersion(),
                profile,
                expanded.analysisTarget(),
                map(
                        "outgoingRelations", outgoing,
                        "incomingRelations", incoming,
                        "neighbors", neighbors,
                        "relationCounts", map(
                                "outgoing", expanded.outgoingRelations().size(),
                                "incoming", expanded.incomingRelations().size(),
                                "neighbors", expanded.neighbors().size()
                        )
                ),
                readModelLinks(expanded.analysisTarget(), "relations"),
                List.of("profile=expanded", "code-search", "implementations", "flow", "blast-radius"),
                relationNextReads(expanded.analysisTarget(), topRelations),
                List.of("opctx_get_entity"),
                "Expand relations when complete graph traversal is needed.",
                List.of("Default keeps top relations by canonical provenance, directness and confidence."),
                truncation("relations limited by relevance", counts(
                        "outgoingRelations", outgoing.size(),
                        "incomingRelations", incoming.size(),
                        "neighbors", neighbors.size(),
                        "sourceRefs", sourceRefs.size(),
                        "validationFindings", validation.size()
                ), counts(
                        "outgoingRelations", expanded.outgoingRelations().size(),
                        "incomingRelations", expanded.incomingRelations().size(),
                        "neighbors", expanded.neighbors().size(),
                        "sourceRefs", countRelationSourceRefs(combine(expanded.outgoingRelations(), expanded.incomingRelations())),
                        "validationFindings", expanded.validationFindings().size()
                )),
                topRelationScore(combine(expanded.outgoingRelations(), expanded.incomingRelations())),
                aggregateConfidence(combine(expanded.outgoingRelations(), expanded.incomingRelations()).stream()
                        .map(ReadModelRelation::provenance)
                        .toList()),
                List.of("Derived relations are read-model projections; sourceRefs show catalog provenance."),
                null,
                sourceRefs,
                validation
        );
    }

    OperationalContextProfiledReadModelDto codeSearch(
            OperationalContextCodeSearchReadModel expanded,
            String requestedProfile
    ) {
        var profile = normalizeProfile(requestedProfile);
        var budget = budget(profile);
        var scopes = limit(expanded.scopes().stream()
                .sorted(Comparator.comparing(scope -> scope.scope().id()))
                .map(scope -> scopeSummary(scope, budget))
                .toList(), budget.scopes());
        var repositories = limit(expanded.repositories().stream()
                .sorted(this::compareRepositories)
                .map(repository -> repositorySummary(repository, budget))
                .toList(), budget.repositories());
        var sourceRefs = sourceRefsFromProvenances(combine(
                expanded.scopes().stream().map(CodeSearchScopeView::provenance).toList(),
                expanded.repositories().stream().map(RepositoryView::provenance).toList()
        ), budget.sourceRefs());
        var validation = validationFindings(expanded.validationFindings(), budget.validationFindings());
        return envelope(
                expanded.contract(),
                expanded.contractVersion(),
                profile,
                expanded.analysisTarget(),
                map(
                        "scopes", scopes,
                        "repositories", repositories,
                        "aggregatedHints", compactHints(expanded.aggregatedHints(), budget.hints()),
                        "repositoryCount", expanded.repositories().size(),
                        "scopeCount", expanded.scopes().size()
                ),
                readModelLinks(expanded.analysisTarget(), "code-search"),
                List.of("profile=expanded", "implementations", "flow", "blast-radius"),
                List.of(
                        "Use implementations default to distinguish primary, legacy, target and support code.",
                        "Use GitLab repository search only inside returned repository/projectPath scope."
                ),
                List.of("opctx_get_entity", "gitlab_search_repository_candidates"),
                "Expand code-search when full module/source layout or all hint classes are required.",
                List.of("Default keeps top scopes, repositories and hint values instead of repeating every module hint."),
                truncation("code-search scopes, repositories and hints compacted", counts(
                        "scopes", scopes.size(),
                        "repositories", repositories.size(),
                        "sourceRefs", sourceRefs.size(),
                        "validationFindings", validation.size()
                ), counts(
                        "scopes", expanded.scopes().size(),
                        "repositories", expanded.repositories().size(),
                        "sourceRefs", countProvenanceSourceRefs(combine(
                                expanded.scopes().stream().map(CodeSearchScopeView::provenance).toList(),
                                expanded.repositories().stream().map(RepositoryView::provenance).toList()
                        )),
                        "validationFindings", expanded.validationFindings().size()
                )),
                1.0,
                aggregateConfidence(combine(
                        expanded.scopes().stream().map(CodeSearchScopeView::provenance).toList(),
                        expanded.repositories().stream().map(RepositoryView::provenance).toList()
                )),
                expanded.limitations(),
                null,
                sourceRefs,
                validation
        );
    }

    OperationalContextProfiledReadModelDto implementations(
            OperationalContextImplementationReadModel expanded,
            String requestedProfile
    ) {
        var profile = normalizeProfile(requestedProfile);
        var budget = budget(profile);
        var implementations = limit(expanded.implementations().stream()
                .sorted(this::compareImplementations)
                .map(implementation -> implementationSummary(implementation, budget))
                .toList(), budget.implementations());
        var sourceRefs = sourceRefsFromProvenances(expanded.implementations().stream()
                .map(ImplementationView::provenance)
                .toList(), budget.sourceRefs());
        var validation = validationFindings(expanded.validationFindings(), budget.validationFindings());
        return envelope(
                expanded.contract(),
                expanded.contractVersion(),
                profile,
                expanded.analysisTarget(),
                map(
                        "implementations", implementations,
                        "implementationCount", expanded.implementations().size(),
                        "lifecycleRoleCounts", lifecycleRoleCounts(expanded.implementations())
                ),
                readModelLinks(expanded.analysisTarget(), "implementations"),
                List.of("profile=expanded", "code-search", "flow", "blast-radius"),
                List.of(
                        "Use flow default to see where the highest-priority implementations participate.",
                        "Use code-search default before fetching code from GitLab."
                ),
                List.of("opctx_get_entity", "gitlab_search_repository_candidates"),
                "Expand implementations for full hint graph, metadata and all implementation cards.",
                List.of("Default implementation cards omit duplicated full code-search hints."),
                truncation("implementation cards limited and hint graph omitted", counts(
                        "implementations", implementations.size(),
                        "sourceRefs", sourceRefs.size(),
                        "validationFindings", validation.size()
                ), counts(
                        "implementations", expanded.implementations().size(),
                        "sourceRefs", countProvenanceSourceRefs(expanded.implementations().stream()
                                .map(ImplementationView::provenance)
                                .toList()),
                        "validationFindings", expanded.validationFindings().size()
                )),
                1.0,
                aggregateConfidence(expanded.implementations().stream()
                        .map(ImplementationView::provenance)
                        .toList()),
                expanded.limitations(),
                null,
                sourceRefs,
                validation
        );
    }

    OperationalContextProfiledReadModelDto flow(
            OperationalContextFlowReadModel expanded,
            String requestedProfile
    ) {
        var profile = normalizeProfile(requestedProfile);
        var budget = budget(profile);
        var steps = limit(expanded.steps().stream()
                .sorted(Comparator.comparingInt(FlowStepView::order))
                .map(step -> flowStepSummary(step, budget))
                .toList(), budget.steps());
        var edges = limit(expanded.edges().stream()
                .map(this::edgeSummary)
                .toList(), budget.edges());
        var sourceRefs = sourceRefsFromProvenances(combine(
                List.of(expanded.trigger().provenance()),
                expanded.steps().stream().map(FlowStepView::provenance).toList(),
                expanded.edges().stream().map(FlowEdgeView::provenance).toList()
        ), budget.sourceRefs());
        var validation = validationFindings(expanded.validationFindings(), budget.validationFindings());
        return envelope(
                expanded.contract(),
                expanded.contractVersion(),
                profile,
                expanded.analysisTarget(),
                map(
                        "trigger", triggerSummary(expanded, budget),
                        "steps", steps,
                        "edges", edges,
                        "stepCount", expanded.steps().size(),
                        "involved", map(
                                "systems", refs(expanded.involvedSystems(), budget.neighbors()),
                                "boundedContexts", refs(expanded.involvedBoundedContexts(), budget.neighbors()),
                                "integrations", refs(expanded.involvedIntegrations(), budget.neighbors()),
                                "dataStores", refs(expanded.involvedDataStores(), budget.neighbors())
                        )
                ),
                readModelLinks(expanded.analysisTarget(), "flow"),
                List.of("profile=expanded", "code-search", "implementations", "blast-radius"),
                List.of(
                        "Use blast-radius default with the most specific endpoint/class/table/queue/topic signal.",
                        "Use implementations default for code ownership before GitLab reads."
                ),
                List.of("opctx_get_entity", "gitlab_search_repository_candidates"),
                "Expand flow when all per-step implementation refs or complete edge detail are needed.",
                List.of("Default keeps ordered step summaries and compact implementation refs; heavy hints stay expanded."),
                truncation("flow steps, edges and per-step hints compacted", counts(
                        "steps", steps.size(),
                        "edges", edges.size(),
                        "sourceRefs", sourceRefs.size(),
                        "validationFindings", validation.size()
                ), counts(
                        "steps", expanded.steps().size(),
                        "edges", expanded.edges().size(),
                        "sourceRefs", countProvenanceSourceRefs(combine(
                                List.of(expanded.trigger().provenance()),
                                expanded.steps().stream().map(FlowStepView::provenance).toList(),
                                expanded.edges().stream().map(FlowEdgeView::provenance).toList()
                        )),
                        "validationFindings", expanded.validationFindings().size()
                )),
                1.0,
                aggregateConfidence(combine(
                        List.of(expanded.trigger().provenance()),
                        expanded.steps().stream().map(FlowStepView::provenance).toList()
                )),
                expanded.limitations(),
                null,
                sourceRefs,
                validation
        );
    }

    OperationalContextProfiledReadModelDto blastRadius(
            OperationalContextBlastRadiusReadModel expanded,
            String requestedProfile
    ) {
        var profile = normalizeProfile(requestedProfile);
        var budget = budget(profile);
        var broadSignal = broadSignal(expanded);
        var impactFlowLimit = broadSignal ? 1 : budget.impactFlows();
        var impactNodeLimit = broadSignal ? Math.max(4, budget.impactNodes() / 2) : budget.impactNodes();
        var implementationLimit = broadSignal ? Math.max(3, budget.implementations() / 2) : budget.implementations();
        var stepLimit = broadSignal ? Math.max(4, budget.steps() / 2) : budget.steps();
        var impactedFlows = limit(expanded.impactedFlows().stream()
                .sorted(this::compareFlowImpacts)
                .map(flowImpact -> flowImpactSummary(flowImpact, budget, stepLimit))
                .toList(), impactFlowLimit);
        var impactedSystems = impactNodes(expanded.impactedSystems(), impactNodeLimit);
        var impactedContexts = impactNodes(expanded.impactedBoundedContexts(), impactNodeLimit);
        var impactedIntegrations = impactNodes(expanded.impactedIntegrations(), impactNodeLimit);
        var impactedDataStores = impactNodes(expanded.impactedDataStores(), impactNodeLimit);
        var implementations = limit(expanded.impactedImplementations().stream()
                .sorted(this::compareImplementationImpacts)
                .map(impact -> implementationImpactSummary(impact, budget))
                .toList(), implementationLimit);
        var sourceRefs = sourceRefsFromProvenances(combine(
                expanded.impactedFlows().stream().map(FlowImpactView::provenance).toList(),
                expanded.impactedSystems().stream().map(ImpactNodeView::provenance).toList(),
                expanded.impactedBoundedContexts().stream().map(ImpactNodeView::provenance).toList(),
                expanded.impactedIntegrations().stream().map(ImpactNodeView::provenance).toList(),
                expanded.impactedDataStores().stream().map(ImpactNodeView::provenance).toList()
        ), budget.sourceRefs());
        var validation = validationFindings(expanded.validationFindings(), budget.validationFindings());
        var limitations = new ArrayList<>(expanded.limitations());
        if (broadSignal) {
            limitations.add("Broad non-catalog signal matched many implementations; use a more specific endpoint, package, table, queue or topic before code reads.");
        }
        var sortedFlowImpacts = expanded.impactedFlows().stream()
                .sorted(this::compareFlowImpacts)
                .toList();
        var sortedImplementationImpacts = expanded.impactedImplementations().stream()
                .sorted(this::compareImplementationImpacts)
                .toList();
        return envelope(
                expanded.contract(),
                expanded.contractVersion(),
                profile,
                expanded.analysisTarget(),
                map(
                        "impactedFlows", impactedFlows,
                        "impactedNodes", map(
                                "systems", impactedSystems,
                                "boundedContexts", impactedContexts,
                                "integrations", impactedIntegrations,
                                "dataStores", impactedDataStores
                        ),
                        "impactedImplementations", implementations,
                        "suggestedNextEvidence", limitText(expanded.suggestedNextEvidence(), budget.hints()),
                        "impactCounts", map(
                                "flows", expanded.impactedFlows().size(),
                                "systems", expanded.impactedSystems().size(),
                                "boundedContexts", expanded.impactedBoundedContexts().size(),
                                "integrations", expanded.impactedIntegrations().size(),
                                "dataStores", expanded.impactedDataStores().size(),
                                "implementations", expanded.impactedImplementations().size()
                        )
                ),
                blastRadiusLinks(expanded.analysisTarget()),
                List.of("profile=expanded", "flow", "implementations", "code-search"),
                blastRadiusNextReads(expanded.analysisTarget(), sortedFlowImpacts, sortedImplementationImpacts, broadSignal),
                List.of("opctx_get_entity", "gitlab_search_repository_candidates"),
                "Expand blast-radius only after the compact impact graph identifies a narrow target.",
                List.of("Default compresses impacted nodes and implementation refs; heavy code refs stay expanded."),
                truncation(
                        broadSignal
                                ? "broad non-catalog signal guard limited blast-radius output"
                                : "blast-radius impact graph compacted",
                        counts(
                                "impactedFlows", impactedFlows.size(),
                                "impactedSystems", impactedSystems.size(),
                                "impactedBoundedContexts", impactedContexts.size(),
                                "impactedIntegrations", impactedIntegrations.size(),
                                "impactedDataStores", impactedDataStores.size(),
                                "impactedImplementations", implementations.size(),
                                "sourceRefs", sourceRefs.size(),
                                "validationFindings", validation.size()
                        ),
                        counts(
                                "impactedFlows", expanded.impactedFlows().size(),
                                "impactedSystems", expanded.impactedSystems().size(),
                                "impactedBoundedContexts", expanded.impactedBoundedContexts().size(),
                                "impactedIntegrations", expanded.impactedIntegrations().size(),
                                "impactedDataStores", expanded.impactedDataStores().size(),
                                "impactedImplementations", expanded.impactedImplementations().size(),
                                "sourceRefs", countProvenanceSourceRefs(combine(
                                        expanded.impactedFlows().stream().map(FlowImpactView::provenance).toList(),
                                        expanded.impactedSystems().stream().map(ImpactNodeView::provenance).toList(),
                                        expanded.impactedBoundedContexts().stream().map(ImpactNodeView::provenance).toList(),
                                        expanded.impactedIntegrations().stream().map(ImpactNodeView::provenance).toList(),
                                        expanded.impactedDataStores().stream().map(ImpactNodeView::provenance).toList()
                                )),
                                "validationFindings", expanded.validationFindings().size()
                        )
                ),
                blastRadiusRelevanceScore(expanded, broadSignal),
                aggregateConfidence(combine(
                        expanded.impactedFlows().stream().map(FlowImpactView::provenance).toList(),
                        expanded.impactedSystems().stream().map(ImpactNodeView::provenance).toList(),
                        expanded.impactedIntegrations().stream().map(ImpactNodeView::provenance).toList()
                )),
                limitations,
                null,
                sourceRefs,
                validation
        );
    }

    private OperationalContextProfiledReadModelDto envelope(
            String contract,
            int contractVersion,
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
            Double relevanceScore,
            String confidence,
            List<String> limitations,
            Object provenance,
            List<Object> sourceRefs,
            List<Object> validationFindings
    ) {
        var effectiveProvenance = provenance != null
                ? provenance
                : defaultProvenance(sourceRefs, validationFindings);
        return new OperationalContextProfiledReadModelDto(
                contract,
                contractVersion,
                profile,
                analysisTarget,
                data,
                links,
                availableExpansions,
                suggestedNextReads,
                nextReadsFrom(links, suggestedNextReads),
                suggestedTools,
                reasonToExpand,
                omittedBecause,
                truncation,
                relevanceScore,
                confidence,
                limitations,
                effectiveProvenance,
                sourceRefs,
                validationFindings
        );
    }

    private String normalizeProfile(String profile) {
        if (!StringUtils.hasText(profile)) {
            return PROFILE_EXPANDED;
        }
        var normalized = profile.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case PROFILE_INDEX, PROFILE_SUMMARY, PROFILE_DEFAULT, PROFILE_EXPANDED -> normalized;
            default -> PROFILE_DEFAULT;
        };
    }

    private ProfileBudget budget(String profile) {
        return switch (normalizeProfile(profile)) {
            case PROFILE_INDEX -> new ProfileBudget(2, 4, 2, 3, 3, 2, 4, 1, 4, 3, 6, 3, 3, 4, 6, 6);
            case PROFILE_SUMMARY -> new ProfileBudget(4, 6, 3, 5, 6, 4, 6, 2, 6, 5, 10, 4, 4, 6, 8, 8);
            default -> new ProfileBudget(8, 10, 5, 8, 12, 8, 12, 3, 10, 8, 18, 8, 6, 10, 16, 10);
        };
    }

    private Map<String, Object> healthCardSummary(ExplainableAggregateDto card) {
        return map(
                "label", card.label(),
                "count", card.count(),
                "severity", card.severity(),
                "confidence", card.confidence(),
                "detailsType", card.detailsType(),
                "detailsIdsCount", card.detailsIds().size(),
                "sourceRefCount", card.sourceRefs().size()
        );
    }

    private Map<String, Object> overviewSectionSummary(OperationalContextDetailSectionDto section) {
        return map(
                "title", section.title(),
                "fields", section.fields()
        );
    }

    private Map<String, Object> explainabilitySummary(
            OperationalContextExplainabilitySectionDto section,
            ProfileBudget budget
    ) {
        return map(
                "title", section.title(),
                "summary", section.summary(),
                "confidence", section.confidence(),
                "reasons", limit(section.reasons(), budget.groupItems()),
                "warnings", limitText(section.warnings(), budget.groupItems()),
                "sourceRefCount", section.sourceRefs().size()
        );
    }

    private List<Map<String, Object>> compactGroups(
            List<ExplainableBreakdownGroupDto> groups,
            int groupLimit,
            int itemLimit
    ) {
        return limit(groups, groupLimit).stream()
                .map(group -> map(
                        "label", group.label(),
                        "count", group.count(),
                        "items", limit(group.items(), itemLimit).stream()
                                .map(this::breakdownItemSummary)
                                .toList(),
                        "omittedItems", Math.max(0, group.items().size() - itemLimit)
                ))
                .toList();
    }

    private Map<String, Object> breakdownItemSummary(ExplainableBreakdownItemDto item) {
        return map(
                "id", item.id(),
                "label", item.label(),
                "kind", item.kind(),
                "reason", item.reason(),
                "status", item.status(),
                "sourceRefCount", item.sourceRefs().size()
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

    private Map<String, Object> relationSummary(ReadModelRelation relation, EntityRef analysisTarget) {
        var peer = relationPeer(relation, analysisTarget);
        return map(
                "relationType", relation.relationType(),
                "direction", relation.direction(),
                "source", keySummary(relation.source()),
                "target", keySummary(relation.target()),
                "peer", keySummary(peer),
                "role", relation.role(),
                "canonicalOwner", keySummary(relation.canonicalOwner()),
                "derived", relation.derived(),
                "confidence", relation.provenance().confidence(),
                "relevanceScore", relationRelevanceScore(relation),
                "reasonToRead", relationReasonToRead(relation),
                "suggestedNextRead", peer != null ? "GET " + entityHref(peer.type(), peer.id(), PROFILE_DEFAULT) : null,
                "provenance", provenanceSummary(relation.provenance())
        );
    }

    private Map<String, Object> scopeSummary(CodeSearchScopeView scope, ProfileBudget budget) {
        return map(
                "scope", entityRefSummary(scope.scope()),
                "scopeType", scope.scopeType(),
                "target", entityRefSummary(scope.target()),
                "repositories", refs(scope.repositories(), budget.repositories()),
                "hints", compactHints(scope.hints(), budget.hints()),
                "traversal", map(
                        "rules", limitText(scope.traversal().rules(), budget.groupItems()),
                        "expandWhen", limitText(scope.traversal().expandWhen(), budget.groupItems())
                ),
                "limitations", limitText(scope.limitations(), budget.groupItems()),
                "provenance", provenanceSummary(scope.provenance())
        );
    }

    private Map<String, Object> repositorySummary(RepositoryView repository, ProfileBudget budget) {
        return map(
                "repository", entityRefSummary(repository.repository()),
                "role", repository.role(),
                "priority", repository.priority(),
                "reason", repository.reason(),
                "readFor", limitText(repository.readFor(), budget.groupItems()),
                "git", map(
                        "provider", repository.git().provider(),
                        "group", repository.git().group(),
                        "project", repository.git().project(),
                        "projectPath", repository.git().projectPath(),
                        "defaultBranch", repository.git().defaultBranch()
                ),
                "sourceRoots", limitText(repository.sourceLayout().sourceRoots(), budget.groupItems()),
                "importantPaths", limitText(repository.sourceLayout().importantPaths(), budget.groupItems()),
                "modules", limit(repository.modules(), Math.max(1, budget.groupItems())).stream()
                        .map(module -> moduleSummary(module, budget))
                        .toList(),
                "hints", compactHints(repository.hints(), budget.hints()),
                "provenance", provenanceSummary(repository.provenance())
        );
    }

    private Map<String, Object> moduleSummary(ModuleView module, ProfileBudget budget) {
        return map(
                "id", module.id(),
                "name", module.name(),
                "moduleType", module.moduleType(),
                "lifecycleStatus", module.lifecycleStatus(),
                "paths", limitText(module.paths(), budget.groupItems()),
                "packages", limitText(module.packages(), budget.groupItems()),
                "sourceRoots", limitText(module.sourceRoots(), budget.groupItems()),
                "importantPaths", limitText(module.importantPaths(), budget.groupItems()),
                "hints", compactHints(module.hints(), Math.max(2, budget.hints() / 3))
        );
    }

    private Map<String, Object> implementationSummary(ImplementationView implementation, ProfileBudget budget) {
        return map(
                "id", implementation.id(),
                "kind", implementation.implementationKind(),
                "lifecycleRole", implementation.lifecycleRole(),
                "migrationStatus", implementation.migrationStatus(),
                "implementationRole", implementation.implementationRole(),
                "priority", implementation.priority(),
                "repository", entityRefSummary(implementation.repository()),
                "codeSearchScope", entityRefSummary(implementation.codeSearchScope()),
                "module", moduleImplementationSummary(implementation.module(), budget),
                "systems", refs(implementation.systems(), budget.groupItems()),
                "boundedContexts", refs(implementation.boundedContexts(), budget.groupItems()),
                "processes", refs(implementation.processes(), budget.groupItems()),
                "packagePrefixes", limitText(implementation.packagePrefixes(), budget.groupItems()),
                "sourceRoots", limitText(implementation.sourceRoots(), budget.groupItems()),
                "importantPaths", limitText(implementation.importantPaths(), budget.groupItems()),
                "hintSummary", hintCounts(implementation.hints()),
                "provenance", provenanceSummary(implementation.provenance())
        );
    }

    private Map<String, Object> moduleImplementationSummary(
            OperationalContextImplementationReadModel.ModuleImplementationView module,
            ProfileBudget budget
    ) {
        if (module == null) {
            return Map.of();
        }
        return map(
                "id", module.id(),
                "name", module.name(),
                "moduleType", module.moduleType(),
                "lifecycleStatus", module.lifecycleStatus(),
                "paths", limitText(module.paths(), budget.groupItems()),
                "packages", limitText(module.packages(), budget.groupItems()),
                "sourceRoots", limitText(module.sourceRoots(), budget.groupItems()),
                "importantPaths", limitText(module.importantPaths(), budget.groupItems())
        );
    }

    private Map<String, Object> triggerSummary(OperationalContextFlowReadModel expanded, ProfileBudget budget) {
        var trigger = expanded.trigger();
        return map(
                "kind", trigger.kind(),
                "channel", trigger.channel(),
                "endpoints", limitText(trigger.endpoints(), budget.hints()),
                "queues", limitText(trigger.queues(), budget.hints()),
                "topics", limitText(trigger.topics(), budget.hints()),
                "sources", refs(trigger.sources(), budget.groupItems()),
                "targets", refs(trigger.targets(), budget.groupItems()),
                "provenance", provenanceSummary(trigger.provenance())
        );
    }

    private Map<String, Object> flowStepSummary(FlowStepView step, ProfileBudget budget) {
        return map(
                "id", step.id(),
                "order", step.order(),
                "name", step.name(),
                "kind", step.kind(),
                "summary", step.summary(),
                "systems", refs(step.systems(), budget.groupItems()),
                "boundedContexts", refs(step.boundedContexts(), budget.groupItems()),
                "integrations", refs(step.integrations(), budget.groupItems()),
                "dataStores", refs(step.dataStores(), budget.groupItems()),
                "codeSearchScopes", refs(step.codeSearchScopes(), budget.groupItems()),
                "endpointHints", limitText(step.endpointHints(), budget.hints()),
                "queueTopicHints", limitText(step.queueTopicHints(), budget.hints()),
                "classHints", limitText(step.classHints(), budget.hints()),
                "codeHintSummary", hintCounts(step.codeHints()),
                "integrationHints", map(
                        "protocols", limitText(step.integrationHints().protocols(), budget.groupItems()),
                        "methods", limitText(step.integrationHints().methods(), budget.groupItems()),
                        "endpoints", limitText(step.integrationHints().endpoints(), budget.hints()),
                        "queues", limitText(step.integrationHints().queues(), budget.hints()),
                        "topics", limitText(step.integrationHints().topics(), budget.hints()),
                        "tables", limitText(step.integrationHints().tables(), budget.hints()),
                        "classHints", limitText(step.integrationHints().classHints(), budget.hints()),
                        "failureModes", limitText(step.integrationHints().failureModes(), budget.groupItems())
                ),
                "implementations", limit(step.implementations(), Math.max(1, budget.groupItems())).stream()
                        .map(implementation -> implementationRefSummary(implementation, false, budget))
                        .toList(),
                "gaps", limitText(step.gaps(), budget.groupItems()),
                "provenance", provenanceSummary(step.provenance())
        );
    }

    private Map<String, Object> edgeSummary(FlowEdgeView edge) {
        return map(
                "sourceStepId", edge.sourceStepId(),
                "targetStepId", edge.targetStepId(),
                "relationType", edge.relationType(),
                "viaEntities", refs(edge.viaEntities(), 4),
                "provenance", provenanceSummary(edge.provenance())
        );
    }

    private Map<String, Object> flowImpactSummary(
            FlowImpactView flowImpact,
            ProfileBudget budget,
            int stepLimit
    ) {
        return map(
                "flow", entityRefSummary(flowImpact.flow()),
                "confidence", flowImpact.confidence(),
                "relevanceScore", flowImpactRelevanceScore(flowImpact),
                "reasonToRead", flowImpactReasonToRead(flowImpact),
                "reasons", limitText(flowImpact.reasons(), budget.groupItems()),
                "impactedSteps", limit(flowImpact.impactedSteps().stream()
                        .sorted(Comparator.comparingInt(StepImpactView::order))
                        .map(step -> stepImpactSummary(step, budget))
                        .toList(), stepLimit),
                "downstreamEdgeCount", flowImpact.downstreamEdges().size(),
                "provenance", provenanceSummary(flowImpact.provenance())
        );
    }

    private Map<String, Object> stepImpactSummary(StepImpactView step, ProfileBudget budget) {
        return map(
                "stepId", step.stepId(),
                "order", step.order(),
                "name", step.name(),
                "kind", step.kind(),
                "impactType", step.impactType(),
                "relevanceScore", stepImpactRelevanceScore(step),
                "reasonToRead", stepImpactReasonToRead(step),
                "reasons", limitText(step.reasons(), budget.groupItems()),
                "systems", refs(step.systems(), budget.groupItems()),
                "boundedContexts", refs(step.boundedContexts(), budget.groupItems()),
                "integrations", refs(step.integrations(), budget.groupItems()),
                "dataStores", refs(step.dataStores(), budget.groupItems()),
                "implementations", limit(step.implementations(), Math.max(1, budget.groupItems())).stream()
                        .map(implementation -> implementationRefSummary(implementation, false, budget))
                        .toList()
        );
    }

    private Map<String, Object> implementationRefSummary(
            ImplementationRef implementation,
            boolean includeHints,
            ProfileBudget budget
    ) {
        return map(
                "id", implementation.id(),
                "lifecycleRole", implementation.lifecycleRole(),
                "migrationStatus", implementation.migrationStatus(),
                "codeSearchScope", entityRefSummary(implementation.codeSearchScope()),
                "repository", entityRefSummary(implementation.repository()),
                "module", includeHints
                        ? moduleImplementationSummary(implementation.module(), budget)
                        : compactModuleImplementationSummary(implementation.module()),
                "packagePrefixes", includeHints ? limitText(implementation.packagePrefixes(), budget.groupItems()) : null,
                "sourceRoots", includeHints ? limitText(implementation.sourceRoots(), budget.groupItems()) : null,
                "hints", includeHints ? compactHints(implementation.hints(), Math.max(2, budget.hints() / 3)) : null,
                "hintSummary", includeHints ? null : hintCounts(implementation.hints())
        );
    }

    private Map<String, Object> compactModuleImplementationSummary(
            OperationalContextImplementationReadModel.ModuleImplementationView module
    ) {
        if (module == null) {
            return Map.of();
        }
        return map(
                "id", module.id(),
                "name", module.name(),
                "lifecycleStatus", module.lifecycleStatus()
        );
    }

    private Map<String, Object> impactNodeSummary(ImpactNodeView node) {
        return map(
                "entity", entityRefSummary(node.entity()),
                "impactType", node.impactType(),
                "direction", node.direction(),
                "criticality", node.criticality(),
                "confidence", node.confidence(),
                "relevanceScore", impactNodeRelevanceScore(node),
                "reasonToRead", impactNodeReasonToRead(node),
                "reasons", limitText(node.reasons(), 3),
                "provenance", provenanceSummary(node.provenance())
        );
    }

    private Map<String, Object> implementationImpactSummary(ImplementationImpactView impact, ProfileBudget budget) {
        return map(
                "implementation", implementationRefSummary(impact.implementation(), false, budget),
                "impactType", impact.impactType(),
                "confidence", impact.confidence(),
                "relevanceScore", implementationImpactRelevanceScore(impact),
                "reasonToRead", implementationImpactReasonToRead(impact),
                "reasons", limitText(impact.reasons(), budget.groupItems())
        );
    }

    private Map<String, Object> compactHints(CodeSearchHints hints, int limit) {
        if (hints == null) {
            return Map.of();
        }
        return map(
                "packagePrefixes", limitText(hints.packagePrefixes(), limit),
                "classHints", limitText(hints.classHints(), limit),
                "endpointHints", limitText(hints.endpointHints(), limit),
                "queueTopicHints", limitText(hints.queueTopicHints(), limit),
                "database", map(
                        "datasourceNames", limitText(hints.databaseHints().datasourceNames(), limit),
                        "schemas", limitText(hints.databaseHints().schemas(), limit),
                        "tables", limitText(hints.databaseHints().tables(), limit),
                        "entities", limitText(hints.databaseHints().entities(), limit),
                        "migrations", limitText(hints.databaseHints().migrations(), limit)
                ),
                "workflow", map(
                        "jobNames", limitText(hints.workflowHints().jobNames(), limit),
                        "workflowNames", limitText(hints.workflowHints().workflowNames(), limit),
                        "definitionPaths", limitText(hints.workflowHints().definitionPaths(), limit)
                )
        );
    }

    private Map<String, Object> hintCounts(CodeSearchHints hints) {
        if (hints == null) {
            return Map.of();
        }
        return map(
                "packagePrefixes", hints.packagePrefixes().size(),
                "classHints", hints.classHints().size(),
                "endpointHints", hints.endpointHints().size(),
                "queueTopicHints", hints.queueTopicHints().size(),
                "database", hints.databaseHints().tables().size()
                        + hints.databaseHints().entities().size()
                        + hints.databaseHints().migrations().size(),
                "workflow", hints.workflowHints().jobNames().size()
                        + hints.workflowHints().workflowNames().size()
                        + hints.workflowHints().definitionPaths().size()
        );
    }

    private List<Map<String, Object>> impactNodes(List<ImpactNodeView> nodes, int limit) {
        return limit(nodes.stream()
                .sorted(this::compareImpactNodes)
                .map(this::impactNodeSummary)
                .toList(), limit);
    }

    private Map<String, Object> entityRefSummary(EntityRef ref) {
        if (ref == null) {
            return Map.of();
        }
        return map(
                "type", ref.type(),
                "id", ref.id(),
                "label", ref.label(),
                "lifecycleStatus", ref.lifecycleStatus()
        );
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
        return map(
                "type", key.type(),
                "id", key.id()
        );
    }

    private List<Map<String, Object>> refs(List<EntityRef> refs, int limit) {
        return limit(refs, limit).stream()
                .map(this::entityRefSummary)
                .toList();
    }

    private Map<String, Object> provenanceSummary(Provenance provenance) {
        if (provenance == null) {
            return Map.of();
        }
        return map(
                "canonical", provenance.canonical(),
                "derivation", provenance.derivation(),
                "confidence", provenance.confidence(),
                "sourceRefCount", provenance.sourceRefs().size(),
                "warnings", limitText(provenance.warnings(), 3)
        );
    }

    private Map<String, Object> defaultProvenance(List<Object> sourceRefs, List<Object> validationFindings) {
        return map(
                "sourceRefs", map(
                        "returned", sourceRefs != null ? sourceRefs.size() : 0,
                        "byFile", sourceRefCounts(sourceRefs, "file"),
                        "byTargetType", sourceRefTargetTypeCounts(sourceRefs),
                        "format", "deduped compact refs; use expanded profile for full sourceRef repetition"
                ),
                "validationFindings", map(
                        "returned", validationFindings != null ? validationFindings.size() : 0
                )
        );
    }

    private List<Object> sourceRefsFromAggregates(List<ExplainableAggregateDto> aggregates, int limit) {
        var refs = new ArrayList<SourceReferenceDto>();
        for (var aggregate : aggregates) {
            refs.addAll(aggregate.sourceRefs());
            for (var group : aggregate.groups()) {
                for (var item : group.items()) {
                    refs.addAll(item.sourceRefs());
                }
            }
        }
        return sourceRefsFromDto(refs, limit);
    }

    private List<Object> sourceRefsFromDto(List<SourceReferenceDto> refs, int limit) {
        var deduped = new LinkedHashMap<String, Object>();
        for (var ref : refs) {
            putSourceRef(deduped, sourceRefSummary(
                    ref.file(),
                    ref.path(),
                    StringUtils.hasText(ref.entityId()) ? ref.entityId() : null,
                    null
            ));
        }
        return limit(new ArrayList<>(deduped.values()), limit);
    }

    private List<Object> sourceRefsFromRelations(List<ReadModelRelation> relations, int limit) {
        return sourceRefsFromProvenances(relations.stream().map(ReadModelRelation::provenance).toList(), limit);
    }

    private List<Object> sourceRefsFromProvenances(List<Provenance> provenances, int limit) {
        var deduped = new LinkedHashMap<String, Object>();
        for (var provenance : provenances) {
            if (provenance == null) {
                continue;
            }
            for (var ref : provenance.sourceRefs()) {
                putSourceRef(deduped, sourceRefSummary(ref));
            }
        }
        return limit(new ArrayList<>(deduped.values()), limit);
    }

    private Map<String, Object> sourceRefSummary(SourceRef ref) {
        var target = ref.entityType() + "/" + ref.entityId();
        return sourceRefSummary(ref.file(), ref.fieldPath(), target, ref.relationRole());
    }

    private Map<String, Object> sourceRefSummary(String file, String path, String target, String relationRole) {
        return map(
                "refId", sourceRefId(file, path, target),
                "file", file,
                "path", path,
                "target", target,
                "role", relationRole
        );
    }

    private Map<String, Object> sourceRefFor(String type, String id) {
        if (!StringUtils.hasText(type) || !StringUtils.hasText(id)) {
            return null;
        }
        return sourceRefSummary(sourceFile(type), idPath(type, id), type + "/" + id, null);
    }

    private void putSourceRef(Map<String, Object> deduped, Map<String, Object> ref) {
        if (!ref.isEmpty()) {
            deduped.putIfAbsent(String.valueOf(ref.getOrDefault("refId", ref.toString())), ref);
        }
    }

    private List<Object> validationFindings(List<ValidationFinding> findings, int limit) {
        return limit(findings, limit).stream()
                .map(finding -> (Object) map(
                        "severity", finding.severity(),
                        "code", finding.code(),
                        "message", finding.message(),
                        "sourceRefCount", finding.sourceRefs().size()
                ))
                .toList();
    }

    private List<Object> validationDtos(List<ValidationFindingDto> findings, int limit) {
        return limit(findings, limit).stream()
                .map(finding -> (Object) map(
                        "id", finding.id(),
                        "severity", finding.severity(),
                        "category", finding.category(),
                        "entityType", finding.entityType(),
                        "entityId", finding.entityId(),
                        "title", finding.title(),
                        "impact", finding.impact(),
                        "sourceRefCount", finding.sourceRefs().size()
                ))
                .toList();
    }

    private Map<String, Integer> validationSummaryDto(List<ValidationFindingDto> findings) {
        var counts = counts("info", 0, "warning", 0, "error", 0);
        for (var finding : findings) {
            counts.computeIfPresent(finding.severity(), (ignored, count) -> count + 1);
        }
        return counts;
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
        if (findings.stream().anyMatch(finding -> "error".equals(finding.severity()))) {
            return "low";
        }
        if (findings.stream().anyMatch(finding -> "warning".equals(finding.severity()))) {
            return "medium";
        }
        return "high";
    }

    private String aggregateConfidence(List<Provenance> provenances) {
        var confidenceValues = provenances.stream()
                .filter(Objects::nonNull)
                .map(Provenance::confidence)
                .toList();
        if (confidenceValues.isEmpty()) {
            return "unknown";
        }
        if (confidenceValues.contains("high")) {
            return "high";
        }
        if (confidenceValues.contains("medium")) {
            return "medium";
        }
        return confidenceValues.get(0);
    }

    private Double relevanceScore(String confidence) {
        return switch (confidence == null ? "" : confidence) {
            case "high" -> 0.9;
            case "medium" -> 0.65;
            case "low" -> 0.35;
            default -> 0.5;
        };
    }

    private Map<String, Integer> sourceRefCounts(List<Object> sourceRefs, String fieldName) {
        var counts = new LinkedHashMap<String, Integer>();
        if (sourceRefs == null || sourceRefs.isEmpty()) {
            return counts;
        }
        for (var sourceRef : sourceRefs) {
            if (sourceRef instanceof Map<?, ?> ref) {
                var value = ref.get(fieldName);
                if (value != null && StringUtils.hasText(String.valueOf(value))) {
                    counts.merge(String.valueOf(value), 1, Integer::sum);
                }
            }
        }
        return counts;
    }

    private Map<String, Integer> sourceRefTargetTypeCounts(List<Object> sourceRefs) {
        var counts = new LinkedHashMap<String, Integer>();
        if (sourceRefs == null || sourceRefs.isEmpty()) {
            return counts;
        }
        for (var sourceRef : sourceRefs) {
            if (!(sourceRef instanceof Map<?, ?> ref)) {
                continue;
            }
            var target = ref.get("target");
            if (target == null || !StringUtils.hasText(String.valueOf(target))) {
                continue;
            }
            var text = String.valueOf(target);
            var separator = text.indexOf('/');
            if (separator > 0) {
                counts.merge(text.substring(0, separator), 1, Integer::sum);
            }
        }
        return counts;
    }

    private String sourceRefId(String file, String path, String target) {
        var key = new ArrayList<String>();
        if (StringUtils.hasText(file)) {
            key.add(file.trim());
        }
        if (StringUtils.hasText(path)) {
            key.add(path.trim());
        }
        if (StringUtils.hasText(target)) {
            key.add(target.trim());
        }
        return String.join("#", key);
    }

    private OperationalContextReadModelTruncationDto truncation(
            String reason,
            Map<String, Integer> returnedCounts,
            Map<String, Integer> totalCounts
    ) {
        var omitted = new LinkedHashMap<String, Integer>();
        totalCounts.forEach((key, total) -> {
            var returned = returnedCounts.getOrDefault(key, 0);
            var omittedCount = Math.max(0, total - returned);
            if (omittedCount > 0) {
                omitted.put(key, omittedCount);
            }
        });
        return new OperationalContextReadModelTruncationDto(!omitted.isEmpty(), omitted.isEmpty() ? null : reason, returnedCounts, omitted);
    }

    private List<OperationalContextReadModelNextReadDto> nextReadsFrom(
            List<OperationalContextReadModelLinkDto> links,
            List<String> suggestedNextReads
    ) {
        var safeLinks = links != null ? links : List.<OperationalContextReadModelLinkDto>of();
        var reads = new ArrayList<OperationalContextReadModelNextReadDto>();
        var keys = new LinkedHashSet<String>();

        for (var suggestion : suggestedNextReads != null ? suggestedNextReads : List.<String>of()) {
            var href = hrefFromSuggestion(suggestion);
            var reason = reasonFromSuggestion(suggestion);
            if (StringUtils.hasText(href)) {
                var link = safeLinks.stream()
                        .filter(candidate -> Objects.equals(candidate.href(), href))
                        .findFirst()
                        .orElseGet(() -> linkFromHref(href, reason));
                addNextRead(reads, keys, nextReadFromLink(link, reason));
            } else if (StringUtils.hasText(suggestion)) {
                addNextRead(reads, keys, new OperationalContextReadModelNextReadDto(
                        "Guidance",
                        "guidance",
                        null,
                        null,
                        null,
                        Map.of(),
                        suggestion.trim()
                ));
            }
        }

        if (reads.isEmpty()) {
            safeLinks.stream()
                    .filter(link -> !List.of("self").contains(link.rel()))
                    .limit(6)
                    .map(link -> nextReadFromLink(link, link.reason()))
                    .forEach(read -> addNextRead(reads, keys, read));
        }

        return List.copyOf(reads);
    }

    private void addNextRead(
            List<OperationalContextReadModelNextReadDto> reads,
            LinkedHashSet<String> keys,
            OperationalContextReadModelNextReadDto read
    ) {
        if (read == null) {
            return;
        }
        var key = String.join("|",
                String.valueOf(read.rel()),
                String.valueOf(read.href()),
                String.valueOf(read.tool()),
                String.valueOf(read.arguments())
        );
        if (keys.add(key)) {
            reads.add(read);
        }
    }

    private OperationalContextReadModelNextReadDto nextReadFromLink(
            OperationalContextReadModelLinkDto link,
            String reason
    ) {
        var tool = toolForLink(link);
        var effectiveReason = StringUtils.hasText(reason)
                ? reason
                : StringUtils.hasText(link.reason()) ? link.reason() : "Follow this focused read.";
        return new OperationalContextReadModelNextReadDto(
                labelForRel(link.rel()),
                link.rel(),
                link.href(),
                link.profile(),
                tool,
                argumentsForLink(link, tool),
                effectiveReason
        );
    }

    private OperationalContextReadModelLinkDto linkFromHref(String href, String reason) {
        var rel = relFromHref(href);
        return new OperationalContextReadModelLinkDto(
                rel,
                href,
                queryParameter(href, "profile"),
                StringUtils.hasText(reason) ? reason : "Follow this focused REST read."
        );
    }

    private String hrefFromSuggestion(String suggestion) {
        if (!StringUtils.hasText(suggestion)) {
            return null;
        }
        var trimmed = suggestion.trim();
        if (!trimmed.startsWith("GET ")) {
            return null;
        }
        var withoutMethod = trimmed.substring(4).trim();
        var reasonSeparator = withoutMethod.indexOf(" -- ");
        return reasonSeparator >= 0
                ? withoutMethod.substring(0, reasonSeparator).trim()
                : withoutMethod;
    }

    private String reasonFromSuggestion(String suggestion) {
        if (!StringUtils.hasText(suggestion)) {
            return null;
        }
        var separator = suggestion.indexOf(" -- ");
        if (separator >= 0 && separator + 4 < suggestion.length()) {
            return suggestion.substring(separator + 4).trim();
        }
        return suggestion.startsWith("GET ") ? null : suggestion.trim();
    }

    private String relFromHref(String href) {
        if (!StringUtils.hasText(href)) {
            return "read";
        }
        if (href.contains("/search")) {
            return "search";
        }
        if (href.contains("/entities/") && !href.contains("/read-model/entities/")) {
            return "entity";
        }
        if (href.contains("/read-model/blast-radius")) {
            return "blast-radius";
        }
        var marker = "/read-model/entities/";
        var markerIndex = href.indexOf(marker);
        if (markerIndex >= 0) {
            var afterType = href.indexOf('/', markerIndex + marker.length());
            if (afterType >= 0) {
                var query = href.indexOf('?', afterType);
                return href.substring(afterType + 1, query >= 0 ? query : href.length());
            }
        }
        return "read";
    }

    private String labelForRel(String rel) {
        if (!StringUtils.hasText(rel)) {
            return "Read";
        }
        return Arrays.stream(rel.replace('-', ' ').split(" "))
                .filter(StringUtils::hasText)
                .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private String toolForLink(OperationalContextReadModelLinkDto link) {
        if (link == null || !StringUtils.hasText(link.href())) {
            return null;
        }
        if (link.href().contains("/search")) {
            return "opctx_search";
        }
        if (link.href().contains("/entities/") && !link.href().contains("/read-model/entities/")
                && !PROFILE_EXPANDED.equals(link.profile())) {
            return "opctx_get_entity";
        }
        if (link.href().contains("/read-model/entities/")
                && List.of("relations", "code-search").contains(link.rel())) {
            return "opctx_get_entity";
        }
        return null;
    }

    private Map<String, Object> argumentsForLink(OperationalContextReadModelLinkDto link, String tool) {
        if (!StringUtils.hasText(tool) || link == null || !StringUtils.hasText(link.href())) {
            return Map.of();
        }
        if ("opctx_search".equals(tool)) {
            return map("query", queryParameter(link.href(), "q"));
        }
        if ("opctx_get_entity".equals(tool)) {
            var type = typeFromHref(link.href());
            var id = queryParameter(link.href(), "id");
            var include = includeForRel(link.rel());
            return include != null
                    ? map("type", type, "id", id, "include", List.of(include))
                    : map("type", type, "id", id);
        }
        return Map.of();
    }

    private String includeForRel(String rel) {
        return switch (rel == null ? "" : rel) {
            case "relations" -> "relations";
            case "code-search" -> "codeSearch";
            default -> null;
        };
    }

    private String typeFromHref(String href) {
        var marker = href.contains("/read-model/entities/")
                ? "/read-model/entities/"
                : "/entities/";
        var markerIndex = href.indexOf(marker);
        if (markerIndex < 0) {
            return null;
        }
        var start = markerIndex + marker.length();
        var slash = href.indexOf('/', start);
        var query = href.indexOf('?', start);
        var end = slash >= 0 ? slash : query;
        if (end < 0) {
            end = href.length();
        }
        return decode(href.substring(start, end));
    }

    private String queryParameter(String href, String name) {
        if (!StringUtils.hasText(href) || !StringUtils.hasText(name)) {
            return null;
        }
        var queryStart = href.indexOf('?');
        if (queryStart < 0 || queryStart + 1 >= href.length()) {
            return null;
        }
        var query = href.substring(queryStart + 1);
        for (var part : query.split("&")) {
            var separator = part.indexOf('=');
            var key = separator >= 0 ? part.substring(0, separator) : part;
            if (Objects.equals(decode(key), name)) {
                return separator >= 0 ? decode(part.substring(separator + 1)) : "";
            }
        }
        return null;
    }

    private List<OperationalContextReadModelLinkDto> summaryLinks() {
        return List.of(
                new OperationalContextReadModelLinkDto("self", "/api/operational-context/summary?profile=default", PROFILE_DEFAULT, "Compact catalog counts and status."),
                new OperationalContextReadModelLinkDto("expanded", "/api/operational-context/summary?profile=expanded", PROFILE_EXPANDED, "Full FE-safe summary payload."),
                new OperationalContextReadModelLinkDto("validation", "/api/operational-context/validation", PROFILE_EXPANDED, "Diagnostic validation findings.")
        );
    }

    private List<OperationalContextReadModelLinkDto> entityLinks(String type, String id) {
        var links = new ArrayList<OperationalContextReadModelLinkDto>();
        links.add(new OperationalContextReadModelLinkDto("self", entityHref(type, id, PROFILE_DEFAULT), PROFILE_DEFAULT, "Compact entity envelope."));
        links.add(new OperationalContextReadModelLinkDto("expanded", entityHref(type, id, PROFILE_EXPANDED), PROFILE_EXPANDED, "Full FE-safe entity detail."));
        if (supportsReadModels(type)) {
            links.add(new OperationalContextReadModelLinkDto("relations", readModelHref(type, id, "relations", PROFILE_DEFAULT), PROFILE_DEFAULT, "Read compact relation graph."));
            links.add(new OperationalContextReadModelLinkDto("code-search", readModelHref(type, id, "code-search", PROFILE_DEFAULT), PROFILE_DEFAULT, "Read compact code-search scope."));
            links.add(new OperationalContextReadModelLinkDto("implementations", readModelHref(type, id, "implementations", PROFILE_DEFAULT), PROFILE_DEFAULT, "Read compact implementation map."));
            links.add(new OperationalContextReadModelLinkDto("flow", readModelHref(type, id, "flow", PROFILE_DEFAULT), PROFILE_DEFAULT, "Read compact flow model."));
            links.add(new OperationalContextReadModelLinkDto("blast-radius", readModelHref(type, id, "blast-radius", PROFILE_DEFAULT), PROFILE_DEFAULT, "Read compact blast-radius model."));
        }
        return List.copyOf(links);
    }

    private List<OperationalContextReadModelLinkDto> readModelLinks(EntityRef target, String current) {
        if (target == null) {
            return List.of();
        }
        return List.of(
                new OperationalContextReadModelLinkDto("self", readModelHref(target.type(), target.id(), current, PROFILE_DEFAULT), PROFILE_DEFAULT, "Compact " + current + " read model."),
                new OperationalContextReadModelLinkDto("expanded", readModelHref(target.type(), target.id(), current, PROFILE_EXPANDED), PROFILE_EXPANDED, "Full " + current + " read model."),
                new OperationalContextReadModelLinkDto("entity", entityHref(target.type(), target.id(), PROFILE_DEFAULT), PROFILE_DEFAULT, "Compact entity detail."),
                new OperationalContextReadModelLinkDto("relations", readModelHref(target.type(), target.id(), "relations", PROFILE_DEFAULT), PROFILE_DEFAULT, "Compact relation graph."),
                new OperationalContextReadModelLinkDto("code-search", readModelHref(target.type(), target.id(), "code-search", PROFILE_DEFAULT), PROFILE_DEFAULT, "Compact repositories and hints."),
                new OperationalContextReadModelLinkDto("implementations", readModelHref(target.type(), target.id(), "implementations", PROFILE_DEFAULT), PROFILE_DEFAULT, "Compact implementation cards."),
                new OperationalContextReadModelLinkDto("flow", readModelHref(target.type(), target.id(), "flow", PROFILE_DEFAULT), PROFILE_DEFAULT, "Compact process flow."),
                new OperationalContextReadModelLinkDto("blast-radius", readModelHref(target.type(), target.id(), "blast-radius", PROFILE_DEFAULT), PROFILE_DEFAULT, "Compact impact graph.")
        );
    }

    private List<OperationalContextReadModelLinkDto> blastRadiusLinks(EntityRef target) {
        if (target == null) {
            return List.of();
        }
        return List.of(
                new OperationalContextReadModelLinkDto("self", blastRadiusHref(target.type(), target.id(), PROFILE_DEFAULT), PROFILE_DEFAULT, "Compact blast-radius graph."),
                new OperationalContextReadModelLinkDto("expanded", blastRadiusHref(target.type(), target.id(), PROFILE_EXPANDED), PROFILE_EXPANDED, "Full blast-radius graph."),
                new OperationalContextReadModelLinkDto("search", "/api/operational-context/search?q=" + encode(target.id()) + "&profile=default", PROFILE_DEFAULT, "Find catalog entities related to this signal.")
        );
    }

    private List<String> entityNextReads(String type, String id) {
        if (!supportsReadModels(type)) {
            return List.of("GET " + entityHref(type, id, PROFILE_EXPANDED));
        }
        return List.of(
                "GET " + readModelHref(type, id, "relations", PROFILE_DEFAULT),
                "GET " + readModelHref(type, id, "code-search", PROFILE_DEFAULT),
                "GET " + readModelHref(type, id, "implementations", PROFILE_DEFAULT),
                "GET " + readModelHref(type, id, "flow", PROFILE_DEFAULT)
        );
    }

    private boolean supportsReadModels(String type) {
        return List.of(
                "system",
                "repository",
                "code-search-scope",
                "process",
                "integration",
                "bounded-context",
                "team",
                "datastore"
        ).contains(type);
    }

    private String entityHref(String type, String id, String profile) {
        return "/api/operational-context/entities/" + encodePath(type) + "?id=" + encode(id) + "&profile=" + profile;
    }

    private String readModelHref(String type, String id, String readModel, String profile) {
        return "/api/operational-context/read-model/entities/" + encodePath(type) + "/" + readModel
                + "?id=" + encode(id) + "&profile=" + profile;
    }

    private String blastRadiusHref(String type, String id, String profile) {
        return "/api/operational-context/read-model/blast-radius?type=" + encode(type)
                + "&id=" + encode(id) + "&profile=" + profile;
    }

    private List<String> relationNextReads(EntityRef analysisTarget, List<ReadModelRelation> topRelations) {
        var reads = new ArrayList<String>();
        for (var relation : topRelations) {
            var peer = relationPeer(relation, analysisTarget);
            if (peer == null) {
                continue;
            }
            var readModel = relationNextReadModel(peer.type());
            if (readModel != null) {
                addDistinct(reads, "GET " + readModelHref(peer.type(), peer.id(), readModel, PROFILE_DEFAULT)
                        + " -- " + relationReasonToRead(relation));
            } else {
                addDistinct(reads, "GET " + entityHref(peer.type(), peer.id(), PROFILE_DEFAULT)
                        + " -- " + relationReasonToRead(relation));
            }
            if (reads.size() >= 4) {
                break;
            }
        }

        addDistinct(reads, "Use code-search default for repositories behind the highest-signal relations.");
        addDistinct(reads, "Use flow default when relations mention a process or integration path.");
        return List.copyOf(reads);
    }

    private String relationNextReadModel(String type) {
        return switch (type == null ? "" : type) {
            case "process" -> "flow";
            case "repository", "code-search-scope", "system", "bounded-context" -> "code-search";
            case "integration" -> "blast-radius";
            default -> null;
        };
    }

    private List<String> blastRadiusNextReads(
            EntityRef analysisTarget,
            List<FlowImpactView> sortedFlowImpacts,
            List<ImplementationImpactView> sortedImplementationImpacts,
            boolean broadSignal
    ) {
        var reads = new ArrayList<String>();
        if (broadSignal && analysisTarget != null) {
            addDistinct(reads, "Refine broad " + analysisTarget.type() + " signal `" + analysisTarget.id()
                    + "` before GitLab reads; prefer endpoint/package/table/queue/topic with narrower scope.");
        }
        for (var flowImpact : limit(sortedFlowImpacts, 2)) {
            if (flowImpact.flow() != null) {
                addDistinct(reads, "GET " + readModelHref(flowImpact.flow().type(), flowImpact.flow().id(), "flow", PROFILE_DEFAULT)
                        + " -- " + flowImpactReasonToRead(flowImpact));
            }
        }
        for (var impact : limit(sortedImplementationImpacts, 2)) {
            var implementation = impact.implementation();
            if (implementation == null) {
                continue;
            }
            if (implementation.codeSearchScope() != null) {
                addDistinct(reads, "GET " + readModelHref(
                        implementation.codeSearchScope().type(),
                        implementation.codeSearchScope().id(),
                        "code-search",
                        PROFILE_DEFAULT
                ) + " -- " + implementationImpactReasonToRead(impact));
            } else if (implementation.repository() != null) {
                addDistinct(reads, "GET " + entityHref(
                        implementation.repository().type(),
                        implementation.repository().id(),
                        PROFILE_DEFAULT
                ) + " -- " + implementationImpactReasonToRead(impact));
            }
        }
        addDistinct(reads, "Use implementation/code-search defaults to narrow GitLab reads to returned repositories and modules.");
        return List.copyOf(reads);
    }

    private void addDistinct(List<String> values, String value) {
        if (StringUtils.hasText(value) && !values.contains(value)) {
            values.add(value);
        }
    }

    private EntityKey relationPeer(ReadModelRelation relation, EntityRef analysisTarget) {
        if (relation == null) {
            return null;
        }
        if (sameKey(relation.source(), analysisTarget)) {
            return relation.target();
        }
        if (sameKey(relation.target(), analysisTarget)) {
            return relation.source();
        }
        return relation.target();
    }

    private boolean sameKey(EntityKey key, EntityRef ref) {
        return key != null
                && ref != null
                && key.type().equals(ref.type())
                && key.id().equals(ref.id());
    }

    private Map<String, Double> relationScoresByNeighbor(EntityRef analysisTarget, List<ReadModelRelation> relations) {
        var scores = new LinkedHashMap<String, Double>();
        for (var relation : relations) {
            var peer = relationPeer(relation, analysisTarget);
            if (peer == null) {
                continue;
            }
            scores.merge(entityKeyValue(peer), relationRelevanceScore(relation), Math::max);
        }
        return scores;
    }

    private int compareEntityRefsByRelationScore(
            EntityRef left,
            EntityRef right,
            Map<String, Double> relationScoresByNeighbor
    ) {
        var scoreComparison = Double.compare(
                relationScoresByNeighbor.getOrDefault(entityRefValue(right), 0.0),
                relationScoresByNeighbor.getOrDefault(entityRefValue(left), 0.0)
        );
        if (scoreComparison != 0) {
            return scoreComparison;
        }
        return Comparator
                .comparing(EntityRef::type)
                .thenComparing(EntityRef::id)
                .compare(left, right);
    }

    private Double topRelationScore(List<ReadModelRelation> relations) {
        return relations.stream()
                .mapToDouble(this::relationRelevanceScore)
                .max()
                .orElse(0.0);
    }

    private Double relationRelevanceScore(ReadModelRelation relation) {
        if (relation == null) {
            return 0.0;
        }
        var score = 0.18;
        score += confidenceRelevance(relation.provenance().confidence());
        score += relation.provenance().canonical() ? 0.16 : 0.04;
        score += relation.derived() ? 0.03 : 0.11;
        score += relationTypeRelevance(relation.relationType());
        score += Math.max(entityTypeRelevance(relation.source()), entityTypeRelevance(relation.target()));
        score += roleRelevance(relation.role());
        score += Math.min(0.04, relation.provenance().sourceRefs().size() * 0.01);
        return roundScore(Math.min(1.0, score));
    }

    private String relationReasonToRead(ReadModelRelation relation) {
        var reasons = new ArrayList<String>();
        reasons.add(relation.provenance().canonical() ? "canonical catalog edge" : "derived read-model edge");
        reasons.add(relation.provenance().confidence() + " confidence");
        reasons.add((relation.derived() ? "derived" : "direct") + " " + relation.relationType());
        reasons.add("connects " + relation.source().type() + " to " + relation.target().type());
        if (StringUtils.hasText(relation.role())) {
            reasons.add("role=" + relation.role());
        }
        if (!relation.provenance().sourceRefs().isEmpty()) {
            reasons.add("has sourceRefs");
        }
        return String.join("; ", reasons);
    }

    private Double flowImpactRelevanceScore(FlowImpactView impact) {
        if (impact == null) {
            return 0.0;
        }
        var directHits = impact.impactedSteps().stream()
                .filter(step -> "direct-hit".equals(step.impactType()))
                .count();
        var score = 0.18
                + confidenceRelevance(impact.confidence())
                + Math.min(0.12, impact.impactedSteps().size() * 0.03)
                + Math.min(0.06, impact.downstreamEdges().size() * 0.01)
                + (directHits > 0 ? 0.22 : 0.06)
                + (impact.provenance().canonical() ? 0.04 : 0.02);
        return roundScore(Math.min(1.0, score));
    }

    private String flowImpactReasonToRead(FlowImpactView impact) {
        var directHits = impact.impactedSteps().stream()
                .filter(step -> "direct-hit".equals(step.impactType()))
                .count();
        var reasons = new ArrayList<String>();
        reasons.add(impact.confidence() + " confidence");
        reasons.add(impact.impactedSteps().size() + " impacted steps");
        if (directHits > 0) {
            reasons.add(directHits + " direct-hit steps");
        }
        if (!impact.downstreamEdges().isEmpty()) {
            reasons.add(impact.downstreamEdges().size() + " downstream edges");
        }
        if (!impact.reasons().isEmpty()) {
            reasons.add(impact.reasons().get(0));
        }
        return String.join("; ", reasons);
    }

    private Double stepImpactRelevanceScore(StepImpactView step) {
        if (step == null) {
            return 0.0;
        }
        var score = 0.18
                + impactTypeRelevance(step.impactType())
                + stepKindRelevance(step.kind())
                + Math.min(0.08, step.implementations().size() * 0.02)
                + Math.min(0.06, (step.systems().size() + step.integrations().size() + step.dataStores().size()) * 0.01);
        return roundScore(Math.min(1.0, score));
    }

    private String stepImpactReasonToRead(StepImpactView step) {
        var reasons = new ArrayList<String>();
        reasons.add(step.impactType() + " " + step.kind());
        if (!step.implementations().isEmpty()) {
            reasons.add(step.implementations().size() + " implementation refs");
        }
        if (!step.systems().isEmpty()) {
            reasons.add(step.systems().size() + " systems");
        }
        if (!step.integrations().isEmpty()) {
            reasons.add(step.integrations().size() + " integrations");
        }
        if (!step.reasons().isEmpty()) {
            reasons.add(step.reasons().get(0));
        }
        return String.join("; ", reasons);
    }

    private Double impactNodeRelevanceScore(ImpactNodeView node) {
        if (node == null) {
            return 0.0;
        }
        var score = 0.16
                + criticalityRelevance(node.criticality())
                + confidenceRelevance(node.confidence())
                + impactTypeRelevance(node.impactType())
                + entityTypeRelevance(node.entity())
                + (node.provenance().canonical() ? 0.04 : 0.02);
        return roundScore(Math.min(1.0, score));
    }

    private String impactNodeReasonToRead(ImpactNodeView node) {
        var reasons = new ArrayList<String>();
        reasons.add(node.criticality() + " criticality");
        reasons.add(node.confidence() + " confidence");
        reasons.add(node.impactType() + " " + node.direction());
        if (node.entity() != null) {
            reasons.add(node.entity().type() + ":" + node.entity().id());
        }
        if (!node.reasons().isEmpty()) {
            reasons.add(node.reasons().get(0));
        }
        return String.join("; ", reasons);
    }

    private Double implementationImpactRelevanceScore(ImplementationImpactView impact) {
        if (impact == null || impact.implementation() == null) {
            return 0.0;
        }
        var implementation = impact.implementation();
        var score = 0.16
                + confidenceRelevance(impact.confidence())
                + impactTypeRelevance(impact.impactType())
                + lifecycleRelevance(implementation.lifecycleRole())
                + migrationStatusRelevance(implementation.migrationStatus())
                + (implementation.codeSearchScope() != null ? 0.04 : 0.0)
                + (implementation.repository() != null ? 0.04 : 0.0);
        return roundScore(Math.min(1.0, score));
    }

    private String implementationImpactReasonToRead(ImplementationImpactView impact) {
        var implementation = impact.implementation();
        if (implementation == null) {
            return impact.confidence() + " confidence " + impact.impactType();
        }
        var reasons = new ArrayList<String>();
        reasons.add(impact.confidence() + " confidence");
        reasons.add(impact.impactType());
        reasons.add("lifecycle=" + implementation.lifecycleRole());
        reasons.add("migration=" + implementation.migrationStatus());
        if (implementation.repository() != null) {
            reasons.add("repo=" + implementation.repository().id());
        }
        if (implementation.module() != null) {
            reasons.add("module=" + implementation.module().id());
        }
        return String.join("; ", reasons);
    }

    private Double blastRadiusRelevanceScore(OperationalContextBlastRadiusReadModel expanded, boolean broadSignal) {
        var best = 0.0;
        for (var impact : expanded.impactedFlows()) {
            best = Math.max(best, flowImpactRelevanceScore(impact));
        }
        for (var node : combine(
                expanded.impactedSystems(),
                expanded.impactedBoundedContexts(),
                expanded.impactedIntegrations(),
                expanded.impactedDataStores()
        )) {
            best = Math.max(best, impactNodeRelevanceScore(node));
        }
        for (var implementation : expanded.impactedImplementations()) {
            best = Math.max(best, implementationImpactRelevanceScore(implementation));
        }
        if (broadSignal) {
            return roundScore(Math.min(0.55, best));
        }
        return roundScore(best);
    }

    private double confidenceRelevance(String confidence) {
        return switch (confidence == null ? "" : confidence) {
            case "high" -> 0.24;
            case "medium" -> 0.16;
            case "low" -> 0.07;
            default -> 0.1;
        };
    }

    private double relationTypeRelevance(String relationType) {
        var normalized = relationType == null ? "" : relationType.toLowerCase(Locale.ROOT);
        if (normalized.contains("primary") || normalized.contains("target") || normalized.contains("source")) {
            return 0.13;
        }
        if (normalized.contains("repository") || normalized.contains("code-search") || normalized.contains("implementation")) {
            return 0.12;
        }
        if (normalized.contains("integration") || normalized.contains("flow") || normalized.contains("process")) {
            return 0.11;
        }
        if (normalized.contains("responsible") || normalized.contains("owner")) {
            return 0.09;
        }
        if (normalized.contains("reference") || normalized.contains("uses")) {
            return 0.07;
        }
        return 0.04;
    }

    private double roleRelevance(String role) {
        return switch (role == null ? "" : role) {
            case "primary", "target", "source", "owner" -> 0.06;
            case "support", "shared", "parallel" -> 0.04;
            case "legacy" -> 0.02;
            default -> 0.0;
        };
    }

    private double entityTypeRelevance(EntityKey key) {
        return key != null ? entityTypeRelevance(key.type()) : 0.0;
    }

    private double entityTypeRelevance(EntityRef ref) {
        return ref != null ? entityTypeRelevance(ref.type()) : 0.0;
    }

    private double entityTypeRelevance(String type) {
        return switch (type == null ? "" : type) {
            case "process" -> 0.12;
            case "system" -> 0.11;
            case "integration", "repository", "code-search-scope" -> 0.1;
            case "bounded-context", "datastore" -> 0.08;
            case "team" -> 0.04;
            default -> 0.03;
        };
    }

    private double impactTypeRelevance(String impactType) {
        var normalized = impactType == null ? "" : impactType.toLowerCase(Locale.ROOT);
        if (normalized.contains("direct")) {
            return 0.24;
        }
        if (normalized.contains("upstream") || normalized.contains("downstream")) {
            return 0.14;
        }
        if (normalized.contains("code")) {
            return 0.12;
        }
        return 0.08;
    }

    private double criticalityRelevance(String criticality) {
        return switch (criticality == null ? "" : criticality) {
            case "critical", "high" -> 0.2;
            case "medium" -> 0.13;
            case "low" -> 0.06;
            default -> 0.08;
        };
    }

    private double stepKindRelevance(String kind) {
        var normalized = kind == null ? "" : kind.toLowerCase(Locale.ROOT);
        if (normalized.contains("endpoint") || normalized.contains("trigger")) {
            return 0.08;
        }
        if (normalized.contains("integration") || normalized.contains("database") || normalized.contains("queue")
                || normalized.contains("topic")) {
            return 0.07;
        }
        return 0.04;
    }

    private double lifecycleRelevance(String role) {
        return switch (role == null ? "" : role) {
            case "primary", "target" -> 0.12;
            case "parallel" -> 0.09;
            case "support", "shared" -> 0.06;
            case "legacy" -> 0.03;
            default -> 0.04;
        };
    }

    private double migrationStatusRelevance(String status) {
        return switch (status == null ? "" : status) {
            case "active", "target" -> 0.08;
            case "transitional", "parallel" -> 0.06;
            case "legacy" -> 0.03;
            default -> 0.04;
        };
    }

    private Double roundScore(double score) {
        return Math.round(score * 100.0) / 100.0;
    }

    private String entityKeyValue(EntityKey key) {
        return key != null ? key.type() + ":" + key.id() : "";
    }

    private String entityRefValue(EntityRef ref) {
        return ref != null ? ref.type() + ":" + ref.id() : "";
    }

    private int compareRelations(ReadModelRelation left, ReadModelRelation right) {
        return Comparator
                .comparingDouble((ReadModelRelation relation) -> -relationRelevanceScore(relation))
                .thenComparingInt(relation -> relation.provenance().canonical() ? 0 : 1)
                .thenComparingInt(relation -> relation.derived() ? 1 : 0)
                .thenComparingInt(relation -> confidenceRank(relation.provenance().confidence()))
                .thenComparing(ReadModelRelation::relationType)
                .compare(left, right);
    }

    private int compareRepositories(RepositoryView left, RepositoryView right) {
        return Comparator
                .comparingInt((RepositoryView repository) -> repository.priority() != null ? repository.priority() : Integer.MAX_VALUE)
                .thenComparingInt(repository -> roleRank(repository.role()))
                .thenComparing(repository -> repository.repository().id())
                .compare(left, right);
    }

    private int compareImplementations(ImplementationView left, ImplementationView right) {
        return Comparator
                .comparingInt((ImplementationView implementation) -> lifecycleRank(implementation.lifecycleRole()))
                .thenComparingInt(implementation -> statusRank(implementation.migrationStatus()))
                .thenComparingInt(implementation -> implementation.priority() != null ? implementation.priority() : Integer.MAX_VALUE)
                .thenComparing(ImplementationView::id)
                .compare(left, right);
    }

    private int compareFlowImpacts(FlowImpactView left, FlowImpactView right) {
        return Comparator
                .comparingDouble((FlowImpactView impact) -> -flowImpactRelevanceScore(impact))
                .thenComparingInt(impact -> confidenceRank(impact.confidence()))
                .thenComparing(impact -> impact.flow().id())
                .compare(left, right);
    }

    private int compareImpactNodes(ImpactNodeView left, ImpactNodeView right) {
        return Comparator
                .comparingDouble((ImpactNodeView node) -> -impactNodeRelevanceScore(node))
                .thenComparingInt(node -> criticalityRank(node.criticality()))
                .thenComparingInt(node -> confidenceRank(node.confidence()))
                .thenComparing(node -> node.entity().id())
                .compare(left, right);
    }

    private int compareImplementationImpacts(ImplementationImpactView left, ImplementationImpactView right) {
        return Comparator
                .comparingDouble((ImplementationImpactView impact) -> -implementationImpactRelevanceScore(impact))
                .thenComparingInt(impact -> confidenceRank(impact.confidence()))
                .thenComparing(impact -> impact.implementation().id())
                .compare(left, right);
    }

    private int confidenceRank(String confidence) {
        return switch (confidence == null ? "" : confidence) {
            case "high" -> 0;
            case "medium" -> 1;
            case "low" -> 2;
            default -> 3;
        };
    }

    private int roleRank(String role) {
        return switch (role == null ? "" : role) {
            case "primary", "target" -> 0;
            case "parallel" -> 1;
            case "support", "shared" -> 2;
            case "legacy" -> 3;
            default -> 4;
        };
    }

    private int lifecycleRank(String role) {
        return switch (role == null ? "" : role) {
            case "primary", "target" -> 0;
            case "parallel" -> 1;
            case "support" -> 2;
            case "legacy" -> 3;
            default -> 4;
        };
    }

    private int statusRank(String status) {
        return switch (status == null ? "" : status) {
            case "active", "target" -> 0;
            case "transitional", "parallel" -> 1;
            case "legacy" -> 2;
            default -> 3;
        };
    }

    private int criticalityRank(String criticality) {
        return switch (criticality == null ? "" : criticality) {
            case "high", "critical" -> 0;
            case "medium" -> 1;
            case "low" -> 2;
            default -> 3;
        };
    }

    private boolean broadSignal(OperationalContextBlastRadiusReadModel expanded) {
        var type = expanded.analysisTarget() != null ? expanded.analysisTarget().type() : "";
        var broadType = List.of("class", "table", "queue", "topic").contains(type);
        return broadType && (expanded.impactedImplementations().size() > 20
                || expanded.impactedFlows().size() > 1
                || totalNodes(expanded) > 20);
    }

    private int totalNodes(OperationalContextBlastRadiusReadModel expanded) {
        return expanded.impactedSystems().size()
                + expanded.impactedBoundedContexts().size()
                + expanded.impactedIntegrations().size()
                + expanded.impactedDataStores().size();
    }

    private Map<String, Integer> lifecycleRoleCounts(List<ImplementationView> implementations) {
        var counts = new LinkedHashMap<String, Integer>();
        for (var implementation : implementations) {
            counts.merge(implementation.lifecycleRole(), 1, Integer::sum);
        }
        return counts;
    }

    @SafeVarargs
    private final <T> List<T> combine(List<T>... lists) {
        var result = new ArrayList<T>();
        for (var list : lists) {
            result.addAll(list);
        }
        return result;
    }

    private <T> List<T> limit(List<T> values, int limit) {
        if (values == null || values.isEmpty() || limit <= 0) {
            return List.of();
        }
        return values.stream().limit(limit).toList();
    }

    private List<String> limitText(List<String> values, int limit) {
        if (values == null || values.isEmpty() || limit <= 0) {
            return List.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .limit(limit)
                .toList();
    }

    private boolean hasTruncation(int total, int returned) {
        return total > returned;
    }

    private int countAggregateSourceRefs(List<ExplainableAggregateDto> aggregates) {
        var count = 0;
        for (var aggregate : aggregates) {
            count += aggregate.sourceRefs().size();
            for (var group : aggregate.groups()) {
                for (var item : group.items()) {
                    count += item.sourceRefs().size();
                }
            }
        }
        return count;
    }

    private int countRelationSourceRefs(List<ReadModelRelation> relations) {
        return countProvenanceSourceRefs(relations.stream().map(ReadModelRelation::provenance).toList());
    }

    private int countProvenanceSourceRefs(List<Provenance> provenances) {
        return provenances.stream()
                .filter(Objects::nonNull)
                .mapToInt(provenance -> provenance.sourceRefs().size())
                .sum();
    }

    private Map<String, Object> map(Object... values) {
        var map = new LinkedHashMap<String, Object>();
        for (var index = 0; index + 1 < values.length; index += 2) {
            var key = values[index];
            var value = values[index + 1];
            if (key == null || !meaningful(value)) {
                continue;
            }
            map.put(String.valueOf(key), value);
        }
        return map;
    }

    private Map<String, Integer> counts(Object... values) {
        var map = new LinkedHashMap<String, Integer>();
        for (var index = 0; index + 1 < values.length; index += 2) {
            var key = values[index];
            var value = values[index + 1];
            if (key == null || value == null) {
                continue;
            }
            map.put(String.valueOf(key), ((Number) value).intValue());
        }
        return map;
    }

    private boolean meaningful(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return StringUtils.hasText(text);
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }

    private String sourceFile(String type) {
        return switch (type) {
            case "system" -> "systems.yml";
            case "repository" -> "repo-map.yml";
            case "code-search-scope" -> "code-search-scopes.yml";
            case "process" -> "processes.yml";
            case "integration" -> "integrations.yml";
            case "bounded-context" -> "bounded-contexts.yml";
            case "team" -> "teams.yml";
            case "glossary-term" -> "glossary.md";
            case "handoff-rule" -> "handoff-rules.md";
            default -> "operational-context";
        };
    }

    private String idPath(String type, String id) {
        return switch (type) {
            case "system" -> "systems[id=" + id + "]";
            case "repository" -> "repositories[id=" + id + "]";
            case "code-search-scope" -> "codeSearchScopes[id=" + id + "]";
            case "process" -> "processes[id=" + id + "]";
            case "integration" -> "integrations[id=" + id + "]";
            case "bounded-context" -> "boundedContexts[id=" + id + "]";
            case "team" -> "teams[id=" + id + "]";
            default -> id;
        };
    }

    private String encode(String value) {
        return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8);
    }

    private String encodePath(String value) {
        return encode(value).replace("+", "%20");
    }

    private String decode(String value) {
        return URLDecoder.decode(value != null ? value : "", StandardCharsets.UTF_8);
    }

    private record ProfileBudget(
            int relationsPerDirection,
            int neighbors,
            int scopes,
            int repositories,
            int hints,
            int implementations,
            int steps,
            int impactFlows,
            int impactNodes,
            int validationFindings,
            int sourceRefs,
            int groups,
            int groupItems,
            int searchResults,
            int edges,
            int summaryCards
    ) {
    }
}
