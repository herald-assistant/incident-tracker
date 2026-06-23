package pl.mkn.incidenttracker.features.flowexplorer.ai.preparation;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotRenderedArtifact;
import pl.mkn.incidenttracker.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerAnalysisGoal;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerJobStartRequest;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerResultSectionMode;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerResultSectionModeAssignment;
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
        var sectionModes = request.resolvedSectionModes();
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
                - Wynik ma zawsze zawierac `overview` oraz tylko aktywne sekcje z `sectionModes`.
                - `sectionModes` jest zrodlem prawdy dla sekcji wyniku: `OFF` oznacza, ze sekcji nie wolno zwracac w `sections`; `COMPACT` i `DEEP` oznaczaja, ze sekcja musi byc zwrocona.
                - Dla aktywnych sekcji ustaw `mode` zgodnie z `sectionModes`: `deep` oznacza bardziej szczegolowa odpowiedz, `compact` oznacza zwarta, ale nadal konkretna odpowiedz. Nigdy nie zwracaj `mode=off`.
                - Najpierw wykorzystaj `compact-flow-manifest.md`, `snippet-cards.md` i, jezeli jest dostepny, `openapi-endpoint-contract.md`; to jest initial evidence przygotowane deterministycznie.
                - Nie powtarzaj GitLab tool calls dla kodu, ktory jest juz widoczny w `snippet-cards.md`.
                - Jezeli `openapi-endpoint-contract.md` jest dostepny, uzyj go jako kontraktu API request/response/parameters/security dla wybranego endpointu. Nie czytaj pelnego OpenAPI YAML.
                - Zawsze zrozum primary endpoint flow jako podstawe overview. `focusAreas` to kompatybilny skrot dla sekcji `DEEP`; o zwracanych sekcjach decyduje `sectionModes`.
                - Glebokosc eksploracji wynika z `reasoningEffort`: low = artifact-first i minimalne tool calls, medium = focused reads dla brakow primary flow, high = glebsze edge case'y i zaleznosci, nadal przez canonical inputs.
                - Pelniejsze czytanie kodu wykonuj przez GitLab tools dopiero wtedy, gdy brakuje go do primary flow albo sekcji deep zgodnie z reasoningEffort; preferuj `gitlab_read_java_method_slice` dla konkretnych metod.
                - Przed kazdym GitLab albo operational context tool call sprawdz `canonical-tool-inputs.md` oraz `compact-flow-manifest.md`: scope repo/branch bierz z canonical, a filePath/metody z manifestu.
                - `context-snapshot.json` jest manifestem bez pelnego kodu snippetow; pelny kod jest w `snippet-cards.md`.
                - Artefakty sa logicznym payloadem sesji, a kluczowe tresci sa osadzone inline ponizej.

                ## Runtime skills usage contract
                %s

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
                activeSectionIds: %s
                reasoningEffort: %s
                userInstructions:
                %s

                ## Tool scope guidance
                - GitLab tools do not read endpoint business scope from hidden ToolContext.
                - When calling GitLab tools, pass `branchRef` explicitly from `canonical-tool-inputs.md`.
                - Pass `applicationName`, known `projectName` and `branchRef` values from `canonical-tool-inputs.md`.
                - Pass `filePath` and method selectors from `compact-flow-manifest.md` or `openapi-endpoint-contract.md` when the tool needs code scope.
                - Do not pass `gitLabGroup`; backend resolves it through operational context or configuration.
                - Do not call repository discovery or endpoint context rebuild when `canonical-tool-inputs.md` and `compact-flow-manifest.md` already contain the needed project, branch and flow file paths.

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
                runtimeSkillsUsageContract(request),
                request.systemId(),
                request.systemId(),
                request.endpointId(),
                request.httpMethod(),
                request.endpointPath(),
                contextSnapshot != null ? contextSnapshot.resolvedRef() : request.branch(),
                request.goal(),
                request.focusAreas(),
                renderSectionModes(sectionModes),
                renderActiveSectionIds(sectionModes),
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

    private String runtimeSkillsUsageContract(FlowExplorerJobStartRequest request) {
        return """
                Dostepne runtime skills sa podlaczone jako skill directories w tej sesji.
                Jezeli potrzebujesz pelnego playbooka, uzyj built-in tool `skill` dla nazwy skillu samodzielnie; nie czekaj, az uzytkownik poprosi o skille.
                Priorytety: MUST = pobierz przez `skill` i zastosuj przed finalna odpowiedzia; SHOULD = pobierz i zastosuj, gdy spelniona jest opisana sytuacja; COULD = opcjonalnie, gdy poprawi jakosc albo jawnie nazwie ograniczenie.

                MUST: flow-explorer-orchestrator
                - Uzyj przed interpretacja artefaktow, zeby utrzymac primary endpoint flow, goal, sectionModes, focusAreas i reasoningEffort.
                - Pilnuje, ze wynik jest dokumentacja przeplywu systemowego, a kod jest evidence, nie jezykiem glownej narracji.

                MUST: flow-explorer-result-contract
                - Uzyj przed finalna odpowiedzia initial run.
                - Pilnuje JSON-only, overview, tylko aktywnych sekcji z sectionModes, sourceRefs, confidence, visibilityLimits i openQuestions.

                %s

                SHOULD: flow-explorer-operational-context-tools
                - Uzyj, gdy trzeba nazwac proces, bounded context, system, integracje, ownerow, glossary albo handoff jezykiem biznesowo-systemowym.
                - Uzyj szczegolnie wtedy, gdy odpowiedz zaczyna brzmiec jak opis implementacji zamiast opisu zachowania systemu.
                - Jezeli operational context ma luke, wpisz ograniczenie i zglos ja przez `record_tool_feedback`.

                SHOULD: flow-explorer-gitlab-tools
                - Uzyj, gdy initial evidence nie wystarcza do primary flow albo sekcji w trybie `deep`.
                - Preferuj `gitlab_read_java_method_slice` dla konkretnej metody, a pelny `gitlab_read_repository_file` tylko gdy slice/outline nie wystarcza.
                - Nie powtarzaj odczytow kodu, ktory jest juz w `snippet-cards.md`.

                COULD: record_tool_feedback
                - Uzyj dla brakow katalogu operational context, glossary, ownerow, integracji albo code-search scope, jezeli luka obniza jakosc biznesowa wyniku.

                COULD: flow-explorer-gitlab-tools + flow-explorer-operational-context-tools
                - Uzyj razem tylko wtedy, gdy laczenie kodu i operational context realnie zmieni odpowiedz albo widocznosc ograniczen.
                """.formatted(goalSkillUsage(request != null ? request.goal() : null));
    }

    private String goalSkillUsage(FlowExplorerAnalysisGoal goal) {
        if (goal == FlowExplorerAnalysisGoal.TEST_SCENARIOS) {
            return """
                    MUST: flow-explorer-goal-test-scenarios
                    - Uzyj przed wypelnieniem sekcji merytorycznych dla celu TEST_SCENARIOS.
                    - Szukaj regul, walidacji, stanow danych, integracji i wariantow, ktore daja konkretne scenariusze testowe bez przepisywania implementacji.
                    """.stripTrailing();
        }
        if (goal == FlowExplorerAnalysisGoal.RISK_DETECTION) {
            return """
                    MUST: flow-explorer-goal-risk-detection
                    - Uzyj przed wypelnieniem sekcji merytorycznych dla celu RISK_DETECTION.
                    - Szukaj ryzyk regresji, ukrytych zaleznosci, efektow ubocznych, integracji i ograniczen widocznosci.
                    """.stripTrailing();
        }
        return """
                MUST: flow-explorer-goal-deep-discovery
                - Uzyj przed wypelnieniem sekcji merytorycznych dla celu DEEP_DISCOVERY.
                - Szukaj pelnego przeplywu biznesowo-systemowego, reguly domenowej, walidacji, zapisu danych, handoffow i integracji.
                """.stripTrailing();
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

    private String renderSectionModes(List<FlowExplorerResultSectionModeAssignment> sectionModes) {
        if (sectionModes == null || sectionModes.isEmpty()) {
            return "[]";
        }
        return sectionModes.stream()
                .map(assignment -> "%s=%s".formatted(assignment.id(), assignment.mode()))
                .reduce((left, right) -> left + ", " + right)
                .orElse("[]");
    }

    private String renderActiveSectionIds(List<FlowExplorerResultSectionModeAssignment> sectionModes) {
        var active = FlowExplorerResultSectionModeResolver.activeOnly(sectionModes).stream()
                .filter(assignment -> assignment.mode() != FlowExplorerResultSectionMode.OFF)
                .map(assignment -> assignment.id().name())
                .toList();
        return active.isEmpty() ? "[]" : active.toString();
    }
}
