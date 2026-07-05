package pl.mkn.tdw.api.operationalcontext.dto;

import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.EntityRef;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.ReadModelRelation;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.ValidationFinding;

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
            String kind,
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

    public record OperationalContextResolvedOwnerDto(
            String targetType,
            String targetId,
            String targetLabel,
            List<String> ownerTeamIds,
            String ownerLabel,
            String source,
            String confidence
    ) {
        public OperationalContextResolvedOwnerDto {
            ownerTeamIds = ownerTeamIds != null ? List.copyOf(ownerTeamIds) : List.of();
        }
    }

    public record OperationalContextResolvedOwnershipDto(
            String situationType,
            List<OperationalContextResolvedOwnerDto> primaryOwners,
            List<OperationalContextResolvedOwnerDto> partnerOwners,
            List<String> resolutionPath,
            String handoffReason,
            List<String> visibilityLimits
    ) {
        public OperationalContextResolvedOwnershipDto {
            primaryOwners = primaryOwners != null ? List.copyOf(primaryOwners) : List.of();
            partnerOwners = partnerOwners != null ? List.copyOf(partnerOwners) : List.of();
            resolutionPath = resolutionPath != null ? List.copyOf(resolutionPath) : List.of();
            visibilityLimits = visibilityLimits != null ? List.copyOf(visibilityLimits) : List.of();
        }
    }

    public record OperationalContextSummaryDto(
            int systems,
            int repositories,
            int codeSearchScopes,
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
            String kind,
            ExplainableValueDto<String> owner,
            OperationalContextResolvedOwnershipDto resolvedOwnership,
            String purpose,
            ExplainableAggregateDto relations,
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
            OperationalContextResolvedOwnershipDto resolvedOwnership,
            ExplainableAggregateDto systems,
            ExplainableAggregateDto contexts,
            ExplainableAggregateDto processes,
            ExplainableAggregateDto integrations,
            ExplainableAggregateDto codeSearchScopes,
            ExplainableAggregateDto codeSearchRoles,
            ExplainableAggregateDto handoffReadiness,
            ExplainableAggregateDto validation
    ) {
    }

    public record OperationalContextCodeSearchScopeRowDto(
            String id,
            String name,
            String scopeType,
            String lifecycleStatus,
            ExplainableAggregateDto target,
            ExplainableAggregateDto repositories,
            ExplainableAggregateDto limitations,
            ExplainableAggregateDto validation
    ) {
    }

    public record OperationalContextProcessRowDto(
            String id,
            String name,
            ExplainableValueDto<String> owner,
            OperationalContextResolvedOwnershipDto resolvedOwnership,
            String purpose,
            ExplainableAggregateDto systems,
            ExplainableAggregateDto externalSystems,
            ExplainableAggregateDto repositories,
            ExplainableAggregateDto contexts,
            ExplainableAggregateDto steps,
            ExplainableAggregateDto completionSignals,
            ExplainableAggregateDto validation
    ) {
    }

    public record OperationalContextIntegrationRowDto(
            String id,
            String name,
            String sourceSystem,
            String targetSystems,
            ExplainableValueDto<String> owner,
            OperationalContextResolvedOwnershipDto resolvedOwnership,
            ExplainableAggregateDto partnerOwners,
            String category,
            String integrationStyle,
            String flowDirection,
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
            OperationalContextResolvedOwnershipDto resolvedOwnership,
            String purpose,
            ExplainableAggregateDto systems,
            ExplainableAggregateDto terms,
            ExplainableAggregateDto relations,
            ExplainableAggregateDto validation
    ) {
    }

    public record OperationalContextTeamRowDto(
            String id,
            String name,
            String purpose,
            ExplainableAggregateDto ownsSystems,
            ExplainableAggregateDto ownsRepositories,
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
            ExplainableAggregateDto matchSignals,
            ExplainableAggregateDto canonicalReferences
    ) {
    }

    public record OperationalContextHandoffRuleRowDto(
            String id,
            String title,
            ExplainableAggregateDto useWhen,
            ExplainableAggregateDto requiredEvidence,
            String expectedFirstAction
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

    public record OperationalContextEntityRelationsReadModelDto(
            String contract,
            int contractVersion,
            EntityRef analysisTarget,
            List<ReadModelRelation> outgoingRelations,
            List<ReadModelRelation> incomingRelations,
            List<EntityRef> neighbors,
            List<ValidationFinding> validationFindings
    ) {
    }

    public record OperationalContextProfiledReadModelDto(
            String contract,
            int contractVersion,
            String profile,
            Object analysisTarget,
            Map<String, Object> data,
            List<OperationalContextReadModelLinkDto> links,
            List<String> availableExpansions,
            List<String> suggestedNextReads,
            List<OperationalContextReadModelNextReadDto> nextReads,
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
        public OperationalContextProfiledReadModelDto {
            data = data != null ? Map.copyOf(data) : Map.of();
            links = links != null ? List.copyOf(links) : List.of();
            availableExpansions = availableExpansions != null ? List.copyOf(availableExpansions) : List.of();
            suggestedNextReads = suggestedNextReads != null ? List.copyOf(suggestedNextReads) : List.of();
            nextReads = nextReads != null ? List.copyOf(nextReads) : List.of();
            suggestedTools = suggestedTools != null ? List.copyOf(suggestedTools) : List.of();
            omittedBecause = omittedBecause != null ? List.copyOf(omittedBecause) : List.of();
            limitations = limitations != null ? List.copyOf(limitations) : List.of();
            sourceRefs = sourceRefs != null ? List.copyOf(sourceRefs) : List.of();
            validationFindings = validationFindings != null ? List.copyOf(validationFindings) : List.of();
        }
    }

    public record OperationalContextReadModelLinkDto(
            String rel,
            String href,
            String profile,
            String reason
    ) {
    }

    public record OperationalContextReadModelNextReadDto(
            String label,
            String rel,
            String href,
            String profile,
            String tool,
            Map<String, Object> arguments,
            String reason
    ) {
        public OperationalContextReadModelNextReadDto {
            arguments = arguments != null ? Map.copyOf(arguments) : Map.of();
        }
    }

    public record OperationalContextReadModelTruncationDto(
            boolean truncated,
            String reason,
            Map<String, Integer> returnedCounts,
            Map<String, Integer> omittedCounts
    ) {
        public OperationalContextReadModelTruncationDto {
            returnedCounts = returnedCounts != null ? Map.copyOf(returnedCounts) : Map.of();
            omittedCounts = omittedCounts != null ? Map.copyOf(omittedCounts) : Map.of();
        }
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
