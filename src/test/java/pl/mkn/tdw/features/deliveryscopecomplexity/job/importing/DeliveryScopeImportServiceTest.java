package pl.mkn.tdw.features.deliveryscopecomplexity.job.importing;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.mkn.tdw.features.deliveryscopecomplexity.job.api.DeliveryScopeAggregateResponse;
import pl.mkn.tdw.features.deliveryscopecomplexity.job.api.DeliveryScopeIssueResponse;
import pl.mkn.tdw.features.deliveryscopecomplexity.job.api.DeliveryScopeComplexityJobStateSnapshot;
import pl.mkn.tdw.features.deliveryscopecomplexity.job.error.DeliveryScopeImportException;
import pl.mkn.tdw.features.deliveryscopecomplexity.job.error.DeliveryScopeImportPersistenceException;
import pl.mkn.tdw.features.deliveryscopecomplexity.job.export.DeliveryScopeComplexityExportEnvelope;
import pl.mkn.tdw.features.deliveryscopecomplexity.job.localworkspace.DeliveryScopeLocalRunPersistence;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeliveryScopeImportServiceTest {

    @Mock
    private DeliveryScopeLocalRunPersistence localRunPersistence;

    private final LocalWorkspaceProperties workspaceProperties = new LocalWorkspaceProperties();
    private DeliveryScopeImportService service;

    @BeforeEach
    void setUp() {
        var objectMapper = JsonMapper.builder().findAndAddModules().build();
        service = new DeliveryScopeImportService(objectMapper, workspaceProperties, localRunPersistence);
        workspaceProperties.setEnabled(true);
    }

    @Test
    void shouldImportTerminalAssessmentUnderNewReadOnlyRunId() {
        var objectMapper = JsonMapper.builder().findAndAddModules().build();
        var source = snapshot("COMPLETED_WITH_WARNINGS", aggregate(0));
        var document = objectMapper.valueToTree(
                DeliveryScopeComplexityExportEnvelope.from(source, source.completedAt())
        );

        var imported = service.importReadOnly(document);

        assertThat(imported.jobId()).startsWith("delivery-scope-import-").isNotEqualTo(source.jobId());
        assertThat(imported.status()).isEqualTo("COMPLETED_WITH_WARNINGS");
        assertThat(imported.jiraProject()).isEqualTo("CRM");
        assertThat(imported.createdAt()).isEqualTo(source.createdAt());
        assertThat(imported.completedAt()).isEqualTo(source.completedAt());
        assertThat(imported.updatedAt()).isAfter(source.updatedAt());
        verify(localRunPersistence).persistRunSnapshot(imported);
    }

    @Test
    void shouldReadLegacyV1IssueWithoutTimeTrackingFields() throws Exception {
        var objectMapper = JsonMapper.builder().findAndAddModules().build();

        var issue = objectMapper.readValue("""
                {
                  "issueKey": "CRM-1",
                  "issueUrl": "https://jira.example.com/browse/CRM-1",
                  "summary": "Legacy issue",
                  "issueType": "Story",
                  "doneAt": "2026-07-10T10:00:00Z",
                  "team": null
                }
                """, DeliveryScopeIssueResponse.class);

        assertThat(issue.timeSpentSeconds()).isNull();
        assertThat(issue.originalEstimateSeconds()).isNull();
        assertThat(issue.remainingEstimateSeconds()).isNull();
        assertThat(issue.timeTrackingCapturedAt()).isNull();
    }

    @Test
    void shouldRejectUnsupportedVersion() {
        var objectMapper = JsonMapper.builder().findAndAddModules().build();
        var document = (ObjectNode) objectMapper.valueToTree(
                DeliveryScopeComplexityExportEnvelope.from(
                        snapshot("COMPLETED", aggregate(0)),
                        Instant.parse("2026-08-17T10:00:00Z")
                )
        );
        document.put("version", 3);

        assertThatThrownBy(() -> service.importReadOnly(document))
                .isInstanceOf(DeliveryScopeImportException.class)
                .hasMessage("Unsupported Delivery Scope Complexity export version.");
    }

    @Test
    void shouldRejectAnyOtherResultContract() {
        var objectMapper = JsonMapper.builder().findAndAddModules().build();
        var document = (ObjectNode) objectMapper.valueToTree(
                DeliveryScopeComplexityExportEnvelope.from(
                        snapshot("COMPLETED", aggregate(0)),
                        Instant.parse("2026-08-17T10:00:00Z")
                )
        );
        ((ObjectNode) document.path("payload")).put("resultContract", "delivery-scope-complexity-v0");

        assertThatThrownBy(() -> service.importReadOnly(document))
                .isInstanceOf(DeliveryScopeImportException.class)
                .hasMessage("Unsupported Delivery Scope Complexity result contract.");
    }

    @Test
    void shouldRejectNonTerminalAssessmentAndInconsistentAggregate() {
        var objectMapper = JsonMapper.builder().findAndAddModules().build();
        var active = objectMapper.valueToTree(
                DeliveryScopeComplexityExportEnvelope.from(
                        snapshot("ANALYZING", aggregate(0)),
                        Instant.parse("2026-08-17T10:00:00Z")
                )
        );
        var inconsistent = objectMapper.valueToTree(
                DeliveryScopeComplexityExportEnvelope.from(
                        snapshot("COMPLETED", aggregate(1)),
                        Instant.parse("2026-08-17T10:00:00Z")
                )
        );

        assertThatThrownBy(() -> service.importReadOnly(active))
                .isInstanceOf(DeliveryScopeImportException.class)
                .hasMessage("Only a completed Delivery Scope Complexity result can be imported.");
        assertThatThrownBy(() -> service.importReadOnly(inconsistent))
                .isInstanceOf(DeliveryScopeImportException.class)
                .hasMessage("Delivery Scope Complexity export has inconsistent aggregate data.");
    }

    @Test
    void shouldExposeUnavailableHistoryWithoutReturningAnUnstoredImport() {
        var objectMapper = JsonMapper.builder().findAndAddModules().build();
        var document = objectMapper.valueToTree(
                DeliveryScopeComplexityExportEnvelope.from(
                        snapshot("FAILED", aggregate(0)),
                        Instant.parse("2026-08-17T10:00:00Z")
                )
        );

        workspaceProperties.setEnabled(false);
        assertThatThrownBy(() -> service.importReadOnly(document))
                .isInstanceOf(DeliveryScopeImportPersistenceException.class);

        workspaceProperties.setEnabled(true);
        doThrow(new IllegalStateException("disk unavailable")).when(localRunPersistence).persistRunSnapshot(any());
        assertThatThrownBy(() -> service.importReadOnly(document))
                .isInstanceOf(DeliveryScopeImportPersistenceException.class);
    }

    private DeliveryScopeComplexityJobStateSnapshot snapshot(
            String status,
            DeliveryScopeAggregateResponse aggregate
    ) {
        var terminal = List.of("COMPLETED", "COMPLETED_WITH_WARNINGS", "FAILED").contains(status);
        return new DeliveryScopeComplexityJobStateSnapshot(
                "delivery-scope-1",
                "CRM",
                LocalDate.parse("2026-07-01"),
                LocalDate.parse("2026-07-31"),
                "gpt-5.4-mini",
                "medium",
                status,
                terminal ? "COMPLETED" : "UNIT_ASSESSMENT",
                terminal ? "Completed" : "Delivery Unit assessment",
                null,
                null,
                Instant.parse("2026-08-17T09:00:00Z"),
                Instant.parse("2026-08-17T09:30:00Z"),
                terminal ? Instant.parse("2026-08-17T09:30:00Z") : null,
                0,
                0,
                0,
                "project = CRM",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                aggregate
        );
    }

    private DeliveryScopeAggregateResponse aggregate(int totalUnits) {
        return new DeliveryScopeAggregateResponse(
                0.0,
                0.0,
                totalUnits,
                0,
                0,
                0,
                0,
                0,
                "LOW",
                null
        );
    }
}
