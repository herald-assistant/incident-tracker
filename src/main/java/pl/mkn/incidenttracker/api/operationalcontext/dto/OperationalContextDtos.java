package pl.mkn.incidenttracker.api.operationalcontext.dto;

import java.util.List;
import java.util.Map;

public final class OperationalContextDtos {

    private OperationalContextDtos() {
    }

    public record SourceReferenceDto(
            String file,
            String path,
            String entityId
    ) {
    }

    public record ExplanationReasonDto(
            String label,
            String detail,
            String strength
    ) {
    }

    public record ExplainableValueDto<T>(
            T value,
            String label,
            String confidence,
            List<ExplanationReasonDto> reasons,
            List<String> warnings,
            List<SourceReferenceDto> sourceRefs
    ) {
    }

    public record ExplainableAggregateDto(
            String label,
            int count,
            String severity,
            String confidence,
            String tooltip,
            List<ExplainableBreakdownGroupDto> groups,
            List<ExplanationReasonDto> reasons,
            List<String> warnings,
            List<SourceReferenceDto> sourceRefs,
            String detailsType,
            List<String> detailsIds
    ) {
    }

    public record ExplainableBreakdownGroupDto(
            String label,
            int count,
            List<ExplainableBreakdownItemDto> items
    ) {
    }

    public record ExplainableBreakdownItemDto(
            String id,
            String label,
            String type,
            String reason,
            String status,
            List<SourceReferenceDto> sourceRefs
    ) {
    }

    public record ValidationFindingDto(
            String id,
            String severity,
            String category,
            String entityType,
            String entityId,
            String title,
            String detail,
            List<SourceReferenceDto> sourceRefs,
            String suggestedFix,
            String impact
    ) {
    }

    public record OpenQuestionDto(
            String id,
            String sourceFile,
            String entityType,
            String entityId,
            String question,
            String severity,
            String status
    ) {
    }

    public record OperationalContextSummaryDto(
            int systems,
            int repositories,
            int processes,
            int integrations,
            int boundedContexts,
            int teams,
            int glossaryTerms,
            int handoffRules,
            int openQuestions,
            Map<String, Integer> validationFindings,
            String catalogStatus,
            List<ExplainableAggregateDto> healthCards
    ) {
    }

    public record OperationalContextSystemRowDto(
            String id,
            String name,
            String type,
            ExplainableValueDto<String> owner,
            String purpose,
            ExplainableAggregateDto repos,
            ExplainableAggregateDto processes,
            ExplainableAggregateDto contexts,
            ExplainableAggregateDto integrations,
            ExplainableAggregateDto signals,
            ExplainableAggregateDto handoffReadiness,
            ExplainableAggregateDto validation,
            ExplainableAggregateDto openQuestions
    ) {
    }

    public record OperationalContextRepositoryRowDto(
            String id,
            String project,
            String group,
            ExplainableValueDto<String> owner,
            ExplainableAggregateDto systems,
            ExplainableAggregateDto processes,
            ExplainableAggregateDto contexts,
            ExplainableAggregateDto packageRoots,
            ExplainableAggregateDto entrypoints,
            ExplainableAggregateDto runtimeMappings,
            ExplainableAggregateDto modules,
            ExplainableAggregateDto handoffReadiness,
            ExplainableAggregateDto validation
    ) {
    }

    public record OperationalContextProcessRowDto(
            String id,
            String name,
            ExplainableValueDto<String> owner,
            String purpose,
            ExplainableAggregateDto systems,
            ExplainableAggregateDto externalSystems,
            ExplainableAggregateDto repos,
            ExplainableAggregateDto contexts,
            ExplainableAggregateDto steps,
            ExplainableAggregateDto completionSignals,
            ExplainableAggregateDto handoffHints,
            ExplainableAggregateDto validation
    ) {
    }

    public record OperationalContextIntegrationRowDto(
            String id,
            String name,
            String from,
            String to,
            ExplainableValueDto<String> owner,
            ExplainableAggregateDto partnerTeams,
            String protocol,
            String type,
            ExplainableAggregateDto processes,
            ExplainableAggregateDto contexts,
            ExplainableAggregateDto signals,
            ExplainableAggregateDto handoffReadiness,
            ExplainableAggregateDto validation
    ) {
    }

    public record OperationalContextBoundedContextRowDto(
            String id,
            String name,
            ExplainableValueDto<String> owner,
            String purpose,
            ExplainableAggregateDto systems,
            ExplainableAggregateDto repos,
            ExplainableAggregateDto processes,
            ExplainableAggregateDto terms,
            ExplainableAggregateDto relations,
            ExplainableAggregateDto runtimeFingerprints,
            ExplainableAggregateDto validation
    ) {
    }

    public record OperationalContextTeamRowDto(
            String id,
            String name,
            String purpose,
            ExplainableAggregateDto ownsSystems,
            ExplainableAggregateDto ownsRepos,
            ExplainableAggregateDto ownsProcesses,
            ExplainableAggregateDto ownsContexts,
            ExplainableAggregateDto ownsIntegrations,
            ExplainableAggregateDto signals,
            ExplainableAggregateDto handoffReadiness,
            ExplainableAggregateDto validation
    ) {
    }

    public record OperationalContextGlossaryRowDto(
            String id,
            String term,
            String category,
            String definition,
            ExplainableAggregateDto typicalEvidenceSignals,
            ExplainableAggregateDto canonicalReferences
    ) {
    }

    public record OperationalContextHandoffRuleRowDto(
            String id,
            String title,
            String routeTo,
            ExplainableAggregateDto useWhen,
            ExplainableAggregateDto requiredEvidence,
            String expectedFirstAction,
            ExplainableAggregateDto partnerTeams
    ) {
    }

    public record OperationalContextSearchResultDto(
            String type,
            String id,
            String label,
            String subtitle,
            String confidence,
            List<String> matchedFields,
            String why,
            Map<String, String> actions
    ) {
    }

    public record OperationalContextEntityDetailDto(
            String type,
            String id,
            String title,
            String subtitle,
            List<OperationalContextDetailSectionDto> overviewSections,
            List<ExplainableBreakdownGroupDto> relatedEntities,
            List<ExplainableBreakdownGroupDto> recognitionSignals,
            List<OperationalContextExplainabilitySectionDto> explainabilitySections,
            List<ValidationFindingDto> validationFindings,
            List<OpenQuestionDto> openQuestions,
            List<SourceReferenceDto> sourceReferences,
            String rawSourcePreview
    ) {
    }

    public record OperationalContextDetailSectionDto(
            String title,
            Map<String, Object> fields
    ) {
    }

    public record OperationalContextExplainabilitySectionDto(
            String title,
            String summary,
            String confidence,
            List<ExplanationReasonDto> reasons,
            List<String> warnings,
            List<SourceReferenceDto> sourceRefs
    ) {
    }
}
