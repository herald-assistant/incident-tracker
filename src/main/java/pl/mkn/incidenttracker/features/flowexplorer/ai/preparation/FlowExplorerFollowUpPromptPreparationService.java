package pl.mkn.incidenttracker.features.flowexplorer.ai.preparation;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.aiplatform.copilot.runtime.CopilotRenderedArtifact;
import pl.mkn.incidenttracker.features.flowexplorer.ai.FlowExplorerFollowUpChatRequest;
import pl.mkn.incidenttracker.features.flowexplorer.ai.FlowExplorerFollowUpChatTurn;
import pl.mkn.incidenttracker.features.flowexplorer.job.api.FlowExplorerResultResponse;
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

                ## Initial request
                applicationName: %s
                systemId: %s
                endpointId: %s
                httpMethod: %s
                endpointPath: %s
                branchRef: %s
                documentationPreset: %s
                focusAreas: %s

                ## Tool scope guidance
                - GitLab tools do not read endpoint business scope from hidden ToolContext.
                - When calling GitLab tools, pass `branchRef` explicitly from this prompt or artifacts.
                - Pass `applicationName` and known `projectName` values when the tool needs repository scope.
                - Do not pass `gitLabGroup`; backend resolves it through operational context or configuration.

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
                request.initialRequest().documentationPreset(),
                request.initialRequest().focusAreas(),
                artifactContents.getOrDefault(INITIAL_RESULT_ARTIFACT, "- initial result unavailable"),
                renderEvidenceSummary(request.toolEvidenceSections()),
                renderHistory(request.history()),
                textOrPlaceholder(request.message()),
                artifactIndex(artifacts),
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
        appendLine(builder, "- status: " + textOrPlaceholder(result.status()));
        appendLine(builder, "- intent: " + textOrPlaceholder(result.userIntentSummary()));
        appendLine(builder, "- audience: " + textOrPlaceholder(result.audienceSummary()));
        appendLine(builder, "- confidence: " + textOrPlaceholder(result.confidence()));
        appendLine(builder, "- visibilityLimits: " + listOrNone(result.visibilityLimits()));
        if (result.aiResponse() != null) {
            appendLine(builder, "- endpoint purpose: " + (
                    result.aiResponse().endpointContract() != null
                            ? textOrPlaceholder(result.aiResponse().endpointContract().purpose())
                            : "(not available)"
            ));
            appendLine(builder, "- flowSteps: " + result.aiResponse().flowSteps().size());
            appendLine(builder, "- businessRules: " + listOrNone(result.aiResponse().businessRules()));
            appendLine(builder, "- validations: " + listOrNone(result.aiResponse().validations()));
            appendLine(builder, "- persistence: " + listOrNone(result.aiResponse().persistence()));
            appendLine(builder, "- externalIntegrations: " + listOrNone(result.aiResponse().externalIntegrations()));
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

    private static void appendLine(StringBuilder builder, String line) {
        if (!builder.isEmpty()) {
            builder.append(System.lineSeparator());
        }
        builder.append(line);
    }
}
