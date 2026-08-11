package pl.mkn.tdw.integrations.operationalcontext;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OperationalContextMaintenanceInstructionsTest {

    @Test
    void shouldKeepSystemMaintenancePromptFreeFromRepositoryReferences() throws IOException {
        var prompt = read("operational-context-maintenance/systems-yml-update-prompt.md");

        assertFalse(prompt.contains("references:\n      repositories:"));
        assertTrue(prompt.contains("Systems do not reference repositories directly."));
        assertTrue(prompt.contains("code-search-scopes.yml"));
    }

    @Test
    void shouldDescribeCodeSearchScopeAsTheCanonicalPathFromSystemToCode() throws IOException {
        var fillOrder = read("operational-context-maintenance/operational-context-fill-order.md");
        var scopePrompt = read("operational-context-maintenance/code-search-scopes-yml-update-prompt.md");

        assertTrue(fillOrder.contains("Do not list repositories on a system."));
        assertTrue(fillOrder.contains("Repository navigation for a system goes"));
        assertTrue(scopePrompt.contains("This file is the canonical bridge between semantic context and code"));
        assertTrue(scopePrompt.contains("bounded context -> optional code-search scope -> repository -> path prefix -> code"));
    }

    @Test
    void shouldLinkEveryMaintenancePromptToCanonicalFieldGuidance() throws IOException {
        var prompts = List.of(
                "systems-yml-update-prompt.md",
                "repo-map-yml-update-prompt.md",
                "code-search-scopes-yml-update-prompt.md",
                "processes-yml-update-prompt.md",
                "integrations-yml-update-prompt.md",
                "bounded-contexts-yml-update-prompt.md",
                "teams-yml-update-prompt.md",
                "glossary-yml-update-prompt.md",
                "handoff-rules-yml-update-prompt.md"
        );

        for (var prompt : prompts) {
            assertTrue(
                    read("operational-context-maintenance/" + prompt)
                            .contains("operational-context-field-guidance.md"),
                    () -> prompt + " must reference canonical field guidance"
            );
        }
    }

    @Test
    void shouldDocumentStrictCodeSearchRulesAndAnonymizedCrmExamples() throws IOException {
        var guidance = read("operational-context-maintenance/operational-context-field-guidance.md");

        assertTrue(guidance.contains("`target.type` is exactly `system` or `bounded-context`"));
        assertTrue(guidance.contains("`whole-repository` or `path-prefixes`"));
        assertTrue(guidance.contains("positive `priority`"));
        assertTrue(guidance.contains("crm-contact-core"));
        assertTrue(guidance.contains("crm-contact-repository"));
        assertTrue(guidance.contains("lifecycleStatus"));
        assertTrue(guidance.contains("Runtime / AI effect"));
    }

    @Test
    void shouldDocumentStructuredMaintenanceControlsWithoutChangingCanonicalShapes() throws IOException {
        var guidance = read("operational-context-maintenance/operational-context-field-guidance.md");
        var codeSearchPrompt = read("operational-context-maintenance/code-search-scopes-yml-update-prompt.md");
        var integrationsPrompt = read("operational-context-maintenance/integrations-yml-update-prompt.md");

        assertTrue(guidance.contains("ownership uses team, status and confidence selectors"));
        assertTrue(guidance.contains("references use per-entity-type catalogue pickers"));
        assertTrue(guidance.contains("`scopeType` is derived from `target.type`"));
        assertTrue(guidance.contains("integration participants use source/target/intermediary/final-target cards"));
        assertTrue(guidance.contains("preserve unknown extensions"));
        assertTrue(codeSearchPrompt.contains("The UI derives `scopeType`"));
        assertTrue(integrationsPrompt.contains("structured participant cards"));
    }

    @Test
    void shouldDocumentGuidedAnonymousCrmGitAndProcessRuntimeControls() throws IOException {
        var guidance = read("operational-context-maintenance/operational-context-field-guidance.md");
        var repositoryPrompt = read("operational-context-maintenance/repo-map-yml-update-prompt.md");
        var processPrompt = read("operational-context-maintenance/processes-yml-update-prompt.md");

        assertTrue(guidance.contains("repository Git identity uses explicit"));
        assertTrue(guidance.contains("`projectPath` is the canonical provider-relative lookup identity"));
        assertTrue(guidance.contains("process participants use actor lines and role-specific system pickers"));
        assertTrue(guidance.contains("matchSignals.strong.terms"));
        assertTrue(guidance.contains("crm-contact-core"));
        assertTrue(repositoryPrompt.contains("through guided\nfields rather than raw JSON"));
        assertTrue(processPrompt.contains("canonical `references` object"));
    }

    @Test
    void shouldDocumentGuidedSignalsRelationsAndPreserveOnlyParticipantRepositories() throws IOException {
        var guidance = read("operational-context-maintenance/operational-context-field-guidance.md");
        var integrationPrompt = read("operational-context-maintenance/integrations-yml-update-prompt.md");

        assertTrue(guidance.contains("match signals use repeatable confidence/key/value rows"));
        assertTrue(guidance.contains("relations use repeatable semantic-edge cards"));
        assertTrue(guidance.contains("Match-signal buckets are `exact`, `strong`, `medium` or `weak`"));
        assertTrue(guidance.contains("canonical `targetType` plus `target`"));
        assertTrue(guidance.contains("Participant-level `repositories` from"));
        assertTrue(integrationPrompt.contains("preserve-only"));
        assertTrue(integrationPrompt.contains("code-search scopes"));
    }

    @Test
    void shouldDocumentGuidedAnonymousCrmFailuresArtifactsCoverageAndGaps() throws IOException {
        var guidance = read("operational-context-maintenance/operational-context-field-guidance.md");
        var processPrompt = read("operational-context-maintenance/processes-yml-update-prompt.md");
        var integrationPrompt = read("operational-context-maintenance/integrations-yml-update-prompt.md");

        assertTrue(guidance.contains("The UI writes one object with `status`, `scannedSources`, `expectedSources` and"));
        assertTrue(guidance.contains("Every card requires an actionable `summary`"));
        assertTrue(guidance.contains("`primaryObjects`, `inputArtifacts`, `outputArtifacts`, `persistedEntities`"));
        assertTrue(guidance.contains("CRM failure-mode example"));
        assertTrue(guidance.contains("crm-contact-rejected"));
        assertTrue(processPrompt.contains("crm-request-validation-failed"));
        assertTrue(processPrompt.contains("never CRM customer records or payloads"));
        assertTrue(integrationPrompt.contains("CRM case handoff not visible"));
        assertTrue(integrationPrompt.contains("never confirm root cause"));
    }

    @Test
    void shouldDocumentGuidedAnonymousCrmProcessBoundaryLifecycleAndCompletionSignals() throws IOException {
        var guidance = read("operational-context-maintenance/operational-context-field-guidance.md");
        var processPrompt = read("operational-context-maintenance/processes-yml-update-prompt.md");

        assertTrue(guidance.contains("`processBoundary`, `lifecycle` and `completionSignals` have separate jobs"));
        assertTrue(guidance.contains("Legacy non-blank string/list values remain readable as `endsWhen`"));
        assertTrue(guidance.contains("Legacy non-blank string/list values remain readable as `statuses`"));
        assertTrue(guidance.contains("CRM process-boundary example"));
        assertTrue(guidance.contains("CRM lifecycle example"));
        assertTrue(guidance.contains("CRM completion-signal example"));
        assertTrue(processPrompt.contains("Guided CRM process semantics"));
        assertTrue(processPrompt.contains("CRM Contact Preference Management"));
        assertTrue(processPrompt.contains("Lifecycle is descriptive operational context, not workflow configuration"));
        assertTrue(processPrompt.contains("completion signals to `successful`"));
    }

    @Test
    void shouldDocumentGuidedAnonymousCrmSystemRuntimeAndRepositoryExplorationFields() throws IOException {
        var guidance = read("operational-context-maintenance/operational-context-field-guidance.md");
        var systemPrompt = read("operational-context-maintenance/systems-yml-update-prompt.md");
        var repositoryPrompt = read("operational-context-maintenance/repo-map-yml-update-prompt.md");

        assertTrue(guidance.contains("system external responsibility and runtime configuration use guided"));
        assertTrue(guidance.contains("repository provenance uses repeatable `sourceRef`/`evidenceType`/`note`"));
        assertTrue(guidance.contains("Service, deployment and application identities belong in `matchSignals`"));
        assertTrue(guidance.contains("Discovery phrases are searchable repository signals"));
        assertTrue(systemPrompt.contains("configurationDirectory: crm/contact-service"));
        assertTrue(systemPrompt.contains("CRM managed\nplatform provider"));
        assertTrue(repositoryPrompt.contains("evidenceType: build-definition"));
        assertTrue(repositoryPrompt.contains("answerWhenUserMentions"));
        assertFalse(systemPrompt.contains("customer-portal"));
        assertFalse(repositoryPrompt.contains("customer-portal"));
    }

    @Test
    void shouldDocumentGuidedAnonymousCrmBoundedContextSemantics() throws IOException {
        var guidance = read("operational-context-maintenance/operational-context-field-guidance.md");
        var boundedContextPrompt = read("operational-context-maintenance/bounded-contexts-yml-update-prompt.md");

        assertTrue(guidance.contains("No supported canonical field requires a raw JSON input"));
        assertTrue(guidance.contains("`scope.businessCapabilities`"));
        assertTrue(guidance.contains("`semanticBoundary.invariants`"));
        assertTrue(guidance.contains("`llmToolHints.usefulSearchKeywords`"));
        assertTrue(boundedContextPrompt.contains("name: CRM Contact Preferences"));
        assertTrue(boundedContextPrompt.contains("localLanguageSummary:"));
        assertTrue(boundedContextPrompt.contains("semanticBoundary:"));
        assertTrue(boundedContextPrompt.contains("evidenceType: domain-note"));
        assertTrue(boundedContextPrompt.contains("No supported bounded-context\n  field requires raw JSON"));
        assertFalse(boundedContextPrompt.contains("customer-requests"));
        assertFalse(boundedContextPrompt.contains("customer-portal"));
    }

    private static String read(String path) throws IOException {
        return Files.readString(Path.of(path)).replace("\r\n", "\n");
    }
}
