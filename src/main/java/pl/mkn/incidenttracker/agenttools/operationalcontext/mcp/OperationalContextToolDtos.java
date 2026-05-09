package pl.mkn.incidenttracker.agenttools.operationalcontext.mcp;

import java.util.List;
import java.util.Map;

public final class OperationalContextToolDtos {

    private OperationalContextToolDtos() {
    }

    public record OpctxScopeResult(
            boolean enabled,
            List<OpctxEntityTypeSummary> entityTypes
    ) {
        public OpctxScopeResult {
            entityTypes = entityTypes != null ? List.copyOf(entityTypes) : List.of();
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
            List<OpctxEntityIndexItem> items
    ) {
        public OpctxListEntitiesResult {
            items = items != null ? List.copyOf(items) : List.of();
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
            List<OpctxSearchItem> results
    ) {
        public OpctxSearchResult {
            types = types != null ? List.copyOf(types) : List.of();
            results = results != null ? List.copyOf(results) : List.of();
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
            List<String> sourceRefs
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
