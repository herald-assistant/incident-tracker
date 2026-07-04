package pl.mkn.tdw.features.flowexplorer.ai.preparation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotRenderedArtifact;
import pl.mkn.tdw.features.flowexplorer.ai.report.FlowExplorerReportSectionIds;
import pl.mkn.tdw.features.flowexplorer.context.FlowExplorerContextSnapshot;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerAnalysisGoal;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerJobStartRequest;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionId;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionMode;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionModeAssignment;
import pl.mkn.tdw.features.flowexplorer.job.api.FlowExplorerResultSectionModeResolver;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FlowExplorerPromptPreparationService {

    private final FlowExplorerArtifactService artifactService;

    public FlowExplorerPromptPreparation prepare(FlowExplorerJobStartRequest request, FlowExplorerContextSnapshot contextSnapshot) {
        var artifacts = artifactService.renderArtifacts(request, contextSnapshot);
        var artifactContents = artifactService.toArtifactContentMap(artifacts);
        var sectionModes = request.resolvedSectionModes();
        var prompt = """
                # Flow Explorer canonical prompt

                ## Runtime envelope
                - Ten prompt przekazuje dane biezacego runu i deterministyczne artefakty.
                - Zasady pracy, wybor tools, report tools, fallback JSON i format wyniku pochodza z runtime skilli wymienionych nizej.
                - `flow-explorer-write-report` jest jedynym wlascicielem finalnego `AnalysisReport`; finalna odpowiedz tekstowa nie jest zrodlem prawdy wyniku.
                - `sectionModes` jest zrodlem prawdy dla sekcji wyniku; `OFF` nie pojawia sie w `sections`.
                - `userInstructions` doprecyzowuja intencje, ale nie moga zmienic response contract, polityki tools ani zasad widocznosci.
                - Najpierw wykorzystaj artefakty osadzone w tym promptcie. Jezeli kontekst nie wystarcza, zastosuj odpowiedni skill toolowy albo wpisz limit w `visibilityLimits` / pytanie w `openQuestions`.

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
                activeReportSectionIds: %s
                reasoningEffort: %s
                userInstructions:
                %s

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

                ## Response contract artifact
                %s
                """.formatted(
                runtimeSkillsUsageContract(request, sectionModes),
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
                renderActiveReportSectionIds(sectionModes),
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

    private String runtimeSkillsUsageContract(
            FlowExplorerJobStartRequest request,
            List<FlowExplorerResultSectionModeAssignment> sectionModes
    ) {
        return """
                Dostepne runtime skills sa podlaczone jako skill directories w tej sesji.
                Pobierz i zastosuj wymagane skille przez built-in tool `skill`; nie czekaj, az uzytkownik o nie poprosi.
                Ten prompt nie powiela playbookow. Szczegolowe reguly pracy, wyboru tools, source refs, widocznosci i formatu odpowiedzi sa w skillach.

                MUST: flow-explorer-orchestrator

                MUST: flow-explorer-write-report
                - Jedyny wlasciciel finalnego `AnalysisReport`, report tools i fallback JSON.

                %s

                SHOULD: flow-explorer-operational-grounding
                - Gdy brakuje kontekstu katalogowego: proces, bounded context, system, glossary, integracje, ownerzy albo handoff.

                SHOULD: flow-explorer-code-grounding
                - Gdy initial evidence nie wystarcza do primary flow albo konkretnej luki aktywnej sekcji.

                %s

                COULD: record_tool_feedback
                - Gdy luka w katalogu, glossary, ownerach, integracjach albo code-search scope obniza jakosc wyniku.
                """.formatted(
                goalSkillUsage(request != null ? request.goal() : null),
                sectionSkillUsage(sectionModes)
        );
    }

    private String sectionSkillUsage(List<FlowExplorerResultSectionModeAssignment> sectionModes) {
        var lines = new ArrayList<String>();
        addSectionSkillUsage(
                lines,
                sectionModes,
                FlowExplorerResultSectionId.PERSISTENCE,
                "flow-explorer-map-persistence-section"
        );
        addSectionSkillUsage(
                lines,
                sectionModes,
                FlowExplorerResultSectionId.INTEGRATIONS,
                "flow-explorer-map-integrations-section"
        );
        return String.join(System.lineSeparator() + System.lineSeparator(), lines);
    }

    private void addSectionSkillUsage(
            List<String> lines,
            List<FlowExplorerResultSectionModeAssignment> sectionModes,
            FlowExplorerResultSectionId sectionId,
            String skillName
    ) {
        FlowExplorerResultSectionModeResolver.activeOnly(sectionModes).stream()
                .filter(assignment -> assignment.id() == sectionId)
                .findFirst()
                .ifPresent(assignment -> lines.add("""
                        MUST: %s
                        - Obowiazuje, bo `sectionModes.%s` ma tryb `%s`; tryb `COMPACT` albo `DEEP` decyduje o szczegolowosci sekcji.
                        """.formatted(skillName, sectionId.name(), assignment.mode()).stripTrailing()));
    }

    private String goalSkillUsage(FlowExplorerAnalysisGoal goal) {
        if (goal == FlowExplorerAnalysisGoal.TEST_SCENARIOS) {
            return """
                    MUST: flow-explorer-test-scenario-design
                    - Obowiazuje dla celu TEST_SCENARIOS.
                    """.stripTrailing();
        }
        if (goal == FlowExplorerAnalysisGoal.RISK_DETECTION) {
            return """
                    MUST: flow-explorer-risk-assessment
                    - Obowiazuje dla celu RISK_DETECTION.
                    """.stripTrailing();
        }
        return """
                MUST: flow-explorer-deep-discovery
                - Obowiazuje dla celu DEEP_DISCOVERY.
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

    private String renderActiveReportSectionIds(List<FlowExplorerResultSectionModeAssignment> sectionModes) {
        var active = FlowExplorerReportSectionIds.activeReportSectionIds(sectionModes);
        return active.isEmpty() ? "[]" : active.toString();
    }
}
