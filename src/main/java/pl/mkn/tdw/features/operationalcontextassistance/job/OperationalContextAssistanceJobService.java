package pl.mkn.tdw.features.operationalcontextassistance.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceAiInput;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceCatalogMaterialService;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceCopilotProvider;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceMode;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistancePromptPreparationService;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceRepositoryFacts;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobSnapshot;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceBatchPreview;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceBatchReviewRequest;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobStartRequest;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceProposalDecision;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceProposalDecisionRequest;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceProposalPreview;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceSourceRevision;
import pl.mkn.tdw.features.operationalcontextassistance.draft.OperationalContextAssistanceDraft;
import pl.mkn.tdw.features.operationalcontextassistance.draft.OperationalContextAssistanceDraftParser;
import pl.mkn.tdw.features.operationalcontextassistance.draft.OperationalContextAssistanceDraftScope;
import pl.mkn.tdw.features.operationalcontextassistance.job.localworkspace.OperationalContextAssistanceLocalRunPersistence;
import pl.mkn.tdw.features.operationalcontextassistance.source.OperationalContextGitLabSourceCollector;
import pl.mkn.tdw.features.operationalcontextassistance.source.OperationalContextGitLabSourceSnapshot;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogFieldError;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogMaintenanceException;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogMaintenanceService;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogConditionalMutationCommand;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogConditionalBatchCommand;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogMutationCommand;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogPreviewViolation;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogValidationService;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextEntry;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextRepositorySearchScope;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextPort;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextSnapshot;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRefResolver;
import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

import java.util.ArrayList;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceProposalPreview.ValidationStatus;
import static pl.mkn.tdw.features.operationalcontextassistance.draft.OperationalContextAssistanceDraft.Operation;

@Service
@Slf4j
@RequiredArgsConstructor
public class OperationalContextAssistanceJobService {

    private static final int MAX_SELECTED_SCOPES_BYTES = 32_000;
    private static final int MAX_EDITED_VALUE_BYTES = 16_384;
    private static final int MAX_EDITED_VALUES_BYTES = 65_536;

    private final Map<String, OperationalContextAssistanceJobState> jobs = new ConcurrentHashMap<>();
    private final Map<String, OperationalContextAssistanceJobStartRequest> requests = new ConcurrentHashMap<>();
    private final OperationalContextPort operationalContextPort;
    private final OperationalContextAssistanceCatalogMaterialService catalogMaterialService;
    private final OperationalContextCatalogMaintenanceService maintenanceService;
    private final OperationalContextCatalogValidationService validationService;
    private final OperationalContextGitLabSourceCollector sourceCollector;
    private final OperationalContextAssistancePromptPreparationService promptPreparationService;
    private final OperationalContextAssistanceCopilotProvider copilotProvider;
    private final OperationalContextAssistanceDraftParser draftParser;
    private final ObjectMapper objectMapper;
    private final TaskExecutor applicationTaskExecutor;
    private final AnalysisAiAuthRefResolver authRefResolver;
    private final OperationalContextAssistanceLocalRunPersistence localRunPersistence;

    public OperationalContextAssistanceJobSnapshot startJob(OperationalContextAssistanceJobStartRequest request) {
        if (request.gitLabSource() != null && request.gitLabSource().projectUrl() != null) {
            sourceCollector.validateProjectUrl(request.gitLabSource().projectUrl());
        }
        var authRef = authRefResolver.resolveForCurrentRequest();
        var jobId = UUID.randomUUID().toString();
        var state = new OperationalContextAssistanceJobState(jobId);
        jobs.put(jobId, state);
        requests.put(jobId, request);
        var accepted = state.snapshot();
        persistSnapshot(accepted, request);
        try {
            applicationTaskExecutor.execute(() -> runJob(jobId, state, request, authRef));
        } catch (RuntimeException exception) {
            state.failed("OPCTX_ASSISTANCE_SCHEDULING_FAILED", "Nie udało się uruchomić asysty AI.");
            log.error("Operational Context assistance scheduling failed jobId={}", jobId, exception);
            var failed = state.snapshot();
            persistSnapshot(failed, request);
            return failed;
        }
        return accepted;
    }

    public OperationalContextAssistanceJobSnapshot getJob(String jobId) {
        var state = jobs.get(jobId);
        if (state == null) {
            throw new OperationalContextAssistanceJobNotFoundException(jobId);
        }
        return state.snapshot();
    }

    public OperationalContextAssistanceBatchPreview previewBatch(
            String jobId, OperationalContextAssistanceBatchReviewRequest request
    ) {
        var state = requireJob(jobId);
        synchronized (state) {
            var current = reviewableSnapshot(state);
            var command = batchCommand(current, request, state.requiredRepositoryScopeIds());
            if (command.mutations().isEmpty()) {
                ensureUnchangedCatalog(command.expectedDigest());
                return new OperationalContextAssistanceBatchPreview(
                        command.expectedDigest(), command.expectedDigest(), true, List.of(), List.of());
            }
            var assessment = maintenanceService.previewAcceptedBatch(command);
            return new OperationalContextAssistanceBatchPreview(
                    assessment.expectedDigest(), assessment.candidateDigest(), assessment.valid(),
                    assessment.entities(), assessment.violations());
        }
    }

    public OperationalContextAssistanceJobSnapshot applyBatch(
            String jobId, OperationalContextAssistanceBatchReviewRequest request
    ) {
        var state = requireJob(jobId);
        synchronized (state) {
            var current = reviewableSnapshot(state);
            if (request == null || request.candidateDigest() == null || request.candidateDigest().isBlank()) {
                throw decisionError("OPCTX_ASSISTANCE_PREVIEW_REQUIRED", UserFacingErrorType.BAD_REQUEST,
                        "Najpierw sprawdź podgląd całego wybranego zestawu zmian.");
            }
            var command = batchCommand(current, request, state.requiredRepositoryScopeIds());
            if (command.mutations().isEmpty()) {
                ensureUnchangedCatalog(command.expectedDigest());
                if (!request.candidateDigest().equals(command.expectedDigest())) {
                    throw decisionError("OPCTX_ASSISTANCE_PREVIEW_STALE", UserFacingErrorType.CONFLICT,
                            "Podgląd całego zestawu jest nieaktualny.");
                }
            } else {
                var assessment = maintenanceService.previewAcceptedBatch(command);
                if (!assessment.valid()) {
                    throw decisionError("OPCTX_ASSISTANCE_BATCH_INVALID", UserFacingErrorType.UNPROCESSABLE_ENTITY,
                            "Wybrane zmiany nie tworzą poprawnego katalogu. Sprawdź walidację całego zestawu.");
                }
                if (!request.candidateDigest().equals(assessment.candidateDigest())) {
                    throw decisionError("OPCTX_ASSISTANCE_PREVIEW_STALE", UserFacingErrorType.CONFLICT,
                            "Wybór pól różni się od sprawdzonego podglądu. Sprawdź zestaw ponownie.");
                }
            }
            var resultDigest = command.mutations().isEmpty()
                    ? command.expectedDigest()
                    : maintenanceService.applyAcceptedBatch(command).contentDigest();
            for (var index = 0; index < request.decisions().size(); index++) {
                var choice = request.decisions().get(index);
                state.recordDecision(new OperationalContextAssistanceProposalDecision(
                        index, choice.action(), choice.selectedPaths(), choice.editedValues(), Instant.now(),
                        choice.action() == OperationalContextAssistanceProposalDecisionRequest.Action.APPLY
                                ? resultDigest : null));
            }
            var decided = state.snapshot();
            persistSnapshot(decided, requests.get(jobId));
            return decided;
        }
    }

    private OperationalContextAssistanceJobState requireJob(String jobId) {
        var state = jobs.get(jobId);
        if (state == null) {
            throw new OperationalContextAssistanceJobNotFoundException(jobId);
        }
        return state;
    }

    private OperationalContextAssistanceJobSnapshot reviewableSnapshot(OperationalContextAssistanceJobState state) {
        var current = state.snapshot();
        if (current.status() != pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobStatus.COMPLETED
                && current.status() != pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobStatus.PARTIAL) {
            throw decisionError("OPCTX_ASSISTANCE_NOT_READY", UserFacingErrorType.CONFLICT,
                    "Propozycje są dostępne dopiero po zakończeniu asysty.");
        }
        if (current.draft() == null || current.draft().proposals().isEmpty()) {
            throw decisionError("OPCTX_ASSISTANCE_NO_PROPOSALS", UserFacingErrorType.BAD_REQUEST,
                    "Ten job nie zawiera propozycji do zapisania.");
        }
        if (!current.proposalDecisions().isEmpty()) {
            throw decisionError("OPCTX_ASSISTANCE_ALREADY_DECIDED", UserFacingErrorType.CONFLICT,
                    "Zestaw propozycji został już rozstrzygnięty.");
        }
        return current;
    }

    private OperationalContextCatalogConditionalBatchCommand batchCommand(
            OperationalContextAssistanceJobSnapshot current,
            OperationalContextAssistanceBatchReviewRequest request,
            Set<String> requiredRepositoryScopeIds
    ) {
        if (request == null || request.decisions() == null
                || request.decisions().size() != current.draft().proposals().size()) {
            throw decisionError("OPCTX_ASSISTANCE_INVALID_SELECTION", UserFacingErrorType.BAD_REQUEST,
                    "Wybierz decyzję dla każdej propozycji w zestawie.");
        }
        var mutations = new ArrayList<OperationalContextCatalogConditionalBatchCommand.Mutation>();
        var selectedRepositoryCreate = false;
        var selectedRepositoryScopeIds = new LinkedHashSet<String>();
        for (var index = 0; index < request.decisions().size(); index++) {
            var choice = request.decisions().get(index);
            if (choice == null || choice.action() == null) {
                throw decisionError("OPCTX_ASSISTANCE_INVALID_DECISION", UserFacingErrorType.BAD_REQUEST,
                        "Decyzja operatora jest wymagana dla każdej propozycji.");
            }
            if (choice.action() == OperationalContextAssistanceProposalDecisionRequest.Action.SKIP) {
                if (!choice.selectedPaths().isEmpty() || !choice.confirmedPaths().isEmpty()
                        || !choice.editedValues().isEmpty()) {
                    throw decisionError("OPCTX_ASSISTANCE_INVALID_DECISION", UserFacingErrorType.BAD_REQUEST,
                            "Pominięcie propozycji nie przyjmuje pól, poprawek ani potwierdzeń.");
                }
                continue;
            }
            var proposal = current.draft().proposals().get(index);
            var selected = selectedChanges(proposal, choice);
            if (proposal.operation() == Operation.CREATE && "repository".equals(proposal.entityType())) {
                selectedRepositoryCreate = true;
            }
            if (proposal.operation() == Operation.UPDATE && "code-search-scope".equals(proposal.entityType())
                    && selected.stream().anyMatch(change -> "repositories".equals(change.path()))) {
                selectedRepositoryScopeIds.add(proposal.entityId());
            }
            mutations.add(new OperationalContextCatalogConditionalBatchCommand.Mutation(
                    proposal.entityType(), proposal.entityId(),
                    proposal.operation() == Operation.CREATE
                            ? OperationalContextCatalogConditionalMutationCommand.Operation.CREATE
                            : OperationalContextCatalogConditionalMutationCommand.Operation.UPDATE,
                    selected.stream().map(change -> new OperationalContextCatalogConditionalMutationCommand.FieldChange(
                            change.path(), change.before(), change.after())).toList()
            ));
        }
        if (selectedRepositoryCreate && !selectedRepositoryScopeIds.containsAll(requiredRepositoryScopeIds)) {
            throw decisionError("OPCTX_ASSISTANCE_REPOSITORY_SCOPE_REQUIRED", UserFacingErrorType.BAD_REQUEST,
                    "Zapis repozytorium wymaga równoczesnego wybrania pola repositories we wszystkich zakresach kodu wskazanych systemów.");
        }
        return new OperationalContextCatalogConditionalBatchCommand(current.catalogDigest(), mutations);
    }

    private void ensureUnchangedCatalog(String expectedDigest) {
        if (!Objects.equals(expectedDigest, operationalContextPort.currentSnapshot().contentDigest())) {
            throw decisionError("OPCTX_ASSISTANCE_CATALOG_STALE", UserFacingErrorType.CONFLICT,
                    "Katalog zmienił się od przygotowania propozycji. Uruchom asystę ponownie.");
        }
    }

    private List<OperationalContextAssistanceDraft.FieldChange> selectedChanges(
            OperationalContextAssistanceDraft.Proposal proposal,
            OperationalContextAssistanceProposalDecisionRequest request
    ) {
        var paths = request.selectedPaths();
        var unique = new LinkedHashSet<>(paths);
        if (paths.isEmpty() || unique.size() != paths.size() || unique.contains(null)) {
            throw decisionError("OPCTX_ASSISTANCE_INVALID_SELECTION", UserFacingErrorType.BAD_REQUEST,
                    "Wybierz co najmniej jedno unikalne pole propozycji.");
        }
        var confirmed = new LinkedHashSet<>(request.confirmedPaths());
        if (confirmed.size() != request.confirmedPaths().size() || !unique.containsAll(confirmed)) {
            throw decisionError("OPCTX_ASSISTANCE_INVALID_CONFIRMATION", UserFacingErrorType.BAD_REQUEST,
                    "Potwierdzenia muszą dotyczyć tylko wybranych pól.");
        }
        var selected = proposal.changes().stream().filter(change -> unique.contains(change.path())).toList();
        if (selected.size() != unique.size()) {
            throw decisionError("OPCTX_ASSISTANCE_INVALID_SELECTION", UserFacingErrorType.BAD_REQUEST,
                    "Wybór zawiera pole spoza propozycji AI.");
        }
        var edits = request.editedValues();
        if (!unique.containsAll(edits.keySet()) || edits.containsKey(null)) {
            throw decisionError("OPCTX_ASSISTANCE_INVALID_EDIT", UserFacingErrorType.BAD_REQUEST,
                    "Poprawiać można tylko wybrane pola propozycji AI.");
        }
        if (selected.stream().anyMatch(change ->
                (proposal.requiresConfirmation() || change.requiresConfirmation() || edits.containsKey(change.path()))
                        && !confirmed.contains(change.path()))) {
            throw decisionError("OPCTX_ASSISTANCE_CONFIRMATION_REQUIRED", UserFacingErrorType.BAD_REQUEST,
                    "Jawnie potwierdź każde wybrane pole wymagające decyzji operatora.");
        }
        var editedBytes = 0;
        var corrected = new ArrayList<OperationalContextAssistanceDraft.FieldChange>(selected.size());
        for (var change : selected) {
            var node = edits.get(change.path());
            if (!edits.containsKey(change.path())) {
                corrected.add(change);
                continue;
            }
            if ("repository".equals(proposal.entityType()) && "git".equals(change.path())
                    || "code-search-scope".equals(proposal.entityType()) && "repositories".equals(change.path())) {
                throw decisionError("OPCTX_ASSISTANCE_INVALID_EDIT", UserFacingErrorType.BAD_REQUEST,
                        "Tożsamości źródła i zakresu kodu nie można poprawiać w przeglądzie asysty.");
            }
            if (node == null || node.isNull() || !sameEditableValueKind(change.after(), node)) {
                throw decisionError("OPCTX_ASSISTANCE_INVALID_EDIT", UserFacingErrorType.BAD_REQUEST,
                        "Poprawiona wartość musi zachować typ proponowanego pola i nie może być null.");
            }
            var valueBytes = node.toString().getBytes(StandardCharsets.UTF_8).length;
            editedBytes += valueBytes;
            if (valueBytes > MAX_EDITED_VALUE_BYTES || editedBytes > MAX_EDITED_VALUES_BYTES) {
                throw decisionError("OPCTX_ASSISTANCE_INVALID_EDIT", UserFacingErrorType.BAD_REQUEST,
                        "Poprawione wartości są zbyt duże do przeglądu asysty.");
            }
            var after = objectMapper.convertValue(node, Object.class);
            var fieldErrors = maintenanceService.validatePartialEditablePayload(
                    proposal.entityType(), Map.of(change.path(), after));
            if (!fieldErrors.isEmpty()) {
                throw decisionError("OPCTX_ASSISTANCE_INVALID_EDIT", UserFacingErrorType.UNPROCESSABLE_ENTITY,
                        "Poprawiona wartość pola " + change.path() + " nie jest poprawna: " + fieldErrors.get(0).message());
            }
            corrected.add(new OperationalContextAssistanceDraft.FieldChange(
                    change.path(), change.before(), after, change.reason(), change.basis(),
                    change.sourceRefs(), change.confidence(), change.requiresConfirmation()));
        }
        return corrected;
    }

    private boolean sameEditableValueKind(Object original, JsonNode edited) {
        return original instanceof String && edited.isTextual()
                || original instanceof List<?> && edited.isArray()
                || original instanceof Map<?, ?> && edited.isObject();
    }

    private OperationalContextAssistanceDecisionException decisionError(
            String code, UserFacingErrorType type, String message
    ) {
        return new OperationalContextAssistanceDecisionException(code, type, message);
    }

    void runJob(
            String jobId,
            OperationalContextAssistanceJobState state,
            OperationalContextAssistanceJobStartRequest request,
            AnalysisAiAuthRef authRef
    ) {
        if (!state.start()) {
            return;
        }
        try {
            var catalogMaterial = catalogMaterialService.capture();
            var catalogSnapshot = new OperationalContextSnapshot(
                    catalogMaterial.contentDigest(), "local", catalogMaterial.catalog());
            var limits = new ArrayList<String>();
            var catalogContext = catalogContext(catalogMaterial);
            var selectedScopeIds = appendSelectedSystemScopes(
                    catalogContext, catalogSnapshot.catalog(), request.repositoryFacts()
            );
            state.requiredRepositoryScopeIds(selectedScopeIds);
            var targetContext = targetContext(request, catalogSnapshot);
            if (!Objects.equals(catalogSnapshot.contentDigest(), operationalContextPort.currentSnapshot().contentDigest())) {
                throw new BlockedRun("Katalog zmienił się podczas zbierania kontekstu. Uruchom asystę ponownie.");
            }
            var source = selectedSource(request);
            if (source != null) {
                limits.addAll(source.visibilityLimits());
            }
            var revision = source != null && source.commitId() != null
                    ? new OperationalContextAssistanceSourceRevision(
                            source.project(), source.requestedRef(), source.commitId())
                    : null;
            state.contextCollected(catalogSnapshot.contentDigest(), revision, limits,
                    source != null ? source.files().size() : 0);
            persistSnapshot(state.snapshot(), request);

            var input = new OperationalContextAssistanceAiInput(
                    request.mode(), request.description(), catalogContext, catalogMaterial.guidance(), targetContext, source,
                    request.repositoryFacts(), limits
            );
            var preparation = promptPreparationService.prepare(input);
            var preparedLimits = preparedLimits(preparation.artifacts());
            state.prepared(preparation.prompt(), preparation.allowedSourceRefs().stream().sorted().toList(), preparedLimits);
            persistSnapshot(state.snapshot(), request);

            var copilotResult = copilotProvider.execute(
                    jobId, request.aiOptions(), authRef, preparation, source, state::activity
            );
            var execution = copilotResult.executionResult();
            state.usage(execution.usage());
            var allSourceRefs = new LinkedHashSet<>(preparation.allowedSourceRefs());
            allSourceRefs.addAll(copilotResult.readSourceRefs());
            state.sourceRefs(allSourceRefs.stream().sorted().toList());
            var target = request.target();
            var scope = new OperationalContextAssistanceDraftScope(
                    request.mode(), target != null ? target.entityType() : null,
                    target != null ? target.entityId() : null, allSourceRefs,
                    source != null ? source.repositoryGit() : null,
                    request.repositoryFacts(), selectedScopeIds
            );
            var draft = draftParser.parse(execution.content(), scope);
            if (draft.proposals().isEmpty() && draft.questions().isEmpty()) {
                throw new BlockedRun("AI nie przygotowało propozycji ani pytania do operatora.");
            }

            var selectedCodeRead = scope.hasSelectedSource();
            if (request.gitLabSource() != null && !selectedCodeRead) {
                limits.add("AI nie odczytało żadnego pliku kodu z wybranego projektu GitLab; drzewo ścieżek nie jest dowodem treści.");
            }
            if (draft.proposals().isEmpty()) {
                state.blockedWithDraft("OPCTX_ASSISTANCE_NO_PROPOSALS",
                        request.gitLabSource() != null && !selectedCodeRead
                                ? "Nie przygotowano propozycji, ponieważ nie odczytano kodu. Sprawdź pytania i ograniczenia."
                                : "Nie przygotowano propozycji. Sprawdź pytania do operatora i ograniczenia.",
                        draft, limits);
                return;
            }

            List<OperationalContextAssistanceProposalPreview> previews;
            if (!Objects.equals(catalogSnapshot.contentDigest(), operationalContextPort.currentSnapshot().contentDigest())) {
                limits.add("Katalog zmienił się podczas analizy; ponów asystę przed zapisem propozycji.");
                previews = deferredPreviews(draft);
            } else {
                previews = preview(draft, catalogSnapshot.contentDigest());
                if (!Objects.equals(catalogSnapshot.contentDigest(), operationalContextPort.currentSnapshot().contentDigest())) {
                    limits.add("Katalog zmienił się podczas podglądu propozycji; ponów asystę przed zapisem.");
                    previews = deferredPreviews(draft);
                }
            }
            var partial = request.gitLabSource() != null && source != null && !source.visibilityLimits().isEmpty();
            partial |= request.gitLabSource() != null && !selectedCodeRead;
            partial |= request.repositoryFacts() != null
                    && selectedScopeIds.size() < request.repositoryFacts().systemIds().size();
            partial |= previews.stream().anyMatch(preview -> !preview.valid());
            state.complete(draft, previews, execution.usage(), limits, partial);
        } catch (BlockedRun exception) {
            state.blocked("OPCTX_ASSISTANCE_CONTEXT_BLOCKED", exception.getMessage());
        } catch (UserFacingApplicationException exception) {
            state.blocked(exception.code(), exception.getMessage());
        } catch (OperationalContextCatalogMaintenanceException exception) {
            state.blocked("OPCTX_ASSISTANCE_TARGET_UNAVAILABLE", exception.getMessage());
        } catch (RuntimeException exception) {
            state.failed("OPCTX_ASSISTANCE_FAILED", "Asysta AI nie zakończyła się poprawnym wynikiem.");
            log.error("Operational Context assistance job failed jobId={}", jobId, exception);
        } finally {
            persistSnapshot(state.snapshot(), request);
        }
    }

    private void persistSnapshot(
            OperationalContextAssistanceJobSnapshot snapshot,
            OperationalContextAssistanceJobStartRequest request
    ) {
        try {
            localRunPersistence.persistRunSnapshot(snapshot, request);
        } catch (RuntimeException exception) {
            log.warn("Operational Context assistance history save failed jobId={}", snapshot.jobId(), exception);
        }
    }

    private OperationalContextGitLabSourceSnapshot selectedSource(OperationalContextAssistanceJobStartRequest request) {
        var selected = request.gitLabSource();
        if (selected == null) {
            return null;
        }
        return selected.projectUrl() != null
                ? sourceCollector.collectUrl(selected.projectUrl(), selected.ref())
                : sourceCollector.collect(selected.project(), selected.ref());
    }

    private ObjectNode catalogContext(
            pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceCatalogMaterial material
    ) {
        var root = objectMapper.createObjectNode();
        root.put("contentDigest", material.contentDigest());
        root.set("documents", objectMapper.valueToTree(material.documents()));
        return root;
    }

    private Set<String> appendSelectedSystemScopes(
            ObjectNode context,
            OperationalContextCatalog catalog,
            OperationalContextAssistanceRepositoryFacts facts
    ) {
        if (facts == null || facts.systemIds().isEmpty()) {
            return Set.of();
        }
        if (!facts.isUsageConsistent()) {
            throw new BlockedRun("Dane wybranego zastosowania repozytorium są niepoprawne.");
        }
        var selectedScopes = context.putArray("selectedSystemScopes");
        var authorizedScopeIds = new LinkedHashSet<String>();
        int contextBytes = 0;
        for (var systemId : facts.systemIds()) {
            var system = catalog.systems().stream()
                    .filter(candidate -> systemId.equals(candidate.id()))
                    .findFirst().orElseThrow(() -> new BlockedRun(
                            "Wybrany system nie istnieje już w katalogu: " + systemId
                    ));
            var matches = catalog.codeSearchScopes().stream()
                    .filter(scope -> "system".equals(scope.scopeType())
                            && "system".equals(scope.target().type())
                            && systemId.equals(scope.target().id()))
                    .toList();
            if (matches.size() != 1) {
                throw new BlockedRun("System " + systemId + " ma " + matches.size()
                        + " zakresów wyszukiwania kodu. Utwórz albo napraw code-search-scope tak, aby system miał dokładnie jeden zakres przed podłączeniem repozytorium.");
            }
            OperationalContextRepositorySearchScope scope = matches.get(0);
            Map<String, Object> writable;
            try {
                writable = maintenanceService.writablePayloadForUpdate("code-search-scope", scope.id());
            } catch (RuntimeException exception) {
                throw new BlockedRun("Nie udało się odczytać zakresu wyszukiwania kodu systemu " + systemId
                        + ". Sprawdź wpis przed podłączeniem repozytorium.");
            }
            var repositories = writable.get("repositories");
            if (!(repositories instanceof List<?>)) {
                throw new BlockedRun("Zakres wyszukiwania kodu systemu " + systemId
                        + " nie ma listy repozytoriów możliwej do aktualizacji.");
            }
            var entry = objectMapper.createObjectNode();
            entry.put("systemId", systemId);
            entry.put("systemName", system.name());
            entry.put("scopeId", scope.id());
            entry.set("target", objectMapper.valueToTree(scope.target()));
            entry.set("beforeRepositories", objectMapper.valueToTree(repositories));
            int entryBytes = entry.toString().getBytes(StandardCharsets.UTF_8).length;
            if (contextBytes + entryBytes > MAX_SELECTED_SCOPES_BYTES) {
                throw new BlockedRun("Zakres wyszukiwania kodu systemu " + systemId
                        + " przekracza limit kontekstu AI; nie można bezpiecznie podłączyć repozytorium.");
            }
            selectedScopes.add(entry);
            authorizedScopeIds.add(scope.id());
            contextBytes += entryBytes;
        }
        return Set.copyOf(authorizedScopeIds);
    }

    private JsonNode targetContext(
            OperationalContextAssistanceJobStartRequest request,
            OperationalContextSnapshot snapshot
    ) {
        if (request.mode() == OperationalContextAssistanceMode.CREATE_AREA) {
            return null;
        }
        var target = request.target();
        var entity = maintenanceService.entity(target.entityType(), target.entityId());
        var result = objectMapper.createObjectNode();
        result.put("kind", target.kind().name());
        result.put("entityType", entity.type());
        result.put("entityId", entity.id());
        result.set("payload", objectMapper.valueToTree(
                maintenanceService.writablePayloadForUpdate(target.entityType(), target.entityId())
        ));
        if (target.kind() == OperationalContextAssistanceJobStartRequest.Target.Kind.OPEN_QUESTION) {
            var question = snapshot.catalog().openQuestions().stream()
                    .filter(item -> target.id().equals(item.id())
                            && target.entityType().equals(item.entityType())
                            && target.entityId().equals(item.entityId()))
                    .findFirst().orElseThrow(() -> new BlockedRun("Wskazane otwarte pytanie nie istnieje."));
            result.set("finding", objectMapper.valueToTree(question));
        } else if (target.kind() == OperationalContextAssistanceJobStartRequest.Target.Kind.VALIDATION_FINDING) {
            var finding = validationService.validate(snapshot.catalog()).fingerprintedFindings().stream()
                    .filter(item -> {
                        var firstRef = item.finding().sourceRefs().stream().findFirst().orElse(null);
                        return firstRef != null
                                && target.id().equals(item.fingerprint())
                                && target.entityType().equals(firstRef.entityType())
                                && target.entityId().equals(firstRef.entityId());
                    })
                    .map(OperationalContextCatalogValidationService.FingerprintedFinding::finding)
                    .findFirst().orElseThrow(() -> new BlockedRun("Wskazany finding Validation nie istnieje."));
            result.set("finding", objectMapper.valueToTree(finding));
        }
        return result;
    }

    private List<String> preparedLimits(Map<String, String> artifacts) {
        try {
            var material = objectMapper.readTree(artifacts.values().iterator().next());
            var result = new ArrayList<String>();
            material.path("visibilityLimits").forEach(value -> result.add(value.asText()));
            return List.copyOf(result);
        } catch (Exception exception) {
            throw new IllegalStateException("Prepared assistance material is invalid.", exception);
        }
    }

    private List<OperationalContextAssistanceProposalPreview> preview(
            OperationalContextAssistanceDraft draft,
            String expectedDigest
    ) {
        var result = new ArrayList<OperationalContextAssistanceProposalPreview>();
        var earlierCreates = new LinkedHashSet<String>();
        for (int index = 0; index < draft.proposals().size(); index++) {
            var proposal = draft.proposals().get(index);
            Map<String, Object> candidate;
            try {
                candidate = candidatePayload(proposal);
                var command = new OperationalContextCatalogMutationCommand(
                        proposal.entityType(), proposal.entityId(), candidate
                );
                var assessment = proposal.operation() == Operation.CREATE
                        ? maintenanceService.previewCreate(command) : maintenanceService.previewUpdate(command);
                if (!Objects.equals(expectedDigest, assessment.baseDigest())) {
                    result.add(deferredPreview(index, proposal));
                    if (proposal.operation() == Operation.CREATE) {
                        earlierCreates.add(proposal.entityId());
                    }
                    continue;
                }
                var status = assessment.valid() ? ValidationStatus.VALID
                        : referencesEarlierCreate(candidate, earlierCreates)
                                && assessment.violations().stream().allMatch(this::referenceViolation)
                        ? ValidationStatus.DEFERRED : ValidationStatus.INVALID;
                result.add(new OperationalContextAssistanceProposalPreview(
                        index, status, assessment.valid(), assessment.candidatePayload(),
                        assessment.violations(), List.of()
                ));
            } catch (OperationalContextCatalogMaintenanceException exception) {
                if (!Objects.equals(expectedDigest, operationalContextPort.currentSnapshot().contentDigest())) {
                    result.add(deferredPreview(index, proposal));
                    if (proposal.operation() == Operation.CREATE) {
                        earlierCreates.add(proposal.entityId());
                    }
                    continue;
                }
                candidate = candidateForDisplay(proposal);
                var status = referencesEarlierCreate(candidate, earlierCreates)
                        && !exception.fieldErrors().isEmpty()
                        && exception.fieldErrors().stream().allMatch(error -> referencePointer(error.pointer()))
                        ? ValidationStatus.DEFERRED : ValidationStatus.INVALID;
                result.add(new OperationalContextAssistanceProposalPreview(
                        index, status, false, candidate, List.of(), exception.fieldErrors()
                ));
            } catch (StaleModelValue exception) {
                if (!Objects.equals(expectedDigest, operationalContextPort.currentSnapshot().contentDigest())) {
                    result.add(deferredPreview(index, proposal));
                    if (proposal.operation() == Operation.CREATE) {
                        earlierCreates.add(proposal.entityId());
                    }
                    continue;
                }
                result.add(new OperationalContextAssistanceProposalPreview(
                        index, ValidationStatus.INVALID, false, candidateForDisplay(proposal), List.of(),
                        List.of(new OperationalContextCatalogFieldError(
                                "/payload/" + exception.path, "AI before value differs from current catalog value"
                        ))
                ));
            }
            if (proposal.operation() == Operation.CREATE) {
                earlierCreates.add(proposal.entityId());
            }
        }
        return List.copyOf(result);
    }

    private Map<String, Object> candidatePayload(OperationalContextAssistanceDraft.Proposal proposal) {
        var payload = new LinkedHashMap<String, Object>();
        if (proposal.operation() == Operation.UPDATE) {
            payload.putAll(maintenanceService.writablePayloadForUpdate(proposal.entityType(), proposal.entityId()));
        } else {
            payload.put("id", proposal.entityId());
        }
        for (var change : proposal.changes()) {
            if (proposal.operation() == Operation.UPDATE
                    && !Objects.equals(payload.get(change.path()), change.before())) {
                throw new StaleModelValue(change.path());
            }
            payload.put(change.path(), change.after());
        }
        return payload;
    }

    private Map<String, Object> candidateForDisplay(OperationalContextAssistanceDraft.Proposal proposal) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("id", proposal.entityId());
        proposal.changes().forEach(change -> payload.put(change.path(), change.after()));
        return payload;
    }

    private boolean referencesEarlierCreate(Map<String, Object> candidate, Set<String> earlierIds) {
        return Stream.of("target", "repositories", "references", "relations", "participants")
                .map(candidate::get)
                .anyMatch(value -> containsEarlierId(value, earlierIds));
    }

    private boolean containsEarlierId(Object value, Set<String> earlierIds) {
        if (value instanceof String text) {
            return earlierIds.contains(text);
        }
        if (value instanceof Map<?, ?> map) {
            return map.values().stream().anyMatch(item -> containsEarlierId(item, earlierIds));
        }
        if (value instanceof Iterable<?> iterable) {
            for (var item : iterable) {
                if (containsEarlierId(item, earlierIds)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean referenceViolation(OperationalContextCatalogPreviewViolation violation) {
        var code = violation.ruleCode() != null ? violation.ruleCode().toUpperCase() : "";
        return code.contains("REFERENCE") || code.contains("TARGET")
                || code.contains("REPOSITORY") || code.contains("RELATION")
                || code.contains("SCOPE");
    }

    private boolean referencePointer(String pointer) {
        return pointer != null && Stream.of("/target", "/repositories", "/references", "/relations", "/participants")
                .anyMatch(pointer::contains);
    }

    private List<OperationalContextAssistanceProposalPreview> deferredPreviews(OperationalContextAssistanceDraft draft) {
        var result = new ArrayList<OperationalContextAssistanceProposalPreview>();
        for (int index = 0; index < draft.proposals().size(); index++) {
            result.add(deferredPreview(index, draft.proposals().get(index)));
        }
        return result;
    }

    private OperationalContextAssistanceProposalPreview deferredPreview(
            int index,
            OperationalContextAssistanceDraft.Proposal proposal
    ) {
        return new OperationalContextAssistanceProposalPreview(
                index, ValidationStatus.DEFERRED, false,
                candidateForDisplay(proposal), List.of(),
                List.of(new OperationalContextCatalogFieldError("/", "Catalog changed during analysis"))
        );
    }

    private static final class BlockedRun extends RuntimeException {
        private BlockedRun(String message) { super(message); }
    }

    private static final class StaleModelValue extends RuntimeException {
        private final String path;

        private StaleModelValue(String path) {
            this.path = path;
        }
    }
}
