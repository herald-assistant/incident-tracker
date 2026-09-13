package pl.mkn.tdw.integrations.operationalcontext;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import pl.mkn.tdw.aiplatform.copilot.runtime.execution.CopilotExecutionResult;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceCopilotProvider;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceCopilotResult;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceCatalogMaterialService;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceMode;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistancePromptPreparation;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistancePromptPreparationService;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceRepositoryFacts;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobStartRequest;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceBatchReviewRequest;
import pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceProposalDecisionRequest;
import pl.mkn.tdw.features.operationalcontextassistance.draft.OperationalContextAssistanceDraft;
import pl.mkn.tdw.features.operationalcontextassistance.draft.OperationalContextAssistanceDraftParser;
import pl.mkn.tdw.features.operationalcontextassistance.job.OperationalContextAssistanceJobService;
import pl.mkn.tdw.features.operationalcontextassistance.source.OperationalContextGitLabSourceCollector;
import pl.mkn.tdw.features.operationalcontextassistance.source.OperationalContextGitLabSourceSnapshot;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OperationalContextAssistanceWriteIntegrationTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void savesDependentProposalsTogetherWithOneCatalogDigest() {
        var harness = harness("ordered", proposals());
        var initialDigest = harness.digest();
        var jobId = harness.startJob();

        var preview = harness.preview(jobId, Set.of(0, 1, 2));
        assertThat(preview.valid()).isTrue();
        assertThat(preview.candidateDigest()).isNotEqualTo(initialDigest);
        assertThatThrownBy(() -> harness.maintenance().entity("system", "assisted-system"))
                .isInstanceOf(OperationalContextCatalogMaintenanceException.class);

        var applied = harness.apply(jobId, Set.of(0, 1, 2));
        assertThat(applied.proposalDecisions()).hasSize(3);
        assertThat(applied.proposalDecisions()).allSatisfy(decision ->
                assertThat(decision.catalogDigest()).isEqualTo(harness.digest()));
        assertThat(harness.maintenance().entity("system", "assisted-system").id()).isEqualTo("assisted-system");
        assertThat(harness.maintenance().entity("repository", "assisted-repository").id())
                .isEqualTo("assisted-repository");
        assertThat(harness.maintenance().entity("code-search-scope", "assisted-scope").id())
                .isEqualTo("assisted-scope");
    }

    @Test
    void skippingRepositoryRequiresSkippingDependentScopeAndKeepsItsYamlUnchanged() throws Exception {
        var harness = harness("skipped-reference", proposals());
        var jobId = harness.startJob();
        var digest = harness.digest();
        var scopeDocument = temporaryDirectory.resolve("skipped-reference/code-search-scopes.yml");
        var before = Files.readString(scopeDocument);

        assertThatThrownBy(() -> harness.preview(jobId, Set.of(0, 2)))
                .isInstanceOf(OperationalContextCatalogMaintenanceException.class);
        var applied = harness.apply(jobId, Set.of(0));

        assertThat(harness.digest()).isNotEqualTo(digest);
        assertThat(Files.readString(scopeDocument)).isEqualTo(before);
        assertThat(applied.proposalDecisions()).hasSize(3);
        assertThat(applied.proposalDecisions().get(1).action())
                .isEqualTo(OperationalContextAssistanceProposalDecisionRequest.Action.SKIP);
        assertThat(applied.proposalDecisions().get(2).action())
                .isEqualTo(OperationalContextAssistanceProposalDecisionRequest.Action.SKIP);
        assertThatThrownBy(() -> harness.maintenance().entity("code-search-scope", "assisted-scope"))
                .isInstanceOf(OperationalContextCatalogMaintenanceException.class);
    }

    @Test
    void attachesSharedLibraryToTwoSelectedSystemScopesWithoutReplacingTheirPrimaryRepositories() {
        var harness = harness("library-two-consumers", seededDocuments(), this::sharedLibraryProposals);
        var jobId = harness.startOnboardingJob();

        assertThat(harness.job().getJob(jobId).previews()).hasSize(3);
        harness.apply(jobId, Set.of(0, 1, 2));

        var catalog = harness.store().currentStoredSnapshot().readSnapshot().catalog();
        assertThat(catalog.repositories()).extracting(OperationalContextDtos.OperationalContextRepository::id)
                .contains("shared-library-repo");
        var resolver = new OperationalContextCodeSearchReadModelBuilder();
        for (var systemId : List.of("consumer-a", "consumer-b")) {
            var model = resolver.buildForEntity(catalog, "system", systemId);
            assertThat(model.scopes()).singleElement().satisfies(scope ->
                    assertThat(scope.repositories()).extracting(OperationalContextRelationIndex.EntityRef::id)
                            .containsExactly("primary-" + systemId, "shared-library-repo"));
            assertThat(model.repositories()).extracting(view -> view.repository().id())
                    .containsExactly("primary-" + systemId, "shared-library-repo");
            assertThat(model.repositories().get(0).role()).isEqualTo("primary");
            assertThat(model.repositories().get(1).role()).isEqualTo("library");
        }
        assertThat(harness.job().getJob(jobId).proposalDecisions()).hasSize(3);
    }

    @Test
    void skippingSharedLibraryDoesNotWriteEitherDependentSystemScope() throws Exception {
        var harness = harness("library-skipped", seededDocuments(), this::sharedLibraryProposals);
        var jobId = harness.startOnboardingJob();
        var scopeDocument = temporaryDirectory.resolve("library-skipped/code-search-scopes.yml");
        var before = Files.readString(scopeDocument);

        assertThatThrownBy(() -> harness.preview(jobId, Set.of(1, 2)))
                .isInstanceOf(OperationalContextCatalogMaintenanceException.class);
        var applied = harness.apply(jobId, Set.of());

        assertThat(Files.readString(scopeDocument)).isEqualTo(before);
        assertThat(harness.store().currentStoredSnapshot().readSnapshot().catalog().repositories())
                .extracting(OperationalContextDtos.OperationalContextRepository::id)
                .doesNotContain("shared-library-repo");
        assertThat(applied.proposalDecisions()).hasSize(3);
        assertThat(applied.proposalDecisions()).allSatisfy(decision ->
                assertThat(decision.action()).isEqualTo(
                        OperationalContextAssistanceProposalDecisionRequest.Action.SKIP));
    }

    @Test
    void previewsThenPublishesDependentRepositoryAndTwoScopeUpdatesAsOneBatch() throws Exception {
        var harness = harness("library-batch", seededDocuments(), this::sharedLibraryProposals);
        var digest = harness.digest();
        var changes = sharedLibraryProposals(harness.maintenance());
        var command = batch(digest, changes);
        var repositoryDocument = temporaryDirectory.resolve("library-batch/repo-map.yml");
        var scopeDocument = temporaryDirectory.resolve("library-batch/code-search-scopes.yml");
        var beforeRepository = Files.readString(repositoryDocument);
        var beforeScope = Files.readString(scopeDocument);

        var preview = harness.maintenance().previewAcceptedBatch(command);

        assertThat(preview.valid()).isTrue();
        assertThat(preview.entities()).hasSize(3);
        assertThat(preview.candidateDigest()).isNotEqualTo(digest);
        assertThat(Files.readString(repositoryDocument)).isEqualTo(beforeRepository);
        assertThat(Files.readString(scopeDocument)).isEqualTo(beforeScope);

        var result = harness.maintenance().applyAcceptedBatch(command);

        assertThat(result.entities()).hasSize(3);
        assertThat(result.contentDigest()).isEqualTo(harness.digest());
        assertThat(harness.store().currentStoredSnapshot().readSnapshot().catalog().codeSearchScopes())
                .allSatisfy(scope -> assertThat(scope.repositories())
                        .extracting(OperationalContextDtos.OperationalContextRepositorySearchRepository::repoId)
                        .contains("shared-library-repo"));
        assertThat(Files.exists(temporaryDirectory.resolve("library-batch/.opctx-batch-journal"))).isFalse();
    }

    @Test
    void rejectsStaleBatchDigestAndBeforeWithoutChangingAnyDocument() throws Exception {
        var harness = harness("library-batch-stale", seededDocuments(), this::sharedLibraryProposals);
        var digest = harness.digest();
        var changes = sharedLibraryProposals(harness.maintenance());
        var repositoryDocument = temporaryDirectory.resolve("library-batch-stale/repo-map.yml");
        var scopeDocument = temporaryDirectory.resolve("library-batch-stale/code-search-scopes.yml");
        var beforeRepository = Files.readString(repositoryDocument);
        var beforeScope = Files.readString(scopeDocument);

        assertThatThrownBy(() -> harness.maintenance().applyAcceptedBatch(batch("stale", changes)))
                .isInstanceOf(OperationalContextCatalogMaintenanceException.class)
                .extracting("code").isEqualTo(OperationalContextCatalogMaintenanceException.Code.STALE_PROPOSAL);

        var firstScope = changes.get(1);
        var wrongBefore = new OperationalContextAssistanceDraft.FieldChange(
                "repositories", List.of(), firstScope.changes().get(0).after(), "stale",
                OperationalContextAssistanceDraft.Basis.USER_STATEMENT, List.of("operator:description"),
                OperationalContextAssistanceDraft.Confidence.HIGH, false);
        var staleScope = new OperationalContextAssistanceDraft.Proposal(
                firstScope.operation(), firstScope.entityType(), firstScope.entityId(), List.of(wrongBefore),
                firstScope.confidence(), firstScope.requiresConfirmation(), firstScope.questions(),
                firstScope.visibilityLimits());
        var staleChanges = List.of(changes.get(0), staleScope, changes.get(2));
        assertThatThrownBy(() -> harness.maintenance().previewAcceptedBatch(batch(digest, staleChanges)))
                .isInstanceOf(OperationalContextCatalogMaintenanceException.class)
                .extracting("code").isEqualTo(OperationalContextCatalogMaintenanceException.Code.STALE_PROPOSAL);

        assertThat(Files.readString(repositoryDocument)).isEqualTo(beforeRepository);
        assertThat(Files.readString(scopeDocument)).isEqualTo(beforeScope);
        assertThat(harness.digest()).isEqualTo(digest);
    }

    @Test
    void rejectsExternalYamlEditBetweenBatchPreviewAndPublication() throws Exception {
        var harness = harness("library-batch-external-edit", seededDocuments(), this::sharedLibraryProposals);
        var command = batch(harness.digest(), sharedLibraryProposals(harness.maintenance()));
        assertThat(harness.maintenance().previewAcceptedBatch(command).valid()).isTrue();
        var repositoryDocument = temporaryDirectory.resolve("library-batch-external-edit/repo-map.yml");
        var scopeDocument = temporaryDirectory.resolve("library-batch-external-edit/code-search-scopes.yml");
        var externallyEdited = Files.readString(repositoryDocument) + "# manually edited after preview\n";
        var beforeScope = Files.readString(scopeDocument);
        Files.writeString(repositoryDocument, externallyEdited);

        assertThatThrownBy(() -> harness.maintenance().applyAcceptedBatch(command))
                .isInstanceOf(OperationalContextCatalogMaintenanceException.class)
                .extracting("code").isEqualTo(OperationalContextCatalogMaintenanceException.Code.STALE_PROPOSAL);
        assertThat(Files.readString(repositoryDocument)).isEqualTo(externallyEdited);
        assertThat(Files.readString(scopeDocument)).isEqualTo(beforeScope);
        assertThat(Files.exists(temporaryDirectory.resolve("library-batch-external-edit/.opctx-batch-journal")))
                .isFalse();
    }

    private OperationalContextCatalogConditionalBatchCommand batch(
            String expectedDigest, List<OperationalContextAssistanceDraft.Proposal> proposals
    ) {
        return new OperationalContextCatalogConditionalBatchCommand(expectedDigest, proposals.stream()
                .map(proposal -> new OperationalContextCatalogConditionalBatchCommand.Mutation(
                        proposal.entityType(), proposal.entityId(),
                        OperationalContextCatalogConditionalMutationCommand.Operation.valueOf(
                                proposal.operation().name()),
                        proposal.changes().stream()
                                .map(change -> new OperationalContextCatalogConditionalMutationCommand.FieldChange(
                                        change.path(), change.before(), change.after()))
                                .toList()))
                .toList());
    }

    private Harness harness(String directory, List<OperationalContextAssistanceDraft.Proposal> proposals) {
        return harness(directory, emptyDocuments(), ignored -> proposals);
    }

    private Harness harness(
            String directory, Map<String, String> documents,
            Function<OperationalContextCatalogMaintenanceService, List<OperationalContextAssistanceDraft.Proposal>> proposals
    ) {
        var properties = new OperationalContextProperties();
        properties.setStorageDirectory(temporaryDirectory.resolve(directory).toString());
        var source = (OperationalContextDocumentSource) () ->
                new OperationalContextRawDocuments("classpath", documents);
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var validation = new OperationalContextCatalogValidationService(
                new OperationalContextValidationBaselineLoader(mapper, new DefaultResourceLoader()));
        var local = new LocalOperationalContextStore(
                properties, source, new OperationalContextCatalogCodec(),
                new OperationalContextAtomicMover(), validation);
        var store = new DefaultOperationalContextSnapshotStore(local);
        var maintenance = new OperationalContextCatalogMaintenanceService(store, new OperationalContextYamlWriter());
        var port = new OperationalContextAdapter(store, new OperationalContextCatalogQueryService());
        store.currentStoredSnapshot();

        var prompt = mock(OperationalContextAssistancePromptPreparationService.class);
        when(prompt.prepare(any())).thenReturn(new OperationalContextAssistancePromptPreparation(
                "prompt", Map.of("input.json", "{\"visibilityLimits\":[]}"), Set.of("operator:description")));
        var copilot = mock(OperationalContextAssistanceCopilotProvider.class);
        when(copilot.execute(anyString(), any(), any(), any(), any(), any()))
                .thenReturn(new OperationalContextAssistanceCopilotResult(
                        new CopilotExecutionResult("{draft}", null), Set.of()));
        var parser = mock(OperationalContextAssistanceDraftParser.class);
        when(parser.parse(anyString(), any())).thenReturn(new OperationalContextAssistanceDraft(
                proposals.apply(maintenance), List.of(), List.of()));

        var collector = mock(OperationalContextGitLabSourceCollector.class);
        when(collector.collect(anyString(), anyString())).thenReturn(new OperationalContextGitLabSourceSnapshot(
                "shared/library", new OperationalContextGitLabSourceSnapshot.RepositoryGit(
                        "gitlab", "CLP/PROCESSES", "SHARED_LIBRARY", "CLP/PROCESSES/SHARED_LIBRARY",
                        "https://gitlab.example.com/CLP/PROCESSES/SHARED_LIBRARY"),
                "main", "1111111111111111111111111111111111111111", List.of(), List.of()));

        var job = new OperationalContextAssistanceJobService(
                port, new OperationalContextAssistanceCatalogMaterialService(port, mapper),
                maintenance, validation, collector,
                prompt, copilot, parser, mapper, Runnable::run,
                () -> AnalysisAiAuthRef.localToken("test"),
                pl.mkn.tdw.features.operationalcontextassistance.job.localworkspace
                        .OperationalContextAssistanceLocalRunPersistence.NO_OP);
        return new Harness(job, maintenance, store);
    }

    private Map<String, String> seededDocuments() {
        var documents = emptyDocuments();
        documents.put("systems.yml", """
                schemaVersion: 1
                catalogKind: operational-context-systems
                systems:
                  - id: consumer-a
                    name: Consumer A
                    systemType: internal-service
                    systemSubtype: unknown
                  - id: consumer-b
                    name: Consumer B
                    systemType: internal-service
                    systemSubtype: unknown
                """);
        documents.put("repo-map.yml", """
                schemaVersion: 1
                catalogKind: operational-context-repositories
                repositories:
                  - id: primary-consumer-a
                    name: Primary Consumer A
                    repositoryType: service
                    git: {provider: gitlab, group: CLP, project: CONSUMER_A, projectPath: CLP/CONSUMER_A}
                  - id: primary-consumer-b
                    name: Primary Consumer B
                    repositoryType: service
                    git: {provider: gitlab, group: CLP, project: CONSUMER_B, projectPath: CLP/CONSUMER_B}
                """);
        documents.put("code-search-scopes.yml", """
                schemaVersion: 1
                catalogKind: operational-context-code-search-scopes
                codeSearchScopes:
                  - id: consumer-a-code
                    name: Consumer A Code
                    scopeType: system
                    target: {type: system, id: consumer-a}
                    repositories:
                      - {repoId: primary-consumer-a, role: primary, priority: 1, searchMode: whole-repository}
                  - id: consumer-b-code
                    name: Consumer B Code
                    scopeType: system
                    target: {type: system, id: consumer-b}
                    repositories:
                      - {repoId: primary-consumer-b, role: primary, priority: 1, searchMode: whole-repository}
                """);
        return documents;
    }

    private List<OperationalContextAssistanceDraft.Proposal> sharedLibraryProposals(
            OperationalContextCatalogMaintenanceService maintenance
    ) {
        return List.of(
                proposal("repository", "shared-library-repo", Map.of(
                        "name", "Shared Library", "repositoryType", "shared-library",
                        "references", Map.of("systems", List.of("consumer-a", "consumer-b")),
                        "git", Map.of("provider", "gitlab", "group", "CLP/PROCESSES",
                                "project", "SHARED_LIBRARY", "projectPath", "CLP/PROCESSES/SHARED_LIBRARY",
                                "url", "https://gitlab.example.com/CLP/PROCESSES/SHARED_LIBRARY"))),
                scopeLibraryUpdate(maintenance, "consumer-a-code"),
                scopeLibraryUpdate(maintenance, "consumer-b-code")
        );
    }

    @SuppressWarnings("unchecked")
    private OperationalContextAssistanceDraft.Proposal scopeLibraryUpdate(
            OperationalContextCatalogMaintenanceService maintenance, String scopeId
    ) {
        var before = (List<Object>) maintenance.writablePayloadForUpdate("code-search-scope", scopeId)
                .get("repositories");
        var after = new ArrayList<>(before);
        after.add(Map.of("repoId", "shared-library-repo", "role", "library", "priority", 2,
                "searchMode", "whole-repository"));
        var change = new OperationalContextAssistanceDraft.FieldChange(
                "repositories", before, after, "Operator wskazał system korzystający z biblioteki.",
                OperationalContextAssistanceDraft.Basis.USER_STATEMENT,
                List.of("operator:description"), OperationalContextAssistanceDraft.Confidence.HIGH, false
        );
        return new OperationalContextAssistanceDraft.Proposal(
                OperationalContextAssistanceDraft.Operation.UPDATE, "code-search-scope", scopeId,
                List.of(change), OperationalContextAssistanceDraft.Confidence.HIGH,
                false, List.of(), List.of());
    }

    private Map<String, String> emptyDocuments() {
        var documents = new LinkedHashMap<String, String>();
        documents.put("teams.yml", yaml("operational-context-teams", "teams"));
        documents.put("processes.yml", yaml("operational-context-processes", "processes"));
        documents.put("systems.yml", yaml("operational-context-systems", "systems"));
        documents.put("integrations.yml", yaml("operational-context-integrations", "integrations"));
        documents.put("repo-map.yml", yaml("operational-context-repositories", "repositories"));
        documents.put("code-search-scopes.yml", yaml("operational-context-code-search-scopes", "codeSearchScopes"));
        documents.put("bounded-contexts.yml", yaml("operational-context-bounded-contexts", "boundedContexts"));
        documents.put("glossary.yml", yaml("operational-context-glossary", "terms"));
        documents.put("handoff-rules.yml", yaml("operational-context-handoff-rules", "handoffRules"));
        return documents;
    }

    private String yaml(String kind, String collection) {
        return "schemaVersion: 1\ncatalogKind: " + kind + "\n" + collection + ": []\n";
    }

    private List<OperationalContextAssistanceDraft.Proposal> proposals() {
        return List.of(
                proposal("system", "assisted-system", Map.of(
                        "name", "Assisted System", "systemType", "internal-service", "systemSubtype", "unknown")),
                proposal("repository", "assisted-repository", Map.of(
                        "name", "Assisted Repository", "repositoryType", "service",
                        "git", Map.of("provider", "gitlab", "projectPath", "example/assisted"))),
                proposal("code-search-scope", "assisted-scope", Map.of(
                        "name", "Assisted Scope", "scopeType", "system",
                        "target", Map.of("type", "system", "id", "assisted-system"),
                        "repositories", List.of(Map.of(
                                "repoId", "assisted-repository", "role", "primary", "priority", 1,
                                "searchMode", "whole-repository", "pathPrefixes", List.of()))))
        );
    }

    private OperationalContextAssistanceDraft.Proposal proposal(
            String type, String id, Map<String, Object> fields
    ) {
        var changes = fields.entrySet().stream().map(entry ->
                new OperationalContextAssistanceDraft.FieldChange(
                        entry.getKey(), null, entry.getValue(), "Operator confirmed the source.",
                        OperationalContextAssistanceDraft.Basis.USER_STATEMENT,
                        List.of("operator:description"),
                        OperationalContextAssistanceDraft.Confidence.HIGH, false
                )).toList();
        return new OperationalContextAssistanceDraft.Proposal(
                OperationalContextAssistanceDraft.Operation.CREATE, type, id, changes,
                OperationalContextAssistanceDraft.Confidence.HIGH, false, List.of(), List.of());
    }

    private record Harness(
            OperationalContextAssistanceJobService job,
            OperationalContextCatalogMaintenanceService maintenance,
            DefaultOperationalContextSnapshotStore store
    ) {
        String digest() {
            return store.currentStoredSnapshot().readSnapshot().contentDigest();
        }

        String startJob() {
            var started = job.startJob(new OperationalContextAssistanceJobStartRequest(
                    OperationalContextAssistanceMode.CREATE_AREA, "Describe the assisted area",
                    null, null, null, null, null));
            return started.jobId();
        }

        String startOnboardingJob() {
            var started = job.startJob(new OperationalContextAssistanceJobStartRequest(
                    OperationalContextAssistanceMode.CREATE_AREA, "Podłącz bibliotekę do wskazanych systemów",
                    null, new OperationalContextAssistanceJobStartRequest.GitLabSource("SHARED_LIBRARY", null, "main"),
                    new OperationalContextAssistanceRepositoryFacts(
                            OperationalContextAssistanceRepositoryFacts.Usage.SHARED_LIBRARY,
                            null, null, List.of("consumer-a", "consumer-b")), null, null));
            return started.jobId();
        }

        pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceBatchPreview preview(
                String jobId, Set<Integer> appliedIndexes
        ) {
            return job.previewBatch(jobId, review(jobId, appliedIndexes, null));
        }

        pl.mkn.tdw.features.operationalcontextassistance.api.OperationalContextAssistanceJobSnapshot apply(
                String jobId, Set<Integer> appliedIndexes
        ) {
            var preview = preview(jobId, appliedIndexes);
            assertThat(preview.valid()).isTrue();
            return job.applyBatch(jobId, review(jobId, appliedIndexes, preview.candidateDigest()));
        }

        private OperationalContextAssistanceBatchReviewRequest review(
                String jobId, Set<Integer> appliedIndexes, String candidateDigest
        ) {
            var proposals = job.getJob(jobId).draft().proposals();
            var decisions = new ArrayList<OperationalContextAssistanceProposalDecisionRequest>();
            for (var index = 0; index < proposals.size(); index++) {
                if (!appliedIndexes.contains(index)) {
                    decisions.add(new OperationalContextAssistanceProposalDecisionRequest(
                            OperationalContextAssistanceProposalDecisionRequest.Action.SKIP, List.of(), List.of()));
                    continue;
                }
                var paths = proposals.get(index).changes().stream()
                        .map(OperationalContextAssistanceDraft.FieldChange::path).toList();
                decisions.add(new OperationalContextAssistanceProposalDecisionRequest(
                        OperationalContextAssistanceProposalDecisionRequest.Action.APPLY, paths, paths));
            }
            return new OperationalContextAssistanceBatchReviewRequest(decisions, candidateDigest);
        }
    }
}
