package pl.mkn.tdw.features.operationalcontextassistance.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCatalogMaintenanceService;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceMode;
import pl.mkn.tdw.features.operationalcontextassistance.ai.OperationalContextAssistanceRepositoryFacts;
import pl.mkn.tdw.features.operationalcontextassistance.source.OperationalContextGitLabSourceSnapshot;

import java.util.Set;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationalContextAssistanceDraftParserTest {

    private final OperationalContextAssistanceDraftParser parser = new OperationalContextAssistanceDraftParser(
            new ObjectMapper(), new OperationalContextCatalogMaintenanceService(null, null)
    );

    @Test
    void parsesSmallReadOnlyCreateDraftWithCollectedProvenance() {
        var draft = parser.parse(validDraft(), createScope());

        assertThat(draft.proposals()).singleElement().satisfies(proposal -> {
            assertThat(proposal.operation()).isEqualTo(OperationalContextAssistanceDraft.Operation.CREATE);
            assertThat(proposal.entityType()).isEqualTo("system");
            assertThat(proposal.changes()).singleElement().satisfies(change -> {
                assertThat(change.path()).isEqualTo("name");
                assertThat(change.after()).isEqualTo("Order Intake");
                assertThat(change.reason()).isEqualTo("Operator nazwał ten obszar Order Intake.");
                assertThat(change.sourceRefs()).containsExactly("operator:description");
            });
        });
    }

    @Test
    void acceptsOneJsonCodeFenceAndPreservesQuestionOnlyModelAnswer() {
        var questionOnly = """
                {"proposals":[],"questions":["Które repozytorium mam przeczytać?"],
                 "visibilityLimits":["Nie wybrano źródła GitLab."]}
                """;

        var draft = parser.parse("```json\n" + questionOnly + "```", createScope());

        assertThat(draft.proposals()).isEmpty();
        assertThat(draft.questions()).containsExactly("Które repozytorium mam przeczytać?");
        assertThat(draft.visibilityLimits()).containsExactly("Nie wybrano źródła GitLab.");
        assertThat(parser.parse("```\n" + validDraft() + "\n```", createScope()).proposals())
                .hasSize(1);
    }

    @Test
    void rejectsCommentaryOrExtraFenceAroundJson() {
        var wrapped = "```json\n" + validDraft() + "\n```";

        for (var invalid : List.of(
                "Oto wynik:\n" + wrapped,
                wrapped + "\nDodatkowy komentarz",
                wrapped + "\n```json\n{}\n```"
        )) {
            assertThatThrownBy(() -> parser.parse(invalid, createScope()))
                    .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                    .hasMessageContaining("strict JSON");
        }
    }

    @Test
    void acceptsOneCatalogRevisionAcrossGlossaryAndIntegrationDocuments() {
        var response = """
                {"proposals":[
                  {"operation":"UPDATE","entityType":"glossary-term","entityId":"agreement",
                   "changes":[{"path":"definition","before":"Stara definicja","after":"Nowa definicja",
                     "reason":"Istniejący termin wymaga uściślenia.","basis":"SOURCE_OBSERVATION",
                     "sourceRefs":["opctx:glossary.yml"],"confidence":"MEDIUM","requiresConfirmation":true}],
                   "confidence":"MEDIUM","requiresConfirmation":true,"questions":[],"visibilityLimits":[]},
                  {"operation":"CREATE","entityType":"integration","entityId":"agreement-to-archive",
                   "changes":[{"path":"name","after":"Archiwizacja umowy",
                     "reason":"Operator wskazał trwałą relację.","basis":"USER_STATEMENT",
                     "sourceRefs":["operator:description","opctx:systems.yml"],"confidence":"MEDIUM","requiresConfirmation":true}],
                   "confidence":"MEDIUM","requiresConfirmation":true,"questions":[],"visibilityLimits":[]}
                ],"questions":[],"visibilityLimits":[]}
                """;
        var scope = new OperationalContextAssistanceDraftScope(
                OperationalContextAssistanceMode.CREATE_AREA, null, null,
                Set.of("operator:description", "opctx:glossary.yml", "opctx:systems.yml"));

        assertThat(parser.parse(response, scope).proposals())
                .extracting(OperationalContextAssistanceDraft.Proposal::entityType)
                .containsExactly("glossary-term", "integration");
    }

    @Test
    void rejectsUnknownEnvelopeAndNestedCatalogFields() {
        assertThatThrownBy(() -> parser.parse(validDraft().replace("\"questions\": []", "\"unknown\": true, \"questions\": []"),
                createScope()))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("unknown field");

        var nested = validDraft().replace("\"path\": \"name\", \"after\": \"Order Intake\"",
                "\"path\": \"runtime\", \"after\": {\"configurationDirectory\": \"config\", \"obsolete\": \"x\"}");
        assertThatThrownBy(() -> parser.parse(nested, createScope()))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("not writable");
    }

    @Test
    void rejectsChangeWithoutReviewableReason() {
        assertThatThrownBy(() -> parser.parse(
                validDraft().replace("\"reason\": \"Operator nazwał ten obszar Order Intake.\", ", ""),
                createScope()))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void rejectsSensitiveModelOutputBeforeItCanReachTheReview() {
        assertThatThrownBy(() -> parser.parse(
                validDraft().replace("\"after\": \"Order Intake\"", "\"after\": \"apiKey=top-secret\""),
                createScope()))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("sensitive content");

        assertThatThrownBy(() -> parser.parse(
                validDraft().replace("Operator nazwał ten obszar Order Intake.", "Kontakt: user@example.com"),
                createScope()))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("sensitive content");
    }

    @Test
    void rejectsReadProjectionPreserveOnlyAndInventedSources() {
        for (var field : new String[]{"rawSourcePreview", "dependencies"}) {
            var response = validDraft().replace("\"path\": \"name\"", "\"path\": \"" + field + "\"");
            assertThatThrownBy(() -> parser.parse(response, createScope()))
                    .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                    .hasMessageContaining("not writable");
        }
        var invented = validDraft().replace("operator:description", "gitlab:unread-file");
        assertThatThrownBy(() -> parser.parse(invented, createScope()))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("collected sources");
    }

    @Test
    void rejectsAiConfirmedOwnershipAndFrontendClassification() {
        var ownership = validDraft().replace("\"path\": \"name\", \"after\": \"Order Intake\"",
                "\"path\": \"ownership\", \"after\": {\"ownershipStatus\": \"explicit\", \"ownerLabel\": \"Team A\"}");
        assertThatThrownBy(() -> parser.parse(ownership, createScope()))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("cannot confirm ownership");

        var frontend = validDraft().replace("\"path\": \"name\", \"after\": \"Order Intake\"",
                "\"path\": \"systemSubtype\", \"after\": \"frontend\"");
        assertThatThrownBy(() -> parser.parse(frontend, createScope()))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("cannot confirm frontend");
    }

    @Test
    void rejectsMissingBeforeOnUpdateAndDuplicateJsonKeys() {
        var update = validDraft().replace("\"operation\": \"CREATE\"", "\"operation\": \"UPDATE\"");
        assertThatThrownBy(() -> parser.parse(update, createScope()))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("before is required");

        var duplicate = validDraft().replace("\"entityId\": \"order-intake\"",
                "\"entityId\": \"order-intake\", \"entityId\": \"other\"");
        assertThatThrownBy(() -> parser.parse(duplicate, createScope()))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("strict JSON");
    }

    @Test
    void rejectsRepositoryWithoutSelectedEvidenceAndUpdateOutsideTarget() {
        var repository = validDraft().replace("\"entityType\": \"system\"", "\"entityType\": \"repository\"");
        assertThatThrownBy(() -> parser.parse(repository, createScope()))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("read file from the selected GitLab project");
        var sourceScope = new OperationalContextAssistanceDraftScope(
                OperationalContextAssistanceMode.CREATE_AREA, null, null,
                Set.of("operator:description", "gitlab:demo-group/demo-app@1111111111111111111111111111111111111111:README.md")
        );
        assertThatThrownBy(() -> parser.parse(repository, sourceScope))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("read file from the selected GitLab project");

        var update = validDraft().replace("\"operation\": \"CREATE\"", "\"operation\": \"UPDATE\"")
                .replace("\"path\": \"name\"", "\"path\": \"summary\", \"before\": null");
        var scope = new OperationalContextAssistanceDraftScope(
                OperationalContextAssistanceMode.IMPROVE_ENTITY, "system", "another-system", Set.of("operator:description")
        );
        assertThatThrownBy(() -> parser.parse(update, scope))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("selected entity");
    }

    @Test
    void acceptsTargetedUpdateAndQuestionOnlyFinding() {
        var update = validDraft().replace("\"operation\": \"CREATE\"", "\"operation\": \"UPDATE\"")
                .replace("\"path\": \"name\"", "\"path\": \"summary\", \"before\": null");
        var scope = new OperationalContextAssistanceDraftScope(
                OperationalContextAssistanceMode.IMPROVE_ENTITY, "system", "order-intake", Set.of("operator:description")
        );
        assertThat(parser.parse(update, scope).proposals()).singleElement()
                .satisfies(proposal -> assertThat(proposal.changes().get(0).before()).isNull());

        var questionOnly = """
                {"proposals":[],"questions":["Kto potwierdza ownera?"],"visibilityLimits":["Brak źródła."]}
                """;
        var findingScope = new OperationalContextAssistanceDraftScope(
                OperationalContextAssistanceMode.RESOLVE_FINDING, "system", "order-intake", Set.of("operator:description")
        );
        assertThat(parser.parse(questionOnly, findingScope).proposals()).isEmpty();
    }

    @Test
    void requiresRepositoryGitIdentityToMatchSelectedNestedProject() {
        var selected = new OperationalContextGitLabSourceSnapshot.RepositoryGit(
                "gitlab", "CLP/PROCESSES", "CLP_AGREEMENT_PROCESS",
                "CLP/PROCESSES/CLP_AGREEMENT_PROCESS",
                "https://gitlab.example.com/CLP/PROCESSES/CLP_AGREEMENT_PROCESS"
        );
        var scope = new OperationalContextAssistanceDraftScope(
                OperationalContextAssistanceMode.IMPROVE_ENTITY, "repository", "agreement-process",
                Set.of("operator:description", "gitlab:CLP/PROCESSES/CLP_AGREEMENT_PROCESS@"
                        + "1111111111111111111111111111111111111111:README.md"), selected
        );
        var valid = """
                {
                  "proposals": [{
                    "operation":"UPDATE", "entityType":"repository", "entityId":"agreement-process",
                    "changes":[{
                      "path":"git", "before":null,
                      "after":{"provider":"gitlab", "group":"CLP/PROCESSES",
                               "project":"CLP_AGREEMENT_PROCESS",
                               "projectPath":"CLP/PROCESSES/CLP_AGREEMENT_PROCESS",
                               "url":"https://gitlab.example.com/CLP/PROCESSES/CLP_AGREEMENT_PROCESS"},
                      "reason":"Potwierdzony adres wybranego repozytorium.",
                      "basis":"SOURCE_OBSERVATION",
                      "sourceRefs":["gitlab:CLP/PROCESSES/CLP_AGREEMENT_PROCESS@1111111111111111111111111111111111111111:README.md"],
                      "confidence":"HIGH", "requiresConfirmation":false
                    }],
                    "confidence":"HIGH", "requiresConfirmation":false,
                    "questions":[], "visibilityLimits":[]
                  }],
                  "questions":[], "visibilityLimits":[]
                }
                """;

        assertThat(parser.parse(valid, scope).proposals()).hasSize(1);
        assertThatThrownBy(() -> parser.parse(
                valid.replace("\"projectPath\":\"CLP/PROCESSES/CLP_AGREEMENT_PROCESS\"",
                        "\"projectPath\":\"CLP/OTHER/CLP_AGREEMENT_PROCESS\""), scope))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("differs from the selected GitLab source");

        var otherRef = "gitlab:CLP/LIBS/shared-client@2222222222222222222222222222222222222222:pom.xml";
        var otherOnlyScope = new OperationalContextAssistanceDraftScope(
                OperationalContextAssistanceMode.IMPROVE_ENTITY, "repository", "agreement-process",
                Set.of("operator:description", otherRef), selected);
        assertThat(otherOnlyScope.hasSelectedSource()).isFalse();
        assertThatThrownBy(() -> parser.parse(
                valid.replace("gitlab:CLP/PROCESSES/CLP_AGREEMENT_PROCESS@1111111111111111111111111111111111111111:README.md",
                        otherRef), otherOnlyScope))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("read GitLab file from the selected project");
    }

    @Test
    void acceptsRepositoryOnlyForUnknownAndSharedLibraryWithoutInventingSystem() {
        var unknown = onboardingScope(OperationalContextAssistanceRepositoryFacts.Usage.UNKNOWN, null, List.of(), Set.of());
        var unknownDraft = draft(repositoryProposal("repo-one", false, null));
        assertThat(parser.parse(unknownDraft, unknown).proposals()).extracting(OperationalContextAssistanceDraft.Proposal::entityType)
                .containsExactly("repository");

        var library = onboardingScope(OperationalContextAssistanceRepositoryFacts.Usage.SHARED_LIBRARY,
                null, List.of(), Set.of());
        var libraryDraft = draft(repositoryProposal("library-one", true, null));
        assertThat(parser.parse(libraryDraft, library).proposals()).extracting(OperationalContextAssistanceDraft.Proposal::entityType)
                .containsExactly("repository");
        assertThatThrownBy(() -> parser.parse(draft(systemProposal("invented-system", "Invented"),
                repositoryProposal("library-one", true, null)), library))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("does not permit a new system");
    }

    @Test
    void acceptsRelatedIntegrationAlongsideSelectedRepositoryOnboarding() {
        var scope = onboardingScope(OperationalContextAssistanceRepositoryFacts.Usage.DEPLOYED_SYSTEM,
                "Agreement Service", List.of(), Set.of());
        var integration = """
                {"operation":"CREATE","entityType":"integration","entityId":"agreement-to-archive",
                 "changes":[{"path":"name","after":"Przekazanie umowy do archiwum",
                   "reason":"Operator opisał relację systemową.","basis":"USER_STATEMENT",
                   "sourceRefs":["operator:description"],"confidence":"MEDIUM","requiresConfirmation":true}],
                 "confidence":"MEDIUM","requiresConfirmation":true,"questions":[],"visibilityLimits":[]}
                """;

        assertThat(parser.parse(draft(systemProposal("agreement-service", "Agreement Service"),
                repositoryProposal("agreement-repo", false, "agreement-service"), integration), scope).proposals())
                .extracting(OperationalContextAssistanceDraft.Proposal::entityType)
                .containsExactly("system", "repository", "integration");
    }

    @Test
    void gitLabSourceWithoutFactsAllowsGeneralCatalogRevision() {
        var noFacts = new OperationalContextAssistanceDraftScope(
                OperationalContextAssistanceMode.CREATE_AREA, null, null,
                Set.of("operator:description",
                        "gitlab:CLP/PROCESSES/CLP_AGREEMENT_PROCESS@1111111111111111111111111111111111111111:README.md"),
                selectedRepositoryGit()
        );
        assertThat(noFacts.repositoryUsage()).isEqualTo(OperationalContextAssistanceRepositoryFacts.Usage.UNKNOWN);
        assertThat(parser.parse(draft(repositoryProposal("repo-one", false, null)), noFacts).proposals())
                .extracting(OperationalContextAssistanceDraft.Proposal::entityType).containsExactly("repository");
        assertThat(parser.parse(draft(systemProposal("invented-system", "Invented")
                .replace("operator:repository-facts", "operator:description")), noFacts).proposals())
                .extracting(OperationalContextAssistanceDraft.Proposal::entityType).containsExactly("system");
    }

    @Test
    void createdRepositoryGitMustCiteReadFileOfSelectedProject() {
        var scope = onboardingScope(OperationalContextAssistanceRepositoryFacts.Usage.UNKNOWN,
                null, List.of(), Set.of());
        var withoutFileRef = repositoryProposal("repo-one", false, null)
                .replace("\"basis\":\"SOURCE_OBSERVATION\",\"sourceRefs\":[\"gitlab:CLP/PROCESSES/CLP_AGREEMENT_PROCESS@1111111111111111111111111111111111111111:README.md\"]",
                        "\"basis\":\"USER_STATEMENT\",\"sourceRefs\":[\"operator:repository-facts\"]");
        assertThatThrownBy(() -> parser.parse(draft(withoutFileRef), scope))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("must cite a read GitLab file");
    }

    @Test
    void newSystemCannotAddInferredOrNonExactServiceNames() {
        var sourceRef = "gitlab:CLP/PROCESSES/CLP_AGREEMENT_PROCESS@1111111111111111111111111111111111111111:README.md";
        var selectedGit = selectedRepositoryGit();
        var withRuntime = new OperationalContextAssistanceDraftScope(
                OperationalContextAssistanceMode.CREATE_AREA, null, null,
                Set.of("operator:description", "operator:repository-facts", sourceRef), selectedGit,
                new OperationalContextAssistanceRepositoryFacts(
                        OperationalContextAssistanceRepositoryFacts.Usage.DEPLOYED_SYSTEM,
                        "Agreement Service", "agreement-runtime", List.of()), Set.of()
        );
        var withoutRuntime = onboardingScope(OperationalContextAssistanceRepositoryFacts.Usage.DEPLOYED_SYSTEM,
                "Agreement Service", List.of(), Set.of());

        assertThat(parser.parse(draft(systemProposalWithSignals("{\"exact\":{\"serviceNames\":[\"agreement-runtime\"]}}")),
                withRuntime).proposals()).hasSize(1);
        for (String signals : List.of(
                "{\"strong\":{\"serviceNames\":[\"agreement-runtime\"]}}",
                "{\"serviceNames\":[\"agreement-runtime\"]}",
                "{\"exact\":{\"serviceNames\":[\"invented-runtime\"]}}"
        )) {
            assertThatThrownBy(() -> parser.parse(draft(systemProposalWithSignals(signals)), withRuntime))
                    .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                    .hasMessageContaining("Service names");
        }
        assertThatThrownBy(() -> parser.parse(draft(systemProposalWithSignals(
                "{\"exact\":{\"serviceNames\":[\"invented-runtime\"]}}")), withoutRuntime))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("Service names");
    }

    @Test
    void deployedSystemRequiresOperatorNameAndSelectedRepoIdentity() {
        var named = onboardingScope(OperationalContextAssistanceRepositoryFacts.Usage.DEPLOYED_SYSTEM,
                "Agreement Service", List.of(), Set.of());
        assertThat(parser.parse(draft(systemProposal("agreement-service", "Agreement Service"),
                repositoryProposal("agreement-repo", false, "agreement-service")), named).proposals()).hasSize(2);
        assertThatThrownBy(() -> parser.parse(draft(systemProposal("agreement-service", "Invented")), named))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("must match");

        var unnamed = onboardingScope(OperationalContextAssistanceRepositoryFacts.Usage.DEPLOYED_SYSTEM,
                null, List.of(), Set.of());
        assertThatThrownBy(() -> parser.parse(draft(systemProposal("agreement-service", "Invented")), unnamed))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("does not permit a new system");
    }

    @Test
    void selectedSystemScopeMayOnlyAppendCreatedRepositoryWithoutChangingPrimary() {
        var scope = onboardingScope(OperationalContextAssistanceRepositoryFacts.Usage.SHARED_LIBRARY,
                null, List.of("system-one"), Set.of("system-one-code"));
        var repo = repositoryProposal("library-one", true, "system-one");
        var valid = scopeUpdateProposal("system-one-code", "library-one", true);
        assertThat(parser.parse(draft(repo, valid), scope).proposals()).hasSize(2);
        assertThatThrownBy(() -> parser.parse(draft(repo), scope))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("update every selected system code-search scope");

        assertThatThrownBy(() -> parser.parse(draft(repo,
                scopeUpdateProposal("other-system-code", "library-one", true)), scope))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("selected system code-search scope");
        assertThatThrownBy(() -> parser.parse(draft(repo,
                scopeUpdateProposal("system-one-code", "library-one", false)), scope))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("preserve existing repositories");
    }

    @Test
    void repositoryOnboardingRequiresEverySelectedScopeForExistingSystemAndLibraryConsumers() {
        var existing = onboardingScope(OperationalContextAssistanceRepositoryFacts.Usage.EXISTING_SYSTEM,
                null, List.of("system-one"), Set.of("system-one-code"));
        var existingRepo = repositoryProposal("repo-one", false, "system-one");
        assertThatThrownBy(() -> parser.parse(draft(existingRepo), existing))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("update every selected system code-search scope");
        assertThat(parser.parse(draft(existingRepo,
                scopeUpdateProposal("system-one-code", "repo-one", true)), existing).proposals()).hasSize(2);

        var library = onboardingScope(OperationalContextAssistanceRepositoryFacts.Usage.SHARED_LIBRARY,
                null, List.of("system-one", "system-two"), Set.of("system-one-code", "system-two-code"));
        var libraryRepo = repositoryProposal("library-one", true, "system-one");
        assertThatThrownBy(() -> parser.parse(draft(libraryRepo,
                scopeUpdateProposal("system-one-code", "library-one", true)), library))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("update every selected system code-search scope");
        assertThat(parser.parse(draft(libraryRepo,
                scopeUpdateProposal("system-one-code", "library-one", true),
                scopeUpdateProposal("system-two-code", "library-one", true)), library).proposals()).hasSize(3);
    }

    @Test
    void operatorFactsDoNotCountAsSourceObservation() {
        var selected = selectedRepositoryGit();
        var factsOnly = new OperationalContextAssistanceDraftScope(
                OperationalContextAssistanceMode.CREATE_AREA, null, null,
                Set.of("operator:description", "operator:repository-facts"), selected,
                new OperationalContextAssistanceRepositoryFacts(
                        OperationalContextAssistanceRepositoryFacts.Usage.UNKNOWN, null, null, List.of()), Set.of()
        );
        assertThat(factsOnly.hasSelectedSource()).isFalse();
        assertThatThrownBy(() -> parser.parse(draft(repositoryProposal("repo-one", false, null)), factsOnly))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("collected sources");
        var observation = validDraft().replace("\"basis\": \"USER_STATEMENT\"",
                "\"basis\": \"SOURCE_OBSERVATION\"")
                .replace("\"sourceRefs\": [\"operator:description\"]",
                        "\"sourceRefs\": [\"operator:repository-facts\"]");
        assertThatThrownBy(() -> parser.parse(observation, factsOnly))
                .isInstanceOf(OperationalContextAssistanceDraftParseException.class)
                .hasMessageContaining("needs a catalog document or GitLab file");
    }

    private OperationalContextAssistanceDraftScope onboardingScope(
            OperationalContextAssistanceRepositoryFacts.Usage usage, String systemName,
            List<String> systemIds, Set<String> selectedScopeIds
    ) {
        return new OperationalContextAssistanceDraftScope(
                OperationalContextAssistanceMode.CREATE_AREA, null, null,
                Set.of("operator:description", "operator:repository-facts",
                        "gitlab:CLP/PROCESSES/CLP_AGREEMENT_PROCESS@1111111111111111111111111111111111111111:README.md"),
                selectedRepositoryGit(),
                new OperationalContextAssistanceRepositoryFacts(usage, systemName, null, systemIds), selectedScopeIds
        );
    }

    private OperationalContextGitLabSourceSnapshot.RepositoryGit selectedRepositoryGit() {
        return new OperationalContextGitLabSourceSnapshot.RepositoryGit(
                "gitlab", "CLP/PROCESSES", "CLP_AGREEMENT_PROCESS",
                "CLP/PROCESSES/CLP_AGREEMENT_PROCESS",
                "https://gitlab.example.com/CLP/PROCESSES/CLP_AGREEMENT_PROCESS"
        );
    }

    private String draft(String... proposals) {
        return "{\"proposals\":[" + String.join(",", proposals) + "],\"questions\":[],\"visibilityLimits\":[]}";
    }

    private String systemProposal(String id, String name) {
        return "{\"operation\":\"CREATE\",\"entityType\":\"system\",\"entityId\":\"" + id
                + "\",\"changes\":[" + change("name", "\"" + name + "\"")
                + "],\"confidence\":\"MEDIUM\",\"requiresConfirmation\":true,\"questions\":[],\"visibilityLimits\":[]}";
    }

    private String systemProposalWithSignals(String signals) {
        var name = "Agreement Service";
        return "{\"operation\":\"CREATE\",\"entityType\":\"system\",\"entityId\":\"agreement-service\""
                + ",\"changes\":[" + change("name", "\"" + name + "\"")
                + "," + change("matchSignals", signals) + "],\"confidence\":\"MEDIUM\""
                + ",\"requiresConfirmation\":true,\"questions\":[],\"visibilityLimits\":[]}";
    }

    private String repositoryProposal(String id, boolean library, String systemId) {
        var git = "{\"provider\":\"gitlab\",\"group\":\"CLP/PROCESSES\","
                + "\"project\":\"CLP_AGREEMENT_PROCESS\","
                + "\"projectPath\":\"CLP/PROCESSES/CLP_AGREEMENT_PROCESS\","
                + "\"url\":\"https://gitlab.example.com/CLP/PROCESSES/CLP_AGREEMENT_PROCESS\"}";
        var changes = new java.util.ArrayList<String>();
        changes.add(gitChange(git));
        if (library) {
            changes.add(change("repositoryType", "\"shared-library\""));
        }
        if (systemId != null) {
            changes.add(change("references", "{\"systems\":[\"" + systemId + "\"]}"));
        }
        return "{\"operation\":\"CREATE\",\"entityType\":\"repository\",\"entityId\":\"" + id
                + "\",\"changes\":[" + String.join(",", changes)
                + "],\"confidence\":\"MEDIUM\",\"requiresConfirmation\":true,\"questions\":[],\"visibilityLimits\":[]}";
    }

    private String scopeUpdateProposal(String scopeId, String repoId, boolean preserveExisting) {
        var existing = "{\"repoId\":\"primary-repo\",\"role\":\"primary\",\"priority\":1,"
                + "\"searchMode\":\"whole-repository\"}";
        var added = "{\"repoId\":\"" + repoId + "\",\"role\":\"library\",\"priority\":2,"
                + "\"searchMode\":\"whole-repository\"}";
        var before = "[" + existing + "]";
        var after = preserveExisting ? "[" + existing + "," + added + "]" : "[" + added + "," + existing + "]";
        return "{\"operation\":\"UPDATE\",\"entityType\":\"code-search-scope\",\"entityId\":\""
                + scopeId + "\",\"changes\":[{\"path\":\"repositories\",\"before\":" + before
                + ",\"after\":" + after + ",\"reason\":\"Operator wskazał system korzystający z biblioteki.\","
                + "\"basis\":\"USER_STATEMENT\",\"sourceRefs\":[\"operator:repository-facts\"],"
                + "\"confidence\":\"MEDIUM\",\"requiresConfirmation\":true}],"
                + "\"confidence\":\"MEDIUM\",\"requiresConfirmation\":true,\"questions\":[],\"visibilityLimits\":[]}";
    }

    private String change(String path, String after) {
        return "{\"path\":\"" + path + "\",\"after\":" + after
                + ",\"reason\":\"Operator podał fakt o projekcie.\",\"basis\":\"USER_STATEMENT\","
                + "\"sourceRefs\":[\"operator:repository-facts\"],\"confidence\":\"MEDIUM\","
                + "\"requiresConfirmation\":true}";
    }

    private String gitChange(String after) {
        return "{\"path\":\"git\",\"after\":" + after
                + ",\"reason\":\"Tożsamość wybranego projektu GitLab.\",\"basis\":\"SOURCE_OBSERVATION\","
                + "\"sourceRefs\":[\"gitlab:CLP/PROCESSES/CLP_AGREEMENT_PROCESS@1111111111111111111111111111111111111111:README.md\"],"
                + "\"confidence\":\"MEDIUM\",\"requiresConfirmation\":true}";
    }

    private OperationalContextAssistanceDraftScope createScope() {
        return new OperationalContextAssistanceDraftScope(
                OperationalContextAssistanceMode.CREATE_AREA, null, null, Set.of("operator:description")
        );
    }

    private String validDraft() {
        return """
                {
                  "proposals": [{
                    "operation": "CREATE", "entityType": "system", "entityId": "order-intake",
                    "changes": [{
                      "path": "name", "after": "Order Intake", "reason": "Operator nazwał ten obszar Order Intake.", "basis": "USER_STATEMENT",
                      "sourceRefs": ["operator:description"], "confidence": "HIGH",
                      "requiresConfirmation": false
                    }],
                    "confidence": "HIGH", "requiresConfirmation": false,
                    "questions": [], "visibilityLimits": []
                  }],
                  "questions": [], "visibilityLimits": []
                }
                """;
    }
}
