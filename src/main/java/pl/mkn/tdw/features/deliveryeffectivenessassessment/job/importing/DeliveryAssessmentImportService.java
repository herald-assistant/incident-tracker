package pl.mkn.tdw.features.deliveryeffectivenessassessment.job.importing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.api.DeliveryAssessmentUnitResponse;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.api.DeliveryEffectivenessAssessmentJobStateSnapshot;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.error.DeliveryAssessmentImportException;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.error.DeliveryAssessmentImportPersistenceException;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.export.DeliveryEffectivenessAssessmentExportEnvelope;
import pl.mkn.tdw.features.deliveryeffectivenessassessment.job.localworkspace.DeliveryAssessmentLocalRunPersistence;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryAssessmentImportService {

    private static final int MAX_IMPORT_CHARACTERS = 10_000_000;
    private static final List<String> TERMINAL_STATUSES = List.of(
            "COMPLETED",
            "COMPLETED_WITH_WARNINGS",
            "FAILED"
    );

    private final ObjectMapper objectMapper;
    private final LocalWorkspaceProperties localWorkspaceProperties;
    private final DeliveryAssessmentLocalRunPersistence localRunPersistence;

    public DeliveryEffectivenessAssessmentJobStateSnapshot importReadOnly(JsonNode document) {
        validateDocument(document);
        var envelope = parse(document);
        validateEnvelope(envelope);
        var imported = asImported(envelope.payload().job(), Instant.now());
        if (!localWorkspaceProperties.isEnabled()) {
            throw new DeliveryAssessmentImportPersistenceException();
        }
        try {
            localRunPersistence.persistRunSnapshot(imported);
        } catch (RuntimeException exception) {
            throw new DeliveryAssessmentImportPersistenceException();
        }
        return imported;
    }

    private DeliveryEffectivenessAssessmentExportEnvelope parse(JsonNode document) {
        try {
            return objectMapper.treeToValue(document, DeliveryEffectivenessAssessmentExportEnvelope.class);
        } catch (Exception exception) {
            throw invalid("Delivery Effectiveness Assessment export has an invalid structure.");
        }
    }

    private void validateDocument(JsonNode document) {
        if (document == null || document.isNull()) {
            throw invalid("Delivery Effectiveness Assessment export is required.");
        }
        if (!document.isObject() || document.toString().length() > MAX_IMPORT_CHARACTERS) {
            throw invalid("Delivery Effectiveness Assessment export has an invalid or oversized structure.");
        }
    }

    private void validateEnvelope(DeliveryEffectivenessAssessmentExportEnvelope envelope) {
        if (envelope == null || !DeliveryEffectivenessAssessmentExportEnvelope.SCHEMA.equals(envelope.schema())) {
            throw invalid("Unsupported Delivery Effectiveness Assessment export schema.");
        }
        if (envelope.version() != DeliveryEffectivenessAssessmentExportEnvelope.VERSION) {
            throw invalid("Unsupported Delivery Effectiveness Assessment export version.");
        }
        if (envelope.exportedAt() == null
                || envelope.payload() == null
                || !DeliveryEffectivenessAssessmentExportEnvelope.PAYLOAD_TYPE.equals(envelope.payload().type())
                || !DeliveryEffectivenessAssessmentExportEnvelope.RESULT_CONTRACT.equals(envelope.payload().resultContract())) {
            throw invalid("Unsupported Delivery Effectiveness Assessment result contract.");
        }
        validateJob(envelope.payload().job());
    }

    private void validateJob(DeliveryEffectivenessAssessmentJobStateSnapshot job) {
        if (job == null
                || !TERMINAL_STATUSES.contains(job.status())
                || !StringUtils.hasText(job.jobId())
                || !StringUtils.hasText(job.jiraProject())
                || job.fromDate() == null
                || job.toDate() == null
                || job.fromDate().isAfter(job.toDate())
                || job.createdAt() == null
                || job.updatedAt() == null
                || job.completedAt() == null
                || job.aggregate() == null) {
            throw invalid("Only a completed Delivery Effectiveness Assessment result can be imported.");
        }
        validateAggregate(job);
    }

    private void validateAggregate(DeliveryEffectivenessAssessmentJobStateSnapshot job) {
        var aggregate = job.aggregate();
        if (aggregate.totalUnits() != job.units().size()) {
            throw invalid("Delivery Effectiveness Assessment export has inconsistent aggregate data.");
        }
        if ("FAILED".equals(job.status())) {
            return;
        }
        if (aggregate.assessedUnits() != count(job.units(), "COMPLETED")
                || aggregate.excludedUnits() != count(job.units(), "EXCLUDED")
                || aggregate.notScorableUnits() != count(job.units(), "NOT_SCORABLE")
                || aggregate.failedUnits() != count(job.units(), "FAILED")
                || aggregate.assessedUnits()
                + aggregate.excludedUnits()
                + aggregate.notScorableUnits()
                + aggregate.failedUnits() != aggregate.totalUnits()) {
            throw invalid("Delivery Effectiveness Assessment export has inconsistent unit status totals.");
        }
    }

    private int count(List<DeliveryAssessmentUnitResponse> units, String status) {
        return (int) units.stream()
                .filter(Objects::nonNull)
                .filter(unit -> Objects.equals(unit.status(), status))
                .count();
    }

    private DeliveryEffectivenessAssessmentJobStateSnapshot asImported(
            DeliveryEffectivenessAssessmentJobStateSnapshot source,
            Instant importedAt
    ) {
        return new DeliveryEffectivenessAssessmentJobStateSnapshot(
                "delivery-assessment-import-" + UUID.randomUUID(),
                source.jiraProject(),
                source.fromDate(),
                source.toDate(),
                source.aiModel(),
                source.reasoningEffort(),
                source.status(),
                source.currentStepCode(),
                source.currentStepLabel(),
                source.errorCode(),
                source.errorMessage(),
                source.createdAt(),
                importedAt,
                source.completedAt(),
                source.discoveredIssues(),
                source.processedIssues(),
                source.totalIssues(),
                source.effectiveJql(),
                source.steps(),
                source.contextSections(),
                source.aiActivityEvents(),
                source.units(),
                source.aggregate(),
                source.visibilityLimits(),
                source.report()
        );
    }

    private DeliveryAssessmentImportException invalid(String message) {
        return new DeliveryAssessmentImportException(message);
    }
}
