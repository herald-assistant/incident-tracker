package pl.mkn.tdw.features.deliverycomplexityassessment.job.importing;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pl.mkn.tdw.features.deliverycomplexityassessment.job.api.DeliveryAssessmentAggregateResponse;
import pl.mkn.tdw.features.deliverycomplexityassessment.job.api.DeliveryAssessmentIssueResponse;
import pl.mkn.tdw.features.deliverycomplexityassessment.job.api.DeliveryComplexityAssessmentJobStateSnapshot;
import pl.mkn.tdw.features.deliverycomplexityassessment.job.error.DeliveryAssessmentImportException;
import pl.mkn.tdw.features.deliverycomplexityassessment.job.error.DeliveryAssessmentImportPersistenceException;
import pl.mkn.tdw.features.deliverycomplexityassessment.job.export.DeliveryComplexityAssessmentExportEnvelope;
import pl.mkn.tdw.features.deliverycomplexityassessment.job.localworkspace.DeliveryAssessmentLocalRunPersistence;
import pl.mkn.tdw.localworkspace.LocalWorkspaceProperties;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DeliveryAssessmentImportServiceTest {

    @Mock
    private DeliveryAssessmentLocalRunPersistence localRunPersistence;

    private final LocalWorkspaceProperties workspaceProperties = new LocalWorkspaceProperties();
    private DeliveryAssessmentImportService service;

    @BeforeEach
    void setUp() {
        var objectMapper = JsonMapper.builder().findAndAddModules().build();
        service = new DeliveryAssessmentImportService(objectMapper, workspaceProperties, localRunPersistence);
        workspaceProperties.setEnabled(true);
    }

    @Test
    void shouldImportTerminalAssessmentUnderNewReadOnlyRunId() {
        var objectMapper = JsonMapper.builder().findAndAddModules().build();
        var source = snapshot("COMPLETED_WITH_WARNINGS", aggregate(0));
        var document = objectMapper.valueToTree(
                DeliveryComplexityAssessmentExportEnvelope.from(source, source.completedAt())
        );

        var imported = service.importReadOnly(document);

        assertThat(imported.jobId()).startsWith("delivery-assessment-import-").isNotEqualTo(source.jobId());
        assertThat(imported.status()).isEqualTo("COMPLETED_WITH_WARNINGS");
        assertThat(imported.jiraProject()).isEqualTo("CRM");
        assertThat(imported.createdAt()).isEqualTo(source.createdAt());
        assertThat(imported.completedAt()).isEqualTo(source.completedAt());
        assertThat(imported.updatedAt()).isAfter(source.updatedAt());
        verify(localRunPersistence).persistRunSnapshot(imported);
    }

    @Test
    void shouldRejectUnsupportedVersion() {
        var objectMapper = JsonMapper.builder().findAndAddModules().build();
        var document = (ObjectNode) objectMapper.valueToTree(
                DeliveryComplexityAssessmentExportEnvelope.from(
                        snapshot("COMPLETED", aggregate(0)),
                        Instant.parse("2026-08-17T10:00:00Z")
                )
        );
        document.put("version", 3);

        assertThatThrownBy(() -> service.importReadOnly(document))
                .isInstanceOf(DeliveryAssessmentImportException.class)
                .hasMessage("Unsupported Delivery Complexity Assessment export version.");
    }

    @Test
    void shouldRejectAnyOtherResultContract() {
        var objectMapper = JsonMapper.builder().findAndAddModules().build();
        var document = (ObjectNode) objectMapper.valueToTree(
                DeliveryComplexityAssessmentExportEnvelope.from(
                        snapshot("COMPLETED", aggregate(0)),
                        Instant.parse("2026-08-17T10:00:00Z")
                )
        );
        ((ObjectNode) document.path("payload")).put("resultContract", "delivery-complexity-assessment-v0");

        assertThatThrownBy(() -> service.importReadOnly(document))
                .isInstanceOf(DeliveryAssessmentImportException.class)
                .hasMessage("Unsupported Delivery Complexity Assessment result contract.");
    }

    @Test
    void shouldRejectNonTerminalAssessmentAndInconsistentAggregate() {
        var objectMapper = JsonMapper.builder().findAndAddModules().build();
        var active = objectMapper.valueToTree(
                DeliveryComplexityAssessmentExportEnvelope.from(
                        snapshot("ANALYZING", aggregate(0)),
                        Instant.parse("2026-08-17T10:00:00Z")
                )
        );
        var inconsistent = objectMapper.valueToTree(
                DeliveryComplexityAssessmentExportEnvelope.from(
                        snapshot("COMPLETED", aggregate(1)),
                        Instant.parse("2026-08-17T10:00:00Z")
                )
        );

        assertThatThrownBy(() -> service.importReadOnly(active))
                .isInstanceOf(DeliveryAssessmentImportException.class)
                .hasMessage("Only a completed Delivery Complexity Assessment result can be imported.");
        assertThatThrownBy(() -> service.importReadOnly(inconsistent))
                .isInstanceOf(DeliveryAssessmentImportException.class)
                .hasMessage("Delivery Complexity Assessment export has inconsistent aggregate data.");
    }

    @Test
    void shouldExposeUnavailableHistoryWithoutReturningAnUnstoredImport() {
        var objectMapper = JsonMapper.builder().findAndAddModules().build();
        var document = objectMapper.valueToTree(
                DeliveryComplexityAssessmentExportEnvelope.from(
                        snapshot("FAILED", aggregate(0)),
                        Instant.parse("2026-08-17T10:00:00Z")
                )
        );

        workspaceProperties.setEnabled(false);
        assertThatThrownBy(() -> service.importReadOnly(document))
                .isInstanceOf(DeliveryAssessmentImportPersistenceException.class);

        workspaceProperties.setEnabled(true);
        doThrow(new IllegalStateException("disk unavailable")).when(localRunPersistence).persistRunSnapshot(any());
        assertThatThrownBy(() -> service.importReadOnly(document))
                .isInstanceOf(DeliveryAssessmentImportPersistenceException.class);
    }

    @Test
    void shouldRoundTripEveryInvocationAndRejectThePreviousSinglePromptShape() {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var state = new pl.mkn.tdw.features.deliverycomplexityassessment.job.state.DeliveryComplexityAssessmentJobState("crm-run",
                new pl.mkn.tdw.features.deliverycomplexityassessment.job.api.DeliveryComplexityAssessmentJobStartRequest("CRM", LocalDate.parse("2026-07-01"), LocalDate.parse("2026-07-31"), "crm-model", "medium"));
        var unit = pl.mkn.tdw.features.deliverycomplexityassessment.DeliveryAssessmentTestFixtures.unit("CRM-1",
                pl.mkn.tdw.features.deliverycomplexityassessment.DeliveryAssessmentTestFixtures.mergeRequest(1, "src/Customer.java", "+ customer status"));
        state.markDiscoveryStarted();
        state.markUnitsReady(new pl.mkn.tdw.features.deliverycomplexityassessment.source.DeliveryAssessmentSourceResult("project = CRM", 1, false, List.of(), List.of()), List.of(unit));
        state.markUnitAnalyzing(unit.unitId());
        var when = Instant.parse("2026-08-17T10:00:00Z");
        var fact = new pl.mkn.tdw.shared.ai.AnalysisAiFinding("customer-status", "outcomeBreadth", "Customer status validation", List.of("CRM/customer-api!1#metadata"), List.of("customer event"));
        var part = new pl.mkn.tdw.shared.ai.AnalysisAiInvocation("call-1", "EVIDENCE_PART", "COMPLETED", 1, 2,
                fact.references(), 100, 500, 16, "CRM part prompt", "{CRM part}", List.of(fact), List.of("CRM visibility"), "crm-session-1", null, null, when, when);
        var synthesis = new pl.mkn.tdw.shared.ai.AnalysisAiInvocation("call-2", "SYNTHESIS", "FAILED", 1, 1,
                fact.references(), 200, 500, 16, "CRM synthesis prompt", "invalid JSON", List.of(), List.of(), "crm-session-2", null, "Malformed result", when, when);
        state.markUnitAiInvocation(unit.unitId(), part);
        state.markUnitAiInvocation(unit.unitId(), synthesis);
        state.markUnitFailed(unit.unitId(), "AI_FAILURE", "Malformed result");
        state.finalizeJob();
        var document = (ObjectNode) mapper.valueToTree(pl.mkn.tdw.features.deliverycomplexityassessment.job.export.DeliveryComplexityAssessmentExportEnvelope.from(state.snapshot(), when));

        var imported = service.importReadOnly(document);
        assertThat(imported.units().get(0).aiInvocations()).containsExactly(part, synthesis);
        var unitJson = (ObjectNode) document.path("payload").path("job").path("units").path(0);
        unitJson.remove("aiInvocations");
        unitJson.put("preparedPrompt", "CRM previous prompt");
        unitJson.put("rawAiResponse", "CRM previous response");
        assertThatThrownBy(() -> service.importReadOnly(document)).isInstanceOf(DeliveryAssessmentImportException.class);
    }

    private DeliveryComplexityAssessmentJobStateSnapshot snapshot(
            String status,
            DeliveryAssessmentAggregateResponse aggregate
    ) {
        var terminal = List.of("COMPLETED", "COMPLETED_WITH_WARNINGS", "FAILED").contains(status);
        return new DeliveryComplexityAssessmentJobStateSnapshot(
                "delivery-assessment-1",
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

    private DeliveryAssessmentAggregateResponse aggregate(int totalUnits) {
        return new DeliveryAssessmentAggregateResponse(
                0,
                Map.of(),
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
