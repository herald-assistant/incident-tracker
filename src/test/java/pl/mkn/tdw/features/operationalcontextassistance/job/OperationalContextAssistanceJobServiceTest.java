package pl.mkn.tdw.features.operationalcontextassistance.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.mockito.ArgumentCaptor;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceAiInput;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceCatalogMaterial;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceCatalogMaterialService;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceCopilotProvider;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceCopilotResult;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceMode;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistancePromptPreparation;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistancePromptPreparationService;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceRepositoryFacts;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobStartRequest;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceBatchReviewRequest;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobStatus;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceProposalDecisionRequest;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceReviewDraft;
import pl.mkn.tdw.features.operationalcontextassistance.draft.OperationalContextAssistanceDraft;
import pl.mkn.tdw.features.operationalcontextassistance.draft.OperationalContextAssistanceDraftParser;
import pl.mkn.tdw.features.operationalcontextassistance.draft.OperationalContextAssistanceDraftPreflight;
import pl.mkn.tdw.features.operationalcontextassistance.draft.OperationalContextAssistanceDraftScope;
import pl.mkn.tdw.features.operationalcontextassistance.job.localworkspace.OperationalContextAssistanceLocalRunPersistence;
import pl.mkn.tdw.features.operationalcontextassistance.job.localworkspace.OperationalContextAssistanceStoredRun;
import pl.mkn.tdw.features.operationalcontextassistance.source.OperationalContextGitLabSourceCollector;
import pl.mkn.tdw.features.operationalcontextassistance.source.OperationalContextGitLabSourceFile;
import pl.mkn.tdw.features.operationalcontextassistance.source.OperationalContextGitLabSourceSelectionException;
import pl.mkn.tdw.features.operationalcontextassistance.source.OperationalContextGitLabSourceSnapshot;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogMaintenanceService;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogConditionalBatchCommand;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogBatchMutationPreview;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogBatchMutationResult;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogMaintenanceException;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogMutationPreview;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogValidationService;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextEditableEntity;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchScope;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextSystem;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextSnapshot;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotExecutionResult;
import pl.mkn.tdw.aiplatform.copilot.runtime.context.CopilotRequiredContextTierException;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;
import pl.mkn.tdw.shared.ai.AnalysisAiUsage;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OperationalContextAssistanceJobServiceTest {

    private final OperationalContextPort catalogPort = mock(OperationalContextPort.class);
    private final OperationalContextAssistanceCatalogMaterialService catalogMaterialService =
            mock(OperationalContextAssistanceCatalogMaterialService.class);
    private final OperationalContextCatalogMaintenanceService maintenanceService =
            mock(OperationalContextCatalogMaintenanceService.class);
    private final OperationalContextCatalogValidationService validationService =
            mock(OperationalContextCatalogValidationService.class);
    private final OperationalContextGitLabSourceCollector sourceCollector = mock(OperationalContextGitLabSourceCollector.class);
    private final OperationalContextAssistancePromptPreparationService promptService =
            mock(OperationalContextAssistancePromptPreparationService.class);
    private final OperationalContextAssistanceCopilotProvider copilotProvider =
            mock(OperationalContextAssistanceCopilotProvider.class);
    private final OperationalContextAssistanceDraftParser parser = mock(OperationalContextAssistanceDraftParser.class);
    private final OperationalContextAssistanceDraftPreflight preflight = mock(OperationalContextAssistanceDraftPreflight.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TaskExecutor directExecutor = Runnable::run;
    private final AnalysisAiAuthRefResolver authResolver = () -> AnalysisAiAuthRef.localToken("test");
    private final OperationalContextAssistanceLocalRunPersistence localRunPersistence =
            mock(OperationalContextAssistanceLocalRunPersistence.class);
    private OperationalContextAssistanceJobService service;

    @BeforeEach
    void setUp() {
        service = new OperationalContextAssistanceJobService(
                catalogPort, catalogMaterialService, maintenanceService, validationService, sourceCollector,
                promptService, copilotProvider, parser, preflight, objectMapper, directExecutor, authResolver,
                localRunPersistence
        );
        when(catalogPort.currentSnapshot()).thenReturn(new OperationalContextSnapshot(
                "digest-1", "local", OperationalContextCatalog.empty()
        ));
        when(catalogMaterialService.capture()).thenReturn(new OperationalContextAssistanceCatalogMaterial(
                "digest-1", OperationalContextCatalog.empty(), Map.of(), Map.of()));
        when(promptService.prepare(any())).thenReturn(new OperationalContextAssistancePromptPreparation(
                "sanitized prompt", Map.of("input.json", "{\"visibilityLimits\":[]}"), Set.of("operator:description")
        ));
        when(preflight.validateParsed(any(), anyString())).thenReturn(
                new OperationalContextAssistanceDraftPreflight.Result(true, false, List.of()));
    }

    @Test
    void createsReadOnlyReviewedDraftFromEmptyCatalog() {
        var usage = new AnalysisAiUsage(100, 50, 0, 0, 150, 0.01, 1000, 1, "test-model", null, null, null);
        when(copilotProvider.execute(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new OperationalContextAssistanceCopilotResult(
                        new CopilotExecutionResult("{draft}", usage), Set.of()));
        var draft = new OperationalContextAssistanceDraft(
                List.of(new OperationalContextAssistanceDraft.Proposal(
                        OperationalContextAssistanceDraft.Operation.CREATE, "system", "order-intake",
                        List.of(new OperationalContextAssistanceDraft.FieldChange(
                                "name", null, "Order Intake", "Nazwa podana przez operatora.",
                                OperationalContextAssistanceDraft.Basis.USER_STATEMENT,
                                List.of("operator:description"),
                                OperationalContextAssistanceDraft.Confidence.HIGH, false
                        )),
                        OperationalContextAssistanceDraft.Confidence.HIGH, false, List.of()
                )), List.of()
        );
        when(parser.parse(anyString(), any())).thenReturn(draft);
        when(maintenanceService.previewCreate(any())).thenReturn(new OperationalContextCatalogMutationPreview(
                "system", "order-intake", "digest-1", Map.of("id", "order-intake", "name", "Order Intake"), List.of()
        ));

        var accepted = service.startJob(new OperationalContextAssistanceJobStartRequest(
                OperationalContextAssistanceMode.CREATE_AREA, "Order Intake przyjmuje zlecenia.",
                null, null, null, null, null
        ));
        var finalState = service.getJob(accepted.jobId());

        assertThat(accepted.status()).isEqualTo(OperationalContextAssistanceJobStatus.QUEUED);
        assertThat(finalState.status()).isEqualTo(OperationalContextAssistanceJobStatus.COMPLETED);
        assertThat(finalState.catalogDigest()).isEqualTo("digest-1");
        assertThat(finalState.previews()).hasSize(1);
        assertThat(finalState.previews().get(0).valid()).isTrue();
        assertThat(finalState.usage()).isEqualTo(usage);
        assertThat(finalState.preparedPrompt()).isEqualTo("sanitized prompt");
        var persisted = ArgumentCaptor.forClass(pl.mkn.tdw.features.operationalcontextassistance.api
                .OperationalContextAssistanceJobSnapshot.class);
        verify(localRunPersistence, atLeast(3)).persistRunSnapshot(persisted.capture(), any(), any());
        assertThat(persisted.getAllValues()).anySatisfy(snapshot -> {
            assertThat(snapshot.status()).isEqualTo(OperationalContextAssistanceJobStatus.ANALYZING);
            assertThat(snapshot.preparedPrompt()).isEqualTo("sanitized prompt");
        });
        assertThat(persisted.getAllValues().get(persisted.getAllValues().size() - 1).status())
                .isEqualTo(OperationalContextAssistanceJobStatus.COMPLETED);
        assertThat(finalState.steps()).extracting(step -> step.status())
                .containsExactly("COMPLETED", "COMPLETED", "COMPLETED");
        verify(preflight).validateParsed(draft, "digest-1");
        verify(maintenanceService, never()).create(any());
        verify(maintenanceService, never()).update(any());
    }

    @Test
    void blocksRunWhenRequiredLongContextCannotBeEstablished() {
        when(copilotProvider.execute(anyString(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new CopilotRequiredContextTierException(
                        "Wybrany model nie udostępnia wymaganego długiego kontekstu."));

        var accepted = service.startJob(new OperationalContextAssistanceJobStartRequest(
                OperationalContextAssistanceMode.CREATE_AREA, "Uściślij istniejący termin.",
                null, null, null, null, null));
        var finalState = service.getJob(accepted.jobId());

        assertThat(finalState.status()).isEqualTo(OperationalContextAssistanceJobStatus.BLOCKED);
        assertThat(finalState.errorCode()).isEqualTo("COPILOT_LONG_CONTEXT_UNAVAILABLE");
        assertThat(finalState.errorMessage()).contains("długiego kontekstu");
    }

    @Test
    void defersPreviewWhenMaintenanceAssessmentUsedAnotherCatalogDigest() {
        when(copilotProvider.execute(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new OperationalContextAssistanceCopilotResult(
                        new CopilotExecutionResult("{draft}", null), Set.of()));
        when(parser.parse(anyString(), any())).thenReturn(new OperationalContextAssistanceDraft(
                List.of(new OperationalContextAssistanceDraft.Proposal(
                        OperationalContextAssistanceDraft.Operation.CREATE, "system", "order-intake",
                        List.of(new OperationalContextAssistanceDraft.FieldChange(
                                "name", null, "Order Intake", "Nazwa podana przez operatora.",
                                OperationalContextAssistanceDraft.Basis.USER_STATEMENT,
                                List.of("operator:description"),
                                OperationalContextAssistanceDraft.Confidence.HIGH, false
                        )),
                        OperationalContextAssistanceDraft.Confidence.HIGH, false, List.of()
                )), List.of()
        ));
        when(maintenanceService.previewCreate(any())).thenReturn(new OperationalContextCatalogMutationPreview(
                "system", "order-intake", "digest-2",
                Map.of("id", "order-intake", "name", "Order Intake"), List.of()
        ));

        var accepted = service.startJob(new OperationalContextAssistanceJobStartRequest(
                OperationalContextAssistanceMode.CREATE_AREA, "Order Intake przyjmuje zlecenia.",
                null, null, null, null, null
        ));
        var finalState = service.getJob(accepted.jobId());

        assertThat(finalState.status()).isEqualTo(OperationalContextAssistanceJobStatus.PARTIAL);
        assertThat(finalState.previews()).hasSize(1);
        assertThat(finalState.previews().get(0).validationStatus())
                .isEqualTo(pl.mkn.tdw.features.operationalcontextassistance.api
                        .OperationalContextAssistanceProposalPreview.ValidationStatus.DEFERRED);
        assertThat(finalState.previews().get(0).valid()).isFalse();
        verify(maintenanceService, never()).create(any());
    }

    @Test
    void unavailableSelectedSourcePreservesNonConversationalBlockedResultAndUsage() {
        when(sourceCollector.collect("crm-contact-api", "main")).thenReturn(new OperationalContextGitLabSourceSnapshot(
                "crm-contact-api", null, "main", null, List.of(), List.of("Nie udało się przypiąć ref.")
        ));
        when(copilotProvider.execute(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new OperationalContextAssistanceCopilotResult(
                        new CopilotExecutionResult("{question}", null), Set.of()));
        when(parser.parse(anyString(), any())).thenReturn(new OperationalContextAssistanceDraft(
                List.of(), List.of("Brak potwierdzonej treści projektu CRM.")
        ));

        var accepted = service.startJob(new OperationalContextAssistanceJobStartRequest(
                OperationalContextAssistanceMode.CREATE_AREA, "Uzupełnij dane CRM Contact API.",
                null, new OperationalContextAssistanceJobStartRequest.GitLabSource("crm-contact-api", null, "main"),
                null, null, null
        ));
        var finalState = service.getJob(accepted.jobId());

        assertThat(finalState.status()).isEqualTo(OperationalContextAssistanceJobStatus.BLOCKED);
        assertThat(finalState.errorCode()).isEqualTo("OPCTX_ASSISTANCE_NO_PROPOSALS");
        assertThat(finalState.errorMessage()).contains("nie odczytano kodu");
        assertThat(finalState.sourceRevision()).isNull();
        assertThat(finalState.visibilityLimits()).contains("Nie udało się przypiąć ref.");
        assertThat(finalState.visibilityLimits()).contains("Brak potwierdzonej treści projektu CRM.");
        assertThat(finalState.draft().proposals()).isEmpty();
        verify(maintenanceService, never()).create(any());
    }

    @Test
    void emptyNonConversationalDraftKeepsAUsefulLimitationInsteadOfFailing() {
        when(copilotProvider.execute(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new OperationalContextAssistanceCopilotResult(
                        new CopilotExecutionResult("{\"proposals\":[],\"visibilityLimits\":[]}", null),
                        Set.of()));
        when(parser.parse(anyString(), any())).thenReturn(new OperationalContextAssistanceDraft(
                List.of(), List.of()));

        var accepted = service.startJob(new OperationalContextAssistanceJobStartRequest(
                OperationalContextAssistanceMode.CREATE_AREA,
                "Uzupełnij dane CRM Contact API.", null, null, null, null, null));
        var finalState = service.getJob(accepted.jobId());

        assertThat(finalState.status()).isEqualTo(OperationalContextAssistanceJobStatus.BLOCKED);
        assertThat(finalState.errorCode()).isEqualTo("OPCTX_ASSISTANCE_NO_PROPOSALS");
        assertThat(finalState.visibilityLimits()).contains(
                "AI nie wskazało zmiany z wystarczającą podstawą w dostępnych źródłach.");
    }

    @Test
    void readingAnotherProjectDoesNotCountAsReadingTheSelectedRepository() {
        when(sourceCollector.collect("demo-app", "main")).thenReturn(new OperationalContextGitLabSourceSnapshot(
                "demo-app", new OperationalContextGitLabSourceSnapshot.RepositoryGit(
                        "gitlab", "crm", "demo-app", "crm/demo-app", null
                ), "main", "a".repeat(40), List.of(), List.of()
        ));
        var otherSourceRef = "gitlab:crm/library@" + "b".repeat(40) + ":pom.xml";
        when(copilotProvider.execute(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new OperationalContextAssistanceCopilotResult(
                        new CopilotExecutionResult("{question}", null), Set.of(otherSourceRef)));
        when(parser.parse(anyString(), any())).thenReturn(new OperationalContextAssistanceDraft(
                List.of(), List.of()
        ));

        var accepted = service.startJob(new OperationalContextAssistanceJobStartRequest(
                OperationalContextAssistanceMode.CREATE_AREA, "Sprawdź zależności aplikacji.",
                null, new OperationalContextAssistanceJobStartRequest.GitLabSource("demo-app", null, "main"),
                null, null, null
        ));
        var finalState = service.getJob(accepted.jobId());

        assertThat(finalState.status()).isEqualTo(OperationalContextAssistanceJobStatus.BLOCKED);
        assertThat(finalState.errorMessage()).contains("nie odczytano kodu");
        assertThat(finalState.sourceRefs()).contains(otherSourceRef);
        assertThat(finalState.visibilityLimits()).anyMatch(limit -> limit.contains("wybranego projektu GitLab"));
    }

    @Test
    void rejectsInvalidProjectUrlBeforeSchedulingAssistanceJob() {
        var url = "https://other.example.com/CRM/PROCESSES/project";
        doThrow(new OperationalContextGitLabSourceSelectionException(
                "Projekt musi znajdować się na skonfigurowanym serwerze GitLab."
        )).when(sourceCollector).validateProjectUrl(url);

        assertThatThrownBy(() -> service.startJob(new OperationalContextAssistanceJobStartRequest(
                OperationalContextAssistanceMode.CREATE_AREA, "Proces obsługi profilu klienta", null,
                new OperationalContextAssistanceJobStartRequest.GitLabSource(null, url, "main"),
                null, null, null
        ))).isInstanceOf(OperationalContextGitLabSourceSelectionException.class);

        verify(sourceCollector, never()).collectUrl(url, "main");
        verify(copilotProvider, never()).execute(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void passesValidatedProjectUrlToCollector() {
        var url = "https://gitlab.example.com/CRM/PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS";
        when(sourceCollector.collectUrl(url, "main")).thenReturn(new OperationalContextGitLabSourceSnapshot(
                "PROCESSES/CRM_CUSTOMER_PROFILE_PROCESS", null, "main", null,
                List.of(), List.of("Nie udało się przypiąć ref.")
        ));
        when(copilotProvider.execute(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new OperationalContextAssistanceCopilotResult(
                        new CopilotExecutionResult("{question}", null), Set.of()));
        when(parser.parse(anyString(), any())).thenReturn(new OperationalContextAssistanceDraft(
                List.of(), List.of()
        ));

        service.startJob(new OperationalContextAssistanceJobStartRequest(
                OperationalContextAssistanceMode.CREATE_AREA, "Proces obsługi profilu klienta", null,
                new OperationalContextAssistanceJobStartRequest.GitLabSource(null, url, "main"),
                null, null, null
        ));

        verify(sourceCollector).validateProjectUrl(url);
        verify(sourceCollector).collectUrl(url, "main");
    }

    @Test
    void authorizesOnlySelectedExistingSystemScopeAndSendsItsCurrentRepositoryList() {
        var facts = new OperationalContextAssistanceRepositoryFacts(
                OperationalContextAssistanceRepositoryFacts.Usage.SHARED_LIBRARY,
                null, null, List.of("system-a")
        );
        var currentRepositories = List.of(Map.of(
                "repoId", "existing-repo", "role", "primary", "priority", 1
        ));
        var catalog = catalog(
                List.of(system("system-a"), system("system-b")),
                List.of(scope("scope-a", "system-a"), scope("scope-b", "system-b"))
        );
        when(catalogPort.currentSnapshot()).thenReturn(new OperationalContextSnapshot(
                "digest-1", "local", catalog
        ));
        when(catalogMaterialService.capture()).thenReturn(new OperationalContextAssistanceCatalogMaterial(
                "digest-1", catalog, Map.of(), Map.of()));
        when(maintenanceService.writablePayloadForUpdate("code-search-scope", "scope-a"))
                .thenReturn(Map.of("repositories", currentRepositories));
        var sourceRef = "gitlab:crm/library@" + "a".repeat(40) + ":README.md";
        when(sourceCollector.collect("library", "main")).thenReturn(new OperationalContextGitLabSourceSnapshot(
                "library", new OperationalContextGitLabSourceSnapshot.RepositoryGit(
                        "gitlab", "crm", "library", "crm/library", null
                ), "main", "a".repeat(40),
                List.of(new OperationalContextGitLabSourceFile("README.md", "Library docs", sourceRef)), List.of()
        ));
        when(promptService.prepare(any())).thenReturn(new OperationalContextAssistancePromptPreparation(
                "sanitized prompt", Map.of("input.json", "{\"visibilityLimits\":[]}"),
                Set.of("operator:description", "operator:repository-facts", sourceRef)
        ));
        when(copilotProvider.execute(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new OperationalContextAssistanceCopilotResult(
                        new CopilotExecutionResult("{question}", null), Set.of()));
        when(parser.parse(anyString(), any())).thenReturn(new OperationalContextAssistanceDraft(
                List.of(), List.of()
        ));

        var accepted = service.startJob(new OperationalContextAssistanceJobStartRequest(
                OperationalContextAssistanceMode.CREATE_AREA, "Biblioteka wspólna",
                null, new OperationalContextAssistanceJobStartRequest.GitLabSource("library", null, "main"),
                facts, null, null
        ));

        assertThat(service.getJob(accepted.jobId()).status()).isEqualTo(OperationalContextAssistanceJobStatus.BLOCKED);
        var input = ArgumentCaptor.forClass(OperationalContextAssistanceAiInput.class);
        verify(promptService).prepare(input.capture());
        assertThat(input.getValue().repositoryFacts()).isEqualTo(facts);
        var selected = input.getValue().catalogContext().path("selectedSystemScopes");
        assertThat(selected).hasSize(1);
        assertThat(selected.get(0).path("scopeId").asText()).isEqualTo("scope-a");
        assertThat(selected.get(0).path("beforeRepositories").get(0).path("repoId").asText())
                .isEqualTo("existing-repo");
        var scope = ArgumentCaptor.forClass(OperationalContextAssistanceDraftScope.class);
        verify(parser).parse(anyString(), scope.capture());
        assertThat(scope.getValue().selectedScopeIds()).containsExactly("scope-a");
        verify(maintenanceService, never()).writablePayloadForUpdate("code-search-scope", "scope-b");
    }

    @Test
    void blocksMissingSelectedSystemScopeBeforeAi() {
        var facts = new OperationalContextAssistanceRepositoryFacts(
                OperationalContextAssistanceRepositoryFacts.Usage.EXISTING_SYSTEM,
                null, null, List.of("system-a")
        );
        var catalog = catalog(List.of(system("system-a")), List.of());
        when(catalogPort.currentSnapshot()).thenReturn(new OperationalContextSnapshot(
                "digest-1", "local", catalog
        ));
        when(catalogMaterialService.capture()).thenReturn(new OperationalContextAssistanceCatalogMaterial(
                "digest-1", catalog, Map.of(), Map.of()));
        var accepted = service.startJob(new OperationalContextAssistanceJobStartRequest(
                OperationalContextAssistanceMode.CREATE_AREA, "Kod systemu",
                null, new OperationalContextAssistanceJobStartRequest.GitLabSource("library", null, "main"),
                facts, null, null
        ));

        var finalState = service.getJob(accepted.jobId());
        assertThat(finalState.status()).isEqualTo(OperationalContextAssistanceJobStatus.BLOCKED);
        assertThat(finalState.errorMessage()).contains("system-a", "0 zakresów");
        verify(promptService, never()).prepare(any());
        verify(copilotProvider, never()).execute(anyString(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void blocksSharedLibraryWhenAnySelectedConsumerHasAmbiguousScope() {
        var facts = new OperationalContextAssistanceRepositoryFacts(
                OperationalContextAssistanceRepositoryFacts.Usage.SHARED_LIBRARY,
                null, null, List.of("system-a", "system-b")
        );
        var catalog = catalog(List.of(system("system-a"), system("system-b")),
                List.of(scope("scope-a", "system-a"), scope("scope-b1", "system-b"),
                        scope("scope-b2", "system-b")));
        when(catalogPort.currentSnapshot()).thenReturn(new OperationalContextSnapshot("digest-1", "local", catalog));
        when(catalogMaterialService.capture()).thenReturn(new OperationalContextAssistanceCatalogMaterial(
                "digest-1", catalog, Map.of(), Map.of()));
        when(maintenanceService.writablePayloadForUpdate("code-search-scope", "scope-a"))
                .thenReturn(Map.of("repositories", List.of(Map.of("repoId", "existing-repo"))));

        var accepted = service.startJob(new OperationalContextAssistanceJobStartRequest(
                OperationalContextAssistanceMode.CREATE_AREA, "Biblioteka współdzielona", null,
                new OperationalContextAssistanceJobStartRequest.GitLabSource("library", null, "main"),
                facts, null, null
        ));

        assertThat(service.getJob(accepted.jobId()).status()).isEqualTo(OperationalContextAssistanceJobStatus.BLOCKED);
        assertThat(service.getJob(accepted.jobId()).errorMessage()).contains("system-b", "2 zakresów");
        verify(promptService, never()).prepare(any());
    }

    @Test
    void blocksSelectedSystemWithUnreadableRepositoryList() {
        var facts = new OperationalContextAssistanceRepositoryFacts(
                OperationalContextAssistanceRepositoryFacts.Usage.EXISTING_SYSTEM,
                null, null, List.of("system-a")
        );
        var catalog = catalog(List.of(system("system-a")), List.of(scope("scope-a", "system-a")));
        when(catalogPort.currentSnapshot()).thenReturn(new OperationalContextSnapshot("digest-1", "local", catalog));
        when(catalogMaterialService.capture()).thenReturn(new OperationalContextAssistanceCatalogMaterial(
                "digest-1", catalog, Map.of(), Map.of()));
        when(maintenanceService.writablePayloadForUpdate("code-search-scope", "scope-a"))
                .thenReturn(Map.of("name", "Niekompletny zakres"));

        var accepted = service.startJob(new OperationalContextAssistanceJobStartRequest(
                OperationalContextAssistanceMode.CREATE_AREA, "Kod systemu", null,
                new OperationalContextAssistanceJobStartRequest.GitLabSource("library", null, "main"),
                facts, null, null
        ));

        assertThat(service.getJob(accepted.jobId()).status()).isEqualTo(OperationalContextAssistanceJobStatus.BLOCKED);
        assertThat(service.getJob(accepted.jobId()).errorMessage()).contains("system-a", "nie ma listy repozytoriów");
        verify(promptService, never()).prepare(any());
    }

    @Test
    void blocksSelectedSystemWhenItsScopeExceedsAiContextLimit() {
        var facts = new OperationalContextAssistanceRepositoryFacts(
                OperationalContextAssistanceRepositoryFacts.Usage.EXISTING_SYSTEM,
                null, null, List.of("system-a")
        );
        var catalog = catalog(List.of(system("system-a")), List.of(scope("scope-a", "system-a")));
        when(catalogPort.currentSnapshot()).thenReturn(new OperationalContextSnapshot("digest-1", "local", catalog));
        when(catalogMaterialService.capture()).thenReturn(new OperationalContextAssistanceCatalogMaterial(
                "digest-1", catalog, Map.of(), Map.of()));
        when(maintenanceService.writablePayloadForUpdate("code-search-scope", "scope-a"))
                .thenReturn(Map.of("repositories", List.of(Map.of("reason", "x".repeat(33_000)))));

        var accepted = service.startJob(new OperationalContextAssistanceJobStartRequest(
                OperationalContextAssistanceMode.CREATE_AREA, "Kod systemu", null,
                new OperationalContextAssistanceJobStartRequest.GitLabSource("library", null, "main"),
                facts, null, null
        ));

        assertThat(service.getJob(accepted.jobId()).status()).isEqualTo(OperationalContextAssistanceJobStatus.BLOCKED);
        assertThat(service.getJob(accepted.jobId()).errorMessage()).contains("system-a", "limit kontekstu AI");
        verify(promptService, never()).prepare(any());
    }

    @Test
    void blocksUnknownSelectedSystemBeforePromptPreparation() {
        var facts = new OperationalContextAssistanceRepositoryFacts(
                OperationalContextAssistanceRepositoryFacts.Usage.EXISTING_SYSTEM,
                null, null, List.of("missing")
        );
        var accepted = service.startJob(new OperationalContextAssistanceJobStartRequest(
                OperationalContextAssistanceMode.CREATE_AREA, "Kod systemu",
                null, new OperationalContextAssistanceJobStartRequest.GitLabSource("library", null, "main"),
                facts, null, null
        ));

        assertThat(service.getJob(accepted.jobId()).status()).isEqualTo(OperationalContextAssistanceJobStatus.BLOCKED);
        verify(promptService, never()).prepare(any());
    }

    @Test
    void resolvesSecondValidationFindingByItsFingerprintAndExactEntity() {
        when(maintenanceService.entity("system", "order-intake")).thenReturn(new OperationalContextEditableEntity(
                "system", "order-intake", "systems.yml", Map.of("id", "order-intake", "name", "Order Intake")
        ));
        when(maintenanceService.writablePayloadForUpdate("system", "order-intake"))
                .thenReturn(Map.of("name", "Order Intake"));
        var first = new OperationalContextRelationIndex.ValidationFinding(
                "warning", "MISSING_OWNER", "Brak ownera w ownership",
                List.of(new OperationalContextRelationIndex.SourceRef(
                        "systems.yml", "system", "order-intake", "$.systems[id=order-intake]", "ownership"
                ))
        );
        var second = new OperationalContextRelationIndex.ValidationFinding(
                "warning", "MISSING_OWNER", "Brak ownera w handoff",
                List.of(new OperationalContextRelationIndex.SourceRef(
                        "systems.yml", "system", "order-intake", "$.systems[id=order-intake].handoff", "handoff"
                ))
        );
        when(validationService.validate(any())).thenReturn(
                new OperationalContextCatalogValidationService.ValidationReport(
                        List.of(first, second), List.of(
                                new OperationalContextCatalogValidationService.FingerprintedFinding(
                                        "fingerprint-first", first
                                ),
                                new OperationalContextCatalogValidationService.FingerprintedFinding(
                                        "fingerprint-second", second
                                )
                        )
                )
        );
        when(copilotProvider.execute(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new OperationalContextAssistanceCopilotResult(
                        new CopilotExecutionResult("{question}", null), Set.of()));
        when(parser.parse(anyString(), any())).thenReturn(new OperationalContextAssistanceDraft(
                List.of(), List.of()
        ));

        var accepted = service.startJob(new OperationalContextAssistanceJobStartRequest(
                OperationalContextAssistanceMode.RESOLVE_FINDING, "Wyjaśnij ownership",
                new OperationalContextAssistanceJobStartRequest.Target(
                        OperationalContextAssistanceJobStartRequest.Target.Kind.VALIDATION_FINDING,
                        "system", "order-intake", "fingerprint-second"
                ), null, null, null, null
        ));

        assertThat(service.getJob(accepted.jobId()).status())
                .isEqualTo(OperationalContextAssistanceJobStatus.BLOCKED);
        var input = ArgumentCaptor.forClass(OperationalContextAssistanceAiInput.class);
        verify(promptService).prepare(input.capture());
        assertThat(input.getValue().targetContext().path("finding").path("code").asText())
                .isEqualTo("MISSING_OWNER");
        assertThat(input.getValue().targetContext().path("finding").path("message").asText())
                .isEqualTo("Brak ownera w handoff");
    }

    @Test
    void sendsOnlyWritableTargetPayloadToAi() {
        when(maintenanceService.entity("integration", "order-to-crm"))
                .thenReturn(new OperationalContextEditableEntity(
                        "integration", "order-to-crm", "integrations.yml",
                        Map.of("id", "order-to-crm", "participants", List.of(Map.of(
                                "system", "order-intake", "legacyNote", "preserve-only"
                        )))
                ));
        when(maintenanceService.writablePayloadForUpdate("integration", "order-to-crm"))
                .thenReturn(Map.of("participants", List.of(Map.of("system", "order-intake"))));
        when(copilotProvider.execute(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new OperationalContextAssistanceCopilotResult(
                        new CopilotExecutionResult("{question}", null), Set.of()));
        when(parser.parse(anyString(), any())).thenReturn(new OperationalContextAssistanceDraft(
                List.of(), List.of()
        ));

        var accepted = service.startJob(new OperationalContextAssistanceJobStartRequest(
                OperationalContextAssistanceMode.IMPROVE_ENTITY, "Uzupełnij integrację",
                new OperationalContextAssistanceJobStartRequest.Target(
                        OperationalContextAssistanceJobStartRequest.Target.Kind.ENTITY,
                        "integration", "order-to-crm", null
                ), null, null, null, null
        ));

        assertThat(service.getJob(accepted.jobId()).status())
                .isEqualTo(OperationalContextAssistanceJobStatus.BLOCKED);
        var input = ArgumentCaptor.forClass(OperationalContextAssistanceAiInput.class);
        verify(promptService).prepare(input.capture());
        assertThat(input.getValue().targetContext().path("payload").toString())
                .contains("order-intake").doesNotContain("legacyNote", "preserve-only");
    }

    @Test
    void previewsAndPublishesAllSelectedEntitiesWithOneCatalogDigest() {
        var jobId = completedCreateJob(List.of(
                proposal("system", "order-intake", false),
                proposal("integration", "order-to-crm", false)));
        when(maintenanceService.previewAcceptedBatch(any())).thenReturn(
                new OperationalContextCatalogBatchMutationPreview(
                        List.of(), "digest-1", "digest-2", List.of()));
        when(maintenanceService.applyAcceptedBatch(any())).thenReturn(
                new OperationalContextCatalogBatchMutationResult(List.of(), "digest-2"));
        var request = new OperationalContextAssistanceBatchReviewRequest(List.of(
                new OperationalContextAssistanceProposalDecisionRequest(
                        OperationalContextAssistanceProposalDecisionRequest.Action.APPLY,
                        List.of("name"), List.of()),
                new OperationalContextAssistanceProposalDecisionRequest(
                        OperationalContextAssistanceProposalDecisionRequest.Action.APPLY,
                        List.of("name"), List.of())
        ), "digest-2");

        var preview = service.previewBatch(jobId, request);
        assertThat(preview.valid()).isTrue();
        var decided = service.applyBatch(jobId, request);
        var savedDecision = ArgumentCaptor.forClass(pl.mkn.tdw.features.operationalcontextassistance.api
                .OperationalContextAssistanceJobSnapshot.class);
        verify(localRunPersistence, atLeast(1)).persistRunSnapshot(savedDecision.capture(), any(), any());
        assertThat(savedDecision.getAllValues()).anySatisfy(snapshot ->
                assertThat(snapshot.proposalDecisions()).hasSize(2));
        assertThat(decided.proposalDecisions()).hasSize(2);
        assertThat(decided.proposalDecisions()).allSatisfy(decision ->
                assertThat(decision.catalogDigest()).isEqualTo("digest-2"));
        var command = ArgumentCaptor.forClass(OperationalContextCatalogConditionalBatchCommand.class);
        verify(maintenanceService).applyAcceptedBatch(command.capture());
        assertThat(command.getValue().expectedDigest()).isEqualTo("digest-1");
        assertThat(command.getValue().mutations()).extracting(
                OperationalContextCatalogConditionalBatchCommand.Mutation::type)
                .containsExactly("system", "integration");
        verify(maintenanceService, never()).applyAcceptedChanges(any());
    }

    @Test
    void previewsAndStoresOperatorCorrectedValueInsteadOfAiValue() {
        var jobId = completedCreateJob(List.of(proposal("system", "order-intake", false)));
        when(maintenanceService.previewAcceptedBatch(any())).thenReturn(
                new OperationalContextCatalogBatchMutationPreview(List.of(), "digest-1", "edited-digest", List.of()));
        when(maintenanceService.applyAcceptedBatch(any())).thenReturn(
                new OperationalContextCatalogBatchMutationResult(List.of(), "edited-digest"));
        var corrected = objectMapper.valueToTree("Operator name");
        var choice = new OperationalContextAssistanceProposalDecisionRequest(
                OperationalContextAssistanceProposalDecisionRequest.Action.APPLY,
                List.of("name"), List.of("name"), Map.of("name", corrected));
        var request = new OperationalContextAssistanceBatchReviewRequest(List.of(choice), "edited-digest");

        assertThat(service.previewBatch(jobId, request).valid()).isTrue();
        var saved = service.applyBatch(jobId, request);

        var command = ArgumentCaptor.forClass(OperationalContextCatalogConditionalBatchCommand.class);
        verify(maintenanceService).applyAcceptedBatch(command.capture());
        assertThat(command.getValue().mutations().get(0).changes().get(0).after()).isEqualTo("Operator name");
        assertThat(saved.draft().proposals().get(0).changes().get(0).after()).isEqualTo("Order Intake");
        assertThat(saved.proposalDecisions().get(0).editedValues()).containsEntry("name", corrected);
    }

    @Test
    void savesUnfinishedReviewWithoutWritingCatalogAndRejectsInvalidPaths() {
        var jobId = completedCreateJob(List.of(crmProposal()));
        var review = new OperationalContextAssistanceReviewDraft(List.of(
                new OperationalContextAssistanceReviewDraft.Selection(
                        List.of("name"), List.of(), Map.of("name", objectMapper.valueToTree("CRM Customer API"))
                )));

        var saved = service.saveReview(jobId, review);
        assertThat(saved.reviewDraft()).isEqualTo(review);
        assertThat(saved.proposalDecisions()).isEmpty();
        verify(maintenanceService, never()).applyAcceptedBatch(any());

        assertThatThrownBy(() -> service.saveReview(jobId, new OperationalContextAssistanceReviewDraft(List.of(
                new OperationalContextAssistanceReviewDraft.Selection(
                        List.of("unknown"), List.of(), Map.of())))))
                .isInstanceOf(OperationalContextAssistanceDecisionException.class);
        assertThatThrownBy(() -> service.saveReview(jobId, new OperationalContextAssistanceReviewDraft(List.of(
                new OperationalContextAssistanceReviewDraft.Selection(
                        List.of("name"), List.of(), Map.of("name", objectMapper.createArrayNode()))))))
                .isInstanceOf(OperationalContextAssistanceDecisionException.class);
        assertThat(service.getJob(jobId).reviewDraft()).isEqualTo(review);
    }

    @Test
    void resumesUnresolvedReviewInAnotherJobServiceAndRequiresFreshPreview() {
        var saved = new java.util.concurrent.atomic.AtomicReference<OperationalContextAssistanceStoredRun>();
        var persistence = new OperationalContextAssistanceLocalRunPersistence() {
            @Override public void persistRunSnapshot(
                    pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobSnapshot snapshot,
                    OperationalContextAssistanceJobStartRequest request, Set<String> scopes) {
                saved.set(new OperationalContextAssistanceStoredRun(snapshot, scopes));
            }

            @Override public Optional<OperationalContextAssistanceStoredRun> findRestorable(String jobId) {
                return Optional.ofNullable(saved.get()).filter(run -> run.snapshot().jobId().equals(jobId));
            }
        };
        service = new OperationalContextAssistanceJobService(
                catalogPort, catalogMaterialService, maintenanceService, validationService, sourceCollector,
                promptService, copilotProvider, parser, preflight, objectMapper, directExecutor, authResolver, persistence);
        var jobId = completedCreateJob(List.of(crmProposal()));
        var review = new OperationalContextAssistanceReviewDraft(List.of(
                new OperationalContextAssistanceReviewDraft.Selection(
                        List.of("name"), List.of("name"), Map.of("name", objectMapper.valueToTree("CRM Customer API"))
                )));
        service.saveReview(jobId, review);

        var reopened = new OperationalContextAssistanceJobService(
                catalogPort, catalogMaterialService, maintenanceService, validationService, sourceCollector,
                promptService, copilotProvider, parser, preflight, objectMapper, directExecutor, authResolver, persistence);
        assertThat(reopened.getJob(jobId).reviewDraft()).isEqualTo(review);
        when(maintenanceService.previewAcceptedBatch(any())).thenReturn(
                new OperationalContextCatalogBatchMutationPreview(List.of(), "digest-1", "digest-2", List.of()));
        var choice = new OperationalContextAssistanceBatchReviewRequest(List.of(
                new OperationalContextAssistanceProposalDecisionRequest(
                        OperationalContextAssistanceProposalDecisionRequest.Action.APPLY,
                        List.of("name"), List.of("name"), Map.of("name", objectMapper.valueToTree("CRM Customer API")))) , null);
        assertThat(reopened.previewBatch(jobId, choice).candidateDigest()).isEqualTo("digest-2");
        when(maintenanceService.applyAcceptedBatch(any())).thenReturn(
                new OperationalContextCatalogBatchMutationResult(List.of(), "digest-2"));
        var decided = reopened.applyBatch(jobId,
                new OperationalContextAssistanceBatchReviewRequest(choice.decisions(), "digest-2"));
        assertThat(decided.proposalDecisions()).hasSize(1);
        assertThatThrownBy(() -> reopened.saveReview(jobId, review))
                .isInstanceOf(OperationalContextAssistanceDecisionException.class);
    }

    @Test
    void rejectsUnconfirmedUnselectedAndWrongTypeOperatorEdits() {
        var jobId = completedCreateJob(List.of(proposal("system", "order-intake", false)));
        var corrected = objectMapper.valueToTree("Operator name");

        assertThatThrownBy(() -> service.previewBatch(jobId, new OperationalContextAssistanceBatchReviewRequest(
                List.of(new OperationalContextAssistanceProposalDecisionRequest(
                        OperationalContextAssistanceProposalDecisionRequest.Action.APPLY,
                        List.of("name"), List.of(), Map.of("name", corrected))), null)))
                .isInstanceOf(OperationalContextAssistanceDecisionException.class)
                .hasMessageContaining("potwierdź");
        assertThatThrownBy(() -> service.previewBatch(jobId, new OperationalContextAssistanceBatchReviewRequest(
                List.of(new OperationalContextAssistanceProposalDecisionRequest(
                        OperationalContextAssistanceProposalDecisionRequest.Action.APPLY,
                        List.of("name"), List.of("name"), Map.of("shortName", corrected))), null)))
                .isInstanceOf(OperationalContextAssistanceDecisionException.class)
                .hasMessageContaining("tylko wybrane");
        assertThatThrownBy(() -> service.previewBatch(jobId, new OperationalContextAssistanceBatchReviewRequest(
                List.of(new OperationalContextAssistanceProposalDecisionRequest(
                        OperationalContextAssistanceProposalDecisionRequest.Action.APPLY,
                        List.of("name"), List.of("name"), Map.of("name", objectMapper.createArrayNode()))), null)))
                .isInstanceOf(OperationalContextAssistanceDecisionException.class)
                .hasMessageContaining("zachować typ");
        verify(maintenanceService, never()).applyAcceptedBatch(any());
    }

    @Test
    void batchDecisionRejectsChangedSelectionAndKeepsAllProposalsUndecided() {
        var jobId = completedCreateJob(List.of(proposal("system", "order-intake", true)));
        when(maintenanceService.previewAcceptedBatch(any())).thenReturn(
                new OperationalContextCatalogBatchMutationPreview(
                        List.of(), "digest-1", "digest-2", List.of()));
        var request = new OperationalContextAssistanceBatchReviewRequest(List.of(
                new OperationalContextAssistanceProposalDecisionRequest(
                        OperationalContextAssistanceProposalDecisionRequest.Action.APPLY,
                        List.of("name"), List.of("name"))
        ), "different-preview");

        assertThatThrownBy(() -> service.applyBatch(jobId, request))
                .isInstanceOf(OperationalContextAssistanceDecisionException.class)
                .hasMessageContaining("różni się");
        assertThat(service.getJob(jobId).proposalDecisions()).isEmpty();
        verify(maintenanceService, never()).applyAcceptedBatch(any());
    }

    @Test
    void batchReviewCannotSaveOnboardedRepositoryWithoutSelectedSystemScopeRepositories() {
        var jobId = completedExistingSystemOnboardingJob();
        var skipScope = new OperationalContextAssistanceBatchReviewRequest(List.of(
                new OperationalContextAssistanceProposalDecisionRequest(
                        OperationalContextAssistanceProposalDecisionRequest.Action.APPLY,
                        List.of("name"), List.of()),
                new OperationalContextAssistanceProposalDecisionRequest(
                        OperationalContextAssistanceProposalDecisionRequest.Action.SKIP,
                        List.of(), List.of())
        ), null);
        assertThatThrownBy(() -> service.previewBatch(jobId, skipScope))
                .isInstanceOf(OperationalContextAssistanceDecisionException.class)
                .hasMessageContaining("równoczesnego wybrania pola repositories");

        var omitRepositories = new OperationalContextAssistanceBatchReviewRequest(List.of(
                new OperationalContextAssistanceProposalDecisionRequest(
                        OperationalContextAssistanceProposalDecisionRequest.Action.APPLY,
                        List.of("name"), List.of()),
                new OperationalContextAssistanceProposalDecisionRequest(
                        OperationalContextAssistanceProposalDecisionRequest.Action.APPLY,
                        List.of("name"), List.of())
        ), "digest-2");
        assertThatThrownBy(() -> service.applyBatch(jobId, omitRepositories))
                .isInstanceOf(OperationalContextAssistanceDecisionException.class)
                .hasMessageContaining("równoczesnego wybrania pola repositories");
        assertThat(service.getJob(jobId).proposalDecisions()).isEmpty();
        verify(maintenanceService, never()).previewAcceptedBatch(any());
        verify(maintenanceService, never()).applyAcceptedBatch(any());
    }

    @Test
    void batchReviewAcceptsRepositoryWithSelectedSystemScopeUpdate() {
        var jobId = completedExistingSystemOnboardingJob();
        when(maintenanceService.previewAcceptedBatch(any())).thenReturn(
                new OperationalContextCatalogBatchMutationPreview(List.of(), "digest-1", "digest-2", List.of()));
        var request = new OperationalContextAssistanceBatchReviewRequest(List.of(
                new OperationalContextAssistanceProposalDecisionRequest(
                        OperationalContextAssistanceProposalDecisionRequest.Action.APPLY,
                        List.of("name"), List.of()),
                new OperationalContextAssistanceProposalDecisionRequest(
                        OperationalContextAssistanceProposalDecisionRequest.Action.APPLY,
                        List.of("repositories"), List.of())
        ), null);

        assertThat(service.previewBatch(jobId, request).valid()).isTrue();
        verify(maintenanceService).previewAcceptedBatch(any());
    }

    private String completedExistingSystemOnboardingJob() {
        var catalog = catalog(List.of(system("system-a")), List.of(scope("scope-a", "system-a")));
        when(catalogPort.currentSnapshot()).thenReturn(new OperationalContextSnapshot("digest-1", "local", catalog));
        when(catalogMaterialService.capture()).thenReturn(new OperationalContextAssistanceCatalogMaterial(
                "digest-1", catalog, Map.of(), Map.of()));
        var beforeRepositories = List.of(Map.of("repoId", "existing-repo", "role", "primary", "priority", 1));
        var afterRepositories = List.of(beforeRepositories.get(0),
                Map.of("repoId", "new-repo", "role", "supporting", "priority", 2));
        when(maintenanceService.writablePayloadForUpdate("code-search-scope", "scope-a"))
                .thenReturn(Map.of("repositories", beforeRepositories, "name", "System A code"));
        var commit = "a".repeat(40);
        when(sourceCollector.collect("new-repo", "main")).thenReturn(new OperationalContextGitLabSourceSnapshot(
                "new-repo",
                new OperationalContextGitLabSourceSnapshot.RepositoryGit(
                        "gitlab", "group", "new-repo", "group/new-repo",
                        "https://gitlab.example.com/group/new-repo"),
                "main", commit,
                List.of(new OperationalContextGitLabSourceFile(
                        "README.md", "Repository documentation",
                        "gitlab:group/new-repo@" + commit + ":README.md")), List.of()
        ));
        when(promptService.prepare(any())).thenReturn(new OperationalContextAssistancePromptPreparation(
                "sanitized prompt", Map.of("input.json", "{\"visibilityLimits\":[]}"),
                Set.of("operator:description", "gitlab:group/new-repo@" + commit + ":README.md")
        ));
        when(copilotProvider.execute(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new OperationalContextAssistanceCopilotResult(
                        new CopilotExecutionResult("{draft}", null), Set.of()));
        var repo = proposal("repository", "new-repo", false);
        var scopeUpdate = new OperationalContextAssistanceDraft.Proposal(
                OperationalContextAssistanceDraft.Operation.UPDATE, "code-search-scope", "scope-a",
                List.of(new OperationalContextAssistanceDraft.FieldChange(
                                "repositories", beforeRepositories, afterRepositories,
                                "System wskazany przez operatora.",
                                OperationalContextAssistanceDraft.Basis.USER_STATEMENT,
                                List.of("operator:description"),
                                OperationalContextAssistanceDraft.Confidence.MEDIUM, false),
                        new OperationalContextAssistanceDraft.FieldChange(
                                "name", "System A code", "Updated scope",
                                "Nazwa z opisu operatora.",
                                OperationalContextAssistanceDraft.Basis.USER_STATEMENT,
                                List.of("operator:description"),
                                OperationalContextAssistanceDraft.Confidence.MEDIUM, false)),
                OperationalContextAssistanceDraft.Confidence.MEDIUM, false, List.of());
        when(parser.parse(anyString(), any())).thenReturn(new OperationalContextAssistanceDraft(
                List.of(repo, scopeUpdate), List.of()));
        org.mockito.Mockito.doAnswer(invocation -> {
            var command = invocation.getArgument(0, pl.mkn.tdw.integrations.operationalcontext
                    .OperationalContextCatalogMutationCommand.class);
            return new OperationalContextCatalogMutationPreview(
                    command.type(), command.id(), "digest-1", command.payload(), List.of());
        }).when(maintenanceService).previewCreate(any());
        org.mockito.Mockito.doAnswer(invocation -> {
            var command = invocation.getArgument(0, pl.mkn.tdw.integrations.operationalcontext
                    .OperationalContextCatalogMutationCommand.class);
            return new OperationalContextCatalogMutationPreview(
                    command.type(), command.id(), "digest-1", command.payload(), List.of());
        }).when(maintenanceService).previewUpdate(any());
        var facts = new OperationalContextAssistanceRepositoryFacts(
                OperationalContextAssistanceRepositoryFacts.Usage.EXISTING_SYSTEM,
                null, null, List.of("system-a"));
        var accepted = service.startJob(new OperationalContextAssistanceJobStartRequest(
                OperationalContextAssistanceMode.CREATE_AREA, "Nowy kod systemu", null,
                new OperationalContextAssistanceJobStartRequest.GitLabSource("new-repo", null, "main"),
                facts, null, null));
        assertThat(service.getJob(accepted.jobId()).status()).isEqualTo(OperationalContextAssistanceJobStatus.COMPLETED);
        return accepted.jobId();
    }

    private String completedCreateJob(List<OperationalContextAssistanceDraft.Proposal> proposals) {
        when(copilotProvider.execute(anyString(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new OperationalContextAssistanceCopilotResult(
                        new CopilotExecutionResult("{draft}", null), Set.of()));
        when(parser.parse(anyString(), any())).thenReturn(new OperationalContextAssistanceDraft(
                proposals, List.of()
        ));
        org.mockito.Mockito.doAnswer(invocation -> {
            var command = invocation.getArgument(0, pl.mkn.tdw.integrations.operationalcontext
                    .OperationalContextCatalogMutationCommand.class);
            return new OperationalContextCatalogMutationPreview(
                    command.type(), command.id(), "digest-1", command.payload(), List.of()
            );
        }).when(maintenanceService).previewCreate(any());
        var accepted = service.startJob(new OperationalContextAssistanceJobStartRequest(
                OperationalContextAssistanceMode.CREATE_AREA, "Opis obszaru", null, null, null, null, null
        ));
        assertThat(service.getJob(accepted.jobId()).status()).isEqualTo(OperationalContextAssistanceJobStatus.COMPLETED);
        return accepted.jobId();
    }

    private OperationalContextCatalog catalog(
            List<OperationalContextSystem> systems,
            List<OperationalContextRepositorySearchScope> scopes
    ) {
        return new OperationalContextCatalog(
                List.of(), List.of(), systems, List.of(), List.of(), scopes,
                List.of(), List.of(), List.of(), List.of()
        );
    }

    private OperationalContextSystem system(String id) {
        return objectMapper.convertValue(Map.of("id", id, "name", id), OperationalContextSystem.class);
    }

    private OperationalContextRepositorySearchScope scope(String id, String systemId) {
        return objectMapper.convertValue(Map.of(
                "id", id, "scopeType", "system",
                "target", Map.of("type", "system", "id", systemId)
        ), OperationalContextRepositorySearchScope.class);
    }

    private OperationalContextAssistanceDraft.Proposal proposal(String type, String id, boolean confirmation) {
        return new OperationalContextAssistanceDraft.Proposal(
                OperationalContextAssistanceDraft.Operation.CREATE, type, id,
                List.of(new OperationalContextAssistanceDraft.FieldChange(
                        "name", null, "Order Intake", "Nazwa podana przez operatora.",
                        OperationalContextAssistanceDraft.Basis.USER_STATEMENT,
                        List.of("operator:description"),
                        OperationalContextAssistanceDraft.Confidence.HIGH, confirmation
                )), OperationalContextAssistanceDraft.Confidence.HIGH, false, List.of()
        );
    }

    private OperationalContextAssistanceDraft.Proposal crmProposal() {
        return new OperationalContextAssistanceDraft.Proposal(
                OperationalContextAssistanceDraft.Operation.CREATE, "system", "crm-customer-api",
                List.of(new OperationalContextAssistanceDraft.FieldChange(
                        "name", null, "CRM API", "Nazwa podana przez operatora.",
                        OperationalContextAssistanceDraft.Basis.USER_STATEMENT,
                        List.of("operator:description"),
                        OperationalContextAssistanceDraft.Confidence.HIGH, false
                )), OperationalContextAssistanceDraft.Confidence.HIGH, false, List.of());
    }
}
