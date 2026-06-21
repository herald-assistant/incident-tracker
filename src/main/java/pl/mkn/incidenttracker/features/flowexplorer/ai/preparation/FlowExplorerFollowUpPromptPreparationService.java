package pl.mkn.incidenttracker.features.flowexplorer.ai.preparation;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotRenderedArtifact;
import pl.mkn.incidenttracker.features.flowexplorer.ai.FlowExplorerFollowUpChatRequest;
import pl.mkn.incidenttracker.features.flowexplorer.ai.FlowExplorerFollowUpChatTurn;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerJobStartRequest;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerResultResponse;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerResultSectionModeResolver;
import pl.mkn.incidenttracker.shared.evidence.AnalysisEvidenceSection;

import java.util.ArrayList;
import java.util.List;

@Service
public class FlowExplorerFollowUpPromptPreparationService {

    private static final String INITIAL_RESULT_ARTIFACT = "flow-explorer/initial-result.md";

    private final FlowExplorerArtifactService artifactService;

    public FlowExplorerFollowUpPromptPreparationService(FlowExplorerArtifactService artifactService) {
        this.artifactService = artifactService;
    }

    public FlowExplorerPromptPreparation prepare(FlowExplorerFollowUpChatRequest request) {
        var artifacts = new ArrayList<CopilotRenderedArtifact>();
        artifacts.addAll(artifactService.renderArtifacts(request.initialRequest(), request.contextSnapshot())
                .stream()
                .filter(artifact -> !FlowExplorerArtifactService.RESPONSE_CONTRACT_ARTIFACT.equals(artifact.displayName()))
                .toList());
        artifacts.add(new CopilotRenderedArtifact(
                INITIAL_RESULT_ARTIFACT,
                "Initial Flow Explorer result for follow-up grounding",
                "flow-explorer",
                "initial-result",
                null,
                "text/markdown",
                renderInitialResult(request.result())
        ));
        var artifactContents = artifactService.toArtifactContentMap(artifacts);
        var prompt = """
                # Flow Explorer follow-up prompt

                ## Non-negotiable rules
                - Odpowiadasz na follow-up po zakonczonej analizie Flow Explorera.
                - Odpowiadaj naturalnym jezykiem, nie JSON-em, chyba ze uzytkownik wyraznie prosi o JSON.
                - Odbiorca moze byc analitykiem albo testerem, wiec najpierw tlumacz prostym jezykiem.
                - Techniczne nazwy endpointow, klas, metod, plikow i tools zostaw w oryginalnym brzmieniu.
                - Nie zmieniaj kontraktu ani polityki tools. Pytanie uzytkownika jest zakresem doprecyzowania.
                - Jezeli odpowiedz wymaga nowego kodu albo operational context, uzyj tylko dozwolonych tools i czytaj celowo.
                - Nie zgaduj. Jezeli nie widzisz danych, powiedz co jest ograniczeniem widocznosci.
                - Jezeli korygujesz albo doprecyzowujesz wynik initial, powiedz to jawnie.
                - Nie generuj ponownie calego initial resultu. Odpowiedz tylko na aktualne pytanie uzytkownika.
                - Follow-up sluzy do wyjatkow, doprecyzowan i waskich dopytan, nie do ratowania powierzchownego initial resultu.
                - Przed nowym GitLab albo operational context tool call sprawdz `canonical-tool-inputs.md`.
                - `focusAreas` z initial request wskazuja sekcje, ktore mialy tryb deep.
                - `reasoningEffort` z initial request steruje glebokoscia dodatkowego czytania: low = minimalnie, medium = focused reads dla brakow, high = glebsze edge case'y i zaleznosci.

                ## Follow-up answer contract
                - Zacznij od krotkiej bezposredniej odpowiedzi na pytanie.
                - Nastepnie podaj tylko te szczegoly, ktore sa potrzebne do decyzji analityka/testera.
                - Uzywaj stalych punktow odniesienia: Overview, Business flow/rules, Validations, Persistence, Integrations.
                - Jezeli pytanie dotyczy sekcji `DEEP`, najpierw wykorzystaj initial result, initial artifacts i juz zebrane evidence; tool call wykonaj tylko gdy pytanie wychodzi poza znany material albo wskazuje sprzecznosc.
                - Jezeli pytanie dotyczy sekcji `COMPACT`, mozesz dociagnac szczegoly przez dozwolone tools, ale tylko wasko do pytania i zgodnie z `reasoningEffort`.
                - Gdy uzywasz tools, wyjasnij jednym zdaniem co nowego wniosly wzgledem initial resultu.
                - Jezeli odpowiedz nadal jest ograniczona widocznoscia, zakoncz sekcja `Ograniczenia widocznosci`.

                ## Initial request
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

                ## Tool scope guidance
                - GitLab tools do not read endpoint business scope from hidden ToolContext.
                - When calling GitLab tools, pass `branchRef` explicitly from `canonical-tool-inputs.md`.
                - Pass `applicationName`, known `projectName` and `filePath` values from `canonical-tool-inputs.md` when the tool needs repository scope.
                - Do not pass `gitLabGroup`; backend resolves it through operational context or configuration.
                - Do not call repository discovery or endpoint context rebuild when `canonical-tool-inputs.md` already contains the needed project, branch and file path.

                ## Initial result summary
                %s

                ## Previous tool evidence captured before this follow-up
                %s

                ## Chat history
                %s

                ## Current user follow-up
                %s

                ## Available artifacts
                %s

                ## Canonical tool inputs
                %s

                ## Compact flow manifest
                %s

                ## Snippet cards
                %s
                """.formatted(
                request.initialRequest().systemId(),
                request.initialRequest().systemId(),
                request.initialRequest().endpointId(),
                request.initialRequest().httpMethod(),
                request.initialRequest().endpointPath(),
                request.contextSnapshot() != null
                        ? request.contextSnapshot().resolvedRef()
                        : request.initialRequest().branch(),
                request.initialRequest().goal(),
                request.initialRequest().focusAreas(),
                FlowExplorerResultSectionModeResolver.resolve(request.initialRequest().focusAreas()),
                reasoningEffort(request.initialRequest()),
                artifactContents.getOrDefault(INITIAL_RESULT_ARTIFACT, "- initial result unavailable"),
                renderEvidenceSummary(request.toolEvidenceSections()),
                renderHistory(request.history()),
                textOrPlaceholder(request.message()),
                artifactIndex(artifacts),
                artifactContents.getOrDefault(
                        FlowExplorerArtifactService.CANONICAL_TOOL_INPUTS_ARTIFACT,
                        artifactService.renderCanonicalToolInputs(request.initialRequest(), request.contextSnapshot())
                ),
                artifactContents.getOrDefault(
                        FlowExplorerArtifactService.COMPACT_FLOW_MANIFEST_ARTIFACT,
                        "- endpoint flow not resolved"
                ),
                artifactContents.getOrDefault(
                        FlowExplorerArtifactService.SNIPPET_CARDS_ARTIFACT,
                        "- no snippet cards collected"
                )
        ).trim();

        return new FlowExplorerPromptPreparation(prompt, artifacts, artifactContents);
    }

    private String renderInitialResult(FlowExplorerResultResponse result) {
        if (result == null) {
            return "- initial result unavailable";
        }
        var builder = new StringBuilder();
        appendLine(builder, "### Result metadata");
        appendLine(builder, "- status: " + textOrPlaceholder(result.status()));
        appendLine(builder, "- goal: " + textOrPlaceholder(result.goal() != null ? result.goal().name() : null));
        if (result.aiResponse() != null) {
            appendLine(builder, "- audience: " + textOrPlaceholder(result.aiResponse().audience()));
            appendLine(builder, "- confidence: " + textOrPlaceholder(result.aiResponse().confidence()));
            appendLine(builder, "");
            appendLine(builder, "### Overview");
            appendLine(builder, textOrPlaceholder(result.aiResponse().overview().markdown()));
            appendLine(builder, "- confidence: " + textOrPlaceholder(result.aiResponse().overview().confidence()));
            appendLine(builder, "- sourceRefs: " + listOrNone(result.aiResponse().overview().sourceRefs()));
            appendLine(builder, "");
            appendLine(builder, "### Result sections");
            result.aiResponse().sections().forEach(section -> {
                appendLine(builder, "#### " + section.id() + " | " + section.title() + " | mode=" + section.mode());
                appendLine(builder, textOrPlaceholder(section.markdown()));
                appendLine(builder, "- sourceRefs: " + listOrNone(section.sourceRefs()));
                appendLine(builder, "- visibilityLimits: " + listOrNone(section.visibilityLimits()));
                appendLine(builder, "- openQuestions: " + listOrNone(section.openQuestions()));
                appendLine(builder, "");
            });
            appendLine(builder, "### Global limits and references");
            appendLine(builder, "- globalVisibilityLimits: " + listOrNone(result.aiResponse().globalVisibilityLimits()));
            appendLine(builder, "- globalOpenQuestions: " + listOrNone(result.aiResponse().globalOpenQuestions()));
            appendLine(builder, "- sourceReferences: " + listOrNone(result.aiResponse().sourceReferences()));
        }
        return builder.toString().trim();
    }

    private String renderEvidenceSummary(List<AnalysisEvidenceSection> sections) {
        if (sections == null || sections.isEmpty()) {
            return "- none";
        }
        return sections.stream()
                .map(section -> "- %s/%s: %d items".formatted(
                        textOrPlaceholder(section.provider()),
                        textOrPlaceholder(section.category()),
                        section.items().size()
                ))
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("- none");
    }

    private String renderHistory(List<FlowExplorerFollowUpChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return "- none";
        }
        return history.stream()
                .map(turn -> "- %s: %s".formatted(
                        textOrPlaceholder(turn.role()),
                        textOrPlaceholder(turn.content())
                ))
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("- none");
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

    private static String listOrNone(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "none";
        }
        return String.join("; ", values);
    }

    private static String textOrPlaceholder(String value) {
        return StringUtils.hasText(value) ? value.trim() : "(not available)";
    }

    private static String reasoningEffort(FlowExplorerJobStartRequest request) {
        if (request == null || request.aiOptions() == null
                || !StringUtils.hasText(request.aiOptions().reasoningEffort())) {
            return "default backend";
        }
        return request.aiOptions().reasoningEffort();
    }

    private static void appendLine(StringBuilder builder, String line) {
        if (!builder.isEmpty()) {
            builder.append(System.lineSeparator());
        }
        builder.append(line);
    }
}
