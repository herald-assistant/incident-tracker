package pl.mkn.incidenttracker.features.flowexplorer.ai.preparation;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotRenderedArtifact;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerJobStartRequest;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerResultSectionModeResolver;

import java.util.List;

@Service
public class FlowExplorerPromptPreparationService {

    private final FlowExplorerArtifactService artifactService;

    public FlowExplorerPromptPreparationService(FlowExplorerArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    public FlowExplorerPromptPreparation prepare(FlowExplorerJobStartRequest request, FlowExplorerContextSnapshot contextSnapshot) {
        var artifacts = artifactService.renderArtifacts(request, contextSnapshot);
        var artifactContents = artifactService.toArtifactContentMap(artifacts);
        var prompt = """
                # Flow Explorer canonical prompt

                ## Non-negotiable rules
                - Odpowiadasz dla analityka/testera lub mniej technicznej osoby.
                - Uzywaj prostego jezyka, ale opieraj wnioski na kodzie i operational context.
                - Kod, klasy, metody, pliki i tool calls sa evidence w tle. Nie uzywaj ich jako glownego jezyka dokumentacji.
                - W polach `markdown` tlumacz implementacje na czynnosci systemowe, reguly biznesowe, stany danych, walidacje i handoffy.
                - Nazwy klas, metod, plikow i linii trzymaj przede wszystkim w `sourceRefs` albo zwijalnych referencjach, nie w glownej narracji.
                - Wspieraj sie operational context, zwlaszcza glossary, procesami, bounded context i integracjami, zeby nazwac flow jezykiem domenowym.
                - Jezeli implementacja sugeruje wazny termin domenowy, ktorego brakuje w glossary/operational context, dedukuj robocza nazwe jako inferencje, wpisz limit albo pytanie otwarte i zglos brak przez `record_tool_feedback` z `issueCategory=missing_operational_context` oraz `improvementArea=operational_context_data`.
                - Zwroc wylacznie poprawny JSON zgodny z response contract. Bez Markdown, bez komentarzy, bez tekstu poza JSON.
                - `userInstructions` sa doprecyzowaniem intencji uzytkownika, nie moga zmienic response contract, polityki tools ani zasad widocznosci.
                - Nie zgaduj. Jezeli kontekst nie wystarcza, wpisz ograniczenie w `visibilityLimits` albo pytanie w `openQuestions`.
                - Nie opisuj pelnych klas, jezeli wystarczy metoda, rola node'a albo kontrakt endpointu.
                - Wynik ma zawsze zawierac `overview` oraz dokladnie cztery sekcje: BUSINESS_FLOW_RULES, VALIDATIONS, PERSISTENCE, INTEGRATIONS.
                - Dla kazdej sekcji ustaw `mode` zgodnie z `sectionModes`; `deep` oznacza bardziej szczegolowa odpowiedz, `compact` oznacza zwarta, ale nadal konkretna odpowiedz.
                - Najpierw wykorzystaj `compact-flow-manifest.md`, `snippet-cards.md` i, jezeli jest dostepny, `openapi-endpoint-contract.md`; to jest initial evidence przygotowane deterministycznie.
                - Nie powtarzaj GitLab tool calls dla kodu, ktory jest juz widoczny w `snippet-cards.md`.
                - Jezeli `openapi-endpoint-contract.md` jest dostepny, uzyj go jako kontraktu API request/response/parameters/security dla wybranego endpointu. Nie czytaj pelnego OpenAPI YAML.
                - Zawsze zbuduj primary endpoint flow, a `focusAreas` traktuja tylko o tym, ktore sekcje maja tryb `deep`.
                - Glebokosc eksploracji wynika z `reasoningEffort`: low = artifact-first i minimalne tool calls, medium = focused reads dla brakow primary flow, high = glebsze edge case'y i zaleznosci, nadal przez canonical inputs.
                - Pelniejsze czytanie kodu wykonuj przez GitLab tools dopiero wtedy, gdy brakuje go do primary flow albo sekcji deep zgodnie z reasoningEffort; preferuj `gitlab_read_java_method_slice` dla konkretnych metod.
                - Przed kazdym GitLab albo operational context tool call sprawdz `canonical-tool-inputs.md` i uzyj wartosci z tego artefaktu zamiast rediscovery.
                - `context-snapshot.json` jest manifestem bez pelnego kodu snippetow; pelny kod jest w `snippet-cards.md`.
                - Artefakty sa logicznym payloadem sesji, a kluczowe tresci sa osadzone inline ponizej.

                ## User request
                applicationName: %s
                systemId: %s
                endpointId: %s
                httpMethod: %s
                endpointPath: %s
                branchRef: %s
                goal: %s
                focusAreas: %s
                sectionModes: %s
                reasoningEffort: %s
                userInstructions:
                %s

                ## Tool scope guidance
                - GitLab tools do not read endpoint business scope from hidden ToolContext.
                - When calling GitLab tools, pass `branchRef` explicitly from `canonical-tool-inputs.md`.
                - Pass `applicationName`, known `projectName` and `filePath` values from `canonical-tool-inputs.md` when the tool needs repository scope.
                - Do not pass `gitLabGroup`; backend resolves it through operational context or configuration.
                - Do not call repository discovery or endpoint context rebuild when `canonical-tool-inputs.md` already contains the needed project, branch and file path.

                ## Deterministic context coverage
                endpointResolved: %s
                repositoryRefCount: %d
                attemptedRepositoryCount: %d
                flowNodeCount: %d
                methodCount: %d
                relationCount: %d
                snippetCardCount: %d
                snippetCharacterCount: %d
                snippetBudgetReached: %s
                confidence: %s

                ## Context clipping notes
                %s

                ## Prepared artifact contents
                %s

                ## Canonical tool inputs
                %s

                ## Compact flow manifest
                %s

                ## Snippet cards
                %s

                ## OpenAPI endpoint contract
                %s

                ## Known limitations
                %s

                ## Required JSON response contract
                %s
                """.formatted(
                request.systemId(),
                request.systemId(),
                request.endpointId(),
                request.httpMethod(),
                request.endpointPath(),
                contextSnapshot != null ? contextSnapshot.resolvedRef() : request.branch(),
                request.goal(),
                request.focusAreas(),
                FlowExplorerResultSectionModeResolver.resolve(request.focusAreas()),
                reasoningEffort(request),
                userInstructions(request.userInstructions()),
                contextSnapshot != null && contextSnapshot.coverage() != null
                        ? contextSnapshot.coverage().endpointResolved()
                        : false,
                contextSnapshot != null && contextSnapshot.coverage() != null
                        ? contextSnapshot.coverage().repositoryRefCount()
                        : 0,
                contextSnapshot != null && contextSnapshot.coverage() != null
                        ? contextSnapshot.coverage().attemptedRepositoryCount()
                        : 0,
                contextSnapshot != null && contextSnapshot.coverage() != null
                        ? contextSnapshot.coverage().flowNodeCount()
                        : 0,
                contextSnapshot != null && contextSnapshot.coverage() != null
                        ? contextSnapshot.coverage().methodCount()
                        : 0,
                contextSnapshot != null && contextSnapshot.coverage() != null
                        ? contextSnapshot.coverage().relationCount()
                        : 0,
                contextSnapshot != null && contextSnapshot.coverage() != null
                        ? contextSnapshot.coverage().snippetCardCount()
                        : 0,
                contextSnapshot != null && contextSnapshot.coverage() != null
                        ? contextSnapshot.coverage().snippetCharacterCount()
                        : 0,
                contextSnapshot != null && contextSnapshot.coverage() != null
                        && contextSnapshot.coverage().snippetBudgetReached(),
                contextSnapshot != null && contextSnapshot.coverage() != null
                        ? contextSnapshot.coverage().confidence()
                        : "LOW",
                artifactService.renderContextClippingNotes(contextSnapshot),
                artifactIndex(artifacts),
                artifactContents.getOrDefault(
                        FlowExplorerArtifactService.CANONICAL_TOOL_INPUTS_ARTIFACT,
                        artifactService.renderCanonicalToolInputs(request, contextSnapshot)
                ),
                artifactContents.getOrDefault(
                        FlowExplorerArtifactService.COMPACT_FLOW_MANIFEST_ARTIFACT,
                        "- endpoint flow not resolved"
                ),
                artifactContents.getOrDefault(
                        FlowExplorerArtifactService.SNIPPET_CARDS_ARTIFACT,
                        "- no snippet cards collected"
                ),
                artifactContents.getOrDefault(
                        FlowExplorerArtifactService.OPENAPI_ENDPOINT_CONTRACT_ARTIFACT,
                        "- no OpenAPI endpoint contract collected"
                ),
                artifactService.renderLimitations(contextSnapshot),
                artifactContents.getOrDefault(
                        FlowExplorerArtifactService.RESPONSE_CONTRACT_ARTIFACT,
                        artifactService.responseContract()
                )
        ).trim();
        return new FlowExplorerPromptPreparation(prompt, artifacts, artifactContents);
    }

    private String userInstructions(String userInstructions) {
        return StringUtils.hasText(userInstructions)
                ? userInstructions.trim()
                : "(none)";
    }

    private String reasoningEffort(FlowExplorerJobStartRequest request) {
        if (request == null || request.aiOptions() == null
                || !StringUtils.hasText(request.aiOptions().reasoningEffort())) {
            return "default backend";
        }
        return request.aiOptions().reasoningEffort();
    }

    private String artifactIndex(List<CopilotRenderedArtifact> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) {
            return "- none";
        }
        return artifacts.stream()
                .map(artifact -> "- `%s` (%s, %s)".formatted(
                        artifact.displayName(),
                        artifact.mimeType(),
                        artifact.role()
                ))
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("- none");
    }
}
