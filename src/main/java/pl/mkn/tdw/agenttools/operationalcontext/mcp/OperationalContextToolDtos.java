package pl.mkn.tdw.agenttools.operationalcontext.mcp;

import java.util.List;
import java.util.Map;

public final class OperationalContextToolDtos {

    private OperationalContextToolDtos() {
    }

    public record OpctxScopeResult(
            boolean enabled,
            List<OpctxEntityTypeSummary> entityTypes,
            OpctxToolAffordances affordances
    ) {
        public OpctxScopeResult {
            entityTypes = entityTypes != null ? List.copyOf(entityTypes) : List.of();
            affordances = affordances != null ? affordances : OpctxToolAffordances.defaultProfile();
        }
    }

    public record OpctxEntityTypeSummary(
            String type,
            String label,
            int count,
            boolean listable,
            boolean searchable,
            boolean detailAvailable
    ) {
    }

    public record OpctxListEntitiesResult(
            String type,
            int page,
            int pageSize,
            int totalItems,
            int totalPages,
            boolean truncated,
            List<OpctxEntityIndexItem> items,
            OpctxToolAffordances affordances
    ) {
        public OpctxListEntitiesResult {
            items = items != null ? List.copyOf(items) : List.of();
            affordances = affordances != null ? affordances : OpctxToolAffordances.defaultProfile();
        }
    }

    public record OpctxEntityIndexItem(
            String type,
            String id,
            String label,
            String summary,
            Map<String, List<String>> facets,
            List<String> sourceRefs
    ) {
        public OpctxEntityIndexItem {
            facets = facets != null ? Map.copyOf(facets) : Map.of();
            sourceRefs = sourceRefs != null ? List.copyOf(sourceRefs) : List.of();
        }
    }

    public record OpctxSearchResult(
            String query,
            List<String> types,
            int limit,
            boolean truncated,
            List<OpctxSearchItem> results,
            OpctxToolAffordances affordances
    ) {
        public OpctxSearchResult {
            types = types != null ? List.copyOf(types) : List.of();
            results = results != null ? List.copyOf(results) : List.of();
            affordances = affordances != null ? affordances : OpctxToolAffordances.defaultProfile();
        }
    }

    public record OpctxSearchItem(
            String type,
            String id,
            String label,
            String summary,
            double confidence,
            List<String> matchedFields,
            List<String> matchedSignals,
            String why,
            List<String> sourceRefs
    ) {
        public OpctxSearchItem {
            matchedFields = matchedFields != null ? List.copyOf(matchedFields) : List.of();
            matchedSignals = matchedSignals != null ? List.copyOf(matchedSignals) : List.of();
            sourceRefs = sourceRefs != null ? List.copyOf(sourceRefs) : List.of();
        }
    }

    public record OpctxEntityDetailResult(
            String type,
            String id,
            String label,
            String summary,
            String purpose,
            Map<String, Object> overview,
            Map<String, Object> relations,
            Map<String, Object> signals,
            Map<String, Object> codeSearch,
            Map<String, Object> handoff,
            Map<String, Object> sourceCoverage,
            List<OpctxOpenQuestion> openQuestions,
            List<String> sourceRefs,
            OpctxToolAffordances affordances
    ) {
        public OpctxEntityDetailResult {
            overview = overview != null ? Map.copyOf(overview) : Map.of();
            relations = relations != null ? Map.copyOf(relations) : Map.of();
            signals = signals != null ? Map.copyOf(signals) : Map.of();
            codeSearch = codeSearch != null ? Map.copyOf(codeSearch) : Map.of();
            handoff = handoff != null ? Map.copyOf(handoff) : Map.of();
            sourceCoverage = sourceCoverage != null ? Map.copyOf(sourceCoverage) : Map.of();
            openQuestions = openQuestions != null ? List.copyOf(openQuestions) : List.of();
            sourceRefs = sourceRefs != null ? List.copyOf(sourceRefs) : List.of();
            affordances = affordances != null ? affordances : OpctxToolAffordances.defaultProfile();
        }
    }

    public record OpctxToolAffordances(
            String profile,
            List<OpctxToolLink> links,
            List<String> availableExpansions,
            List<String> suggestedNextReads,
            List<String> suggestedTools,
            String reasonToExpand,
            List<String> omittedBecause,
            OpctxTruncation truncation,
            List<String> limitations
    ) {
        public OpctxToolAffordances {
            profile = profile != null ? profile : "default";
            links = links != null ? List.copyOf(links) : List.of();
            availableExpansions = availableExpansions != null ? List.copyOf(availableExpansions) : List.of();
            suggestedNextReads = suggestedNextReads != null ? List.copyOf(suggestedNextReads) : List.of();
            suggestedTools = suggestedTools != null ? List.copyOf(suggestedTools) : List.of();
            omittedBecause = omittedBecause != null ? List.copyOf(omittedBecause) : List.of();
            truncation = truncation != null ? truncation : OpctxTruncation.none();
            limitations = limitations != null ? List.copyOf(limitations) : List.of();
        }

        public static OpctxToolAffordances defaultProfile() {
            return new OpctxToolAffordances(
                    "default",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    null,
                    List.of(),
                    OpctxTruncation.none(),
                    List.of()
            );
        }
    }

    public record OpctxToolLink(
            String rel,
            String tool,
            Map<String, Object> arguments,
            String reason
    ) {
        public OpctxToolLink {
            arguments = arguments != null ? Map.copyOf(arguments) : Map.of();
        }
    }

    public record OpctxTruncation(
            boolean truncated,
            String reason,
            Map<String, Integer> returnedCounts,
            Map<String, Integer> omittedCounts
    ) {
        public OpctxTruncation {
            returnedCounts = returnedCounts != null ? Map.copyOf(returnedCounts) : Map.of();
            omittedCounts = omittedCounts != null ? Map.copyOf(omittedCounts) : Map.of();
        }

        public static OpctxTruncation none() {
            return new OpctxTruncation(false, null, Map.of(), Map.of());
        }
    }

    public record OpctxOpenQuestion(
            String id,
            String sourceFile,
            String question,
            String severity,
            String status
    ) {
    }
}
