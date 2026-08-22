package pl.mkn.tdw.features.uiexplorer.ai.preparation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import pl.mkn.tdw.aiplatform.copilot.runtime.CopilotArtifactContentMapper;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerReachabilityBoundary;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerScreenReachabilityContext;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerSectionContextCoverage;
import pl.mkn.tdw.features.uiexplorer.context.UiExplorerSourceScope;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerCoverageStatus;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerScreenIdentity;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionId;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSectionMode;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceReference;
import pl.mkn.tdw.features.uiexplorer.contract.UiExplorerSourceRevision;
import pl.mkn.tdw.features.uiexplorer.job.api.UiExplorerJobStartRequest;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendDiscoveryStatus;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendEffectiveRouteChain;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityComponent;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityComponentLevel;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityDependency;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityDependencyCategory;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityDependencyKind;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityEdge;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendReachabilityEdgeKind;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRepositoryScope;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteChainSegment;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteNode;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteNodeKind;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendRouteTarget;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenReachabilityGraph;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendSourceReference;
import pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendSourceRevision;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.util.StringUtils.countOccurrencesOf;

class UiExplorerInitialPromptMatrixTest {

    private static final int MAX_FIXED_PROMPT_OVERHEAD = 12_000;
    private static final Map<String, Integer> MAX_PROMPT_CHARACTERS = Map.of(
            "simple-screen", 14_500,
            "deep-routed-container", 16_500,
            "dynamic-form", 17_500,
            "reactive-access", 16_500
    );

    private final UiExplorerPromptPreparationService service = new UiExplorerPromptPreparationService(
            new UiExplorerArtifactService(new ObjectMapper()),
            new CopilotArtifactContentMapper()
    );

    @Test
    void shouldKeepEveryReachableCrmFactOnceInAReadablePromptAcrossTheMatrix() {
        var preparedCases = matrix().stream()
                .map(scenario -> new PreparedCase(
                        scenario,
                        service.prepare(scenario.request(), scenario.context())
                ))
                .toList();

        preparedCases.forEach(preparedCase -> {
            var scenario = preparedCase.scenario();
            var preparation = preparedCase.preparation();
            var prompt = preparation.prompt();

            assertThat(prompt).containsSubsequence(
                    "## 1. Analysis request and active sections",
                    "## 2. Selected screen and source revision",
                    "## 3. Effective route, component BFS and dependency map",
                    "## 4. Reachable source evidence",
                    "## 5. Coverage and targeted research queue",
                    "## 6. Functional documentation writing contract",
                    "## 7. Final response contract",
                    "## Final output rules"
            );
            scenario.expectedSourceMarkers().forEach(marker -> {
                assertThat(prompt).contains(marker);
                assertThat(countOccurrencesOf(prompt, marker))
                        .as("source marker %s in matrix case %s", marker, scenario.id())
                        .isEqualTo(1);
            });
            preparation.artifactContents().values().forEach(content ->
                    assertThat(countOccurrencesOf(prompt, content))
                            .as("logical artifact embedded once in matrix case %s", scenario.id())
                            .isEqualTo(1)
            );

            var artifactCharacters = preparation.artifacts().stream()
                    .mapToInt(artifact -> artifact.content().length())
                    .sum();
            assertThat(prompt.length() - artifactCharacters)
                    .as("fixed instructions and headings in matrix case %s", scenario.id())
                    .isPositive()
                    .isLessThan(MAX_FIXED_PROMPT_OVERHEAD);
            assertThat(prompt.length())
                    .as("reviewed initial prompt baseline in matrix case %s", scenario.id())
                    .isLessThanOrEqualTo(MAX_PROMPT_CHARACTERS.get(scenario.id()));
            assertThat(preparation.visibilityLimits()).containsExactlyElementsOf(
                    scenario.context().visibilityLimits()
            );
        });

        assertThat(promptLength(preparedCases, "simple-screen"))
                .isLessThan(promptLength(preparedCases, "deep-routed-container"));
        assertThat(promptLength(preparedCases, "simple-screen"))
                .isLessThan(promptLength(preparedCases, "dynamic-form"));
        assertThat(promptLength(preparedCases, "simple-screen"))
                .isLessThan(promptLength(preparedCases, "reactive-access"));
    }

    @Test
    void shouldExposeRoutingFormsTransportsStateAccessAndOnlyConfirmedRuntimeBoundary() {
        var prompts = matrix().stream()
                .collect(java.util.stream.Collectors.toMap(
                        MatrixScenario::id,
                        scenario -> service.prepare(scenario.request(), scenario.context()).prompt()
                ));

        assertThat(prompts.get("deep-routed-container"))
                .containsSubsequence("### BFS depth 0", "### BFS depth 1", "### BFS depth 2")
                .contains("/crm/customers/:customerId/workspace/activities");
        assertThat(prompts.get("dynamic-form"))
                .contains("CRM_DYNAMIC_FORM_MANUAL_OVERRIDE")
                .contains("CRM_DYNAMIC_FORM_VALIDATION")
                .contains("CRM_RUNTIME_SCHEMA_BOUNDARY")
                .contains("Runtime CRM form schema must be resolved from backend-provided data.")
                .contains("Exact runtime field catalogue is produced outside the approved frontend repository.");
        assertThat(prompts.get("reactive-access"))
                .contains("CrmAdvisorGuard")
                .contains("CRM_ROLE_ACCESS")
                .contains("CRM_REST_LOAD_CONTACT")
                .contains("CRM_WEBSOCKET_CONTACT_REFRESH")
                .contains("CRM_NGRX_CONTACT_STATE")
                .contains("kind: `BACKEND_CLIENT`")
                .contains("kind: `WEBSOCKET`")
                .contains("kind: `NGRX`");

        var recoverable = service.prepare(
                UiExplorerAiPreparationTestFixture.request(),
                UiExplorerAiPreparationTestFixture.context()
        );
        assertThat(recoverable.prompt())
                .contains("Runtime field definition requires targeted evidence.")
                .contains("\"visibilityLimits\":[]");
        assertThat(recoverable.visibilityLimits()).isEmpty();
    }

    private int promptLength(List<PreparedCase> preparedCases, String id) {
        return preparedCases.stream()
                .filter(preparedCase -> preparedCase.scenario().id().equals(id))
                .findFirst()
                .orElseThrow()
                .preparation()
                .prompt()
                .length();
    }

    private List<MatrixScenario> matrix() {
        return List.of(
                simpleScreen(),
                deepRoutedContainer(),
                dynamicForm(),
                reactiveAccess()
        );
    }

    private MatrixScenario simpleScreen() {
        var component = component(
                "component-crm-contact-summary",
                0,
                0,
                "CrmContactSummaryComponent",
                List.of(),
                List.of(),
                """
                        export class CrmContactSummaryComponent {
                          readonly CRM_SIMPLE_CONTACT_SUMMARY = 'Synthetic contact summary';
                        }
                        """
        );
        return scenario(
                "simple-screen",
                List.of("/crm/contacts/:contactId"),
                List.of(component),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("CRM_SIMPLE_CONTACT_SUMMARY")
        );
    }

    private MatrixScenario deepRoutedContainer() {
        var container = component(
                "component-crm-workspace",
                0,
                0,
                "CrmWorkspaceComponent",
                List.of(),
                List.of("component-crm-activity-panel"),
                """
                        export class CrmWorkspaceComponent {
                          readonly CRM_DEEP_CONTAINER_CONTEXT = 'Selected customer workspace';
                        }
                        """
        );
        var panel = component(
                "component-crm-activity-panel",
                1,
                1,
                "CrmActivityPanelComponent",
                List.of(),
                List.of("component-crm-activity-detail"),
                """
                        export class CrmActivityPanelComponent {
                          readonly CRM_DEEP_CONTAINER_CHILD_ACTION = 'Open activity details';
                        }
                        """
        );
        var detail = component(
                "component-crm-activity-detail",
                2,
                2,
                "CrmActivityDetailComponent",
                List.of("dependency-crm-activity-api"),
                List.of(),
                """
                        export class CrmActivityDetailComponent {
                          readonly selectedActivityId = input.required<string>();
                        }
                        """
        );
        var api = dependency(
                "dependency-crm-activity-api",
                0,
                GitLabFrontendReachabilityDependencyKind.BACKEND_CLIENT,
                GitLabFrontendReachabilityDependencyCategory.FUNCTIONAL,
                "CrmActivityApi",
                List.of("loadActivity"),
                List.of(detail.componentId()),
                List.of(),
                """
                        export class CrmActivityApi {
                          loadActivity(id: string) { return this.http.get('/synthetic-crm/activities/' + id); }
                        }
                        """
        );
        return scenario(
                "deep-routed-container",
                List.of(
                        "/crm",
                        "/crm/customers/:customerId",
                        "/crm/customers/:customerId/workspace",
                        "/crm/customers/:customerId/workspace/activities"
                ),
                List.of(container, panel, detail),
                List.of(api),
                List.of(),
                List.of(),
                List.of(),
                List.of("CRM_DEEP_CONTAINER_CONTEXT", "CRM_DEEP_CONTAINER_CHILD_ACTION")
        );
    }

    private MatrixScenario dynamicForm() {
        var form = component(
                "component-crm-engagement-form",
                0,
                0,
                "CrmEngagementFormComponent",
                List.of("dependency-crm-form-facade"),
                List.of("component-crm-schedule-editor"),
                """
                        export class CrmEngagementFormComponent {
                          readonly definition = this.formFacade.loadRuntimeDefinition();
                        }
                        """
        );
        var schedule = component(
                "component-crm-schedule-editor",
                1,
                1,
                "CrmScheduleEditorComponent",
                List.of("dependency-crm-form-validator", "dependency-crm-schedule-calculator"),
                List.of(),
                """
                        export class CrmScheduleEditorComponent {
                          readonly CRM_DYNAMIC_FORM_MANUAL_OVERRIDE = signal(false);
                          recalculateAfterManualEdit() { return this.calculator.preserveBusinessRules(); }
                        }
                        """
        );
        var facade = dependency(
                "dependency-crm-form-facade",
                0,
                GitLabFrontendReachabilityDependencyKind.FACADE,
                GitLabFrontendReachabilityDependencyCategory.FUNCTIONAL,
                "CrmFormFacade",
                List.of("loadRuntimeDefinition"),
                List.of(form.componentId()),
                List.of(),
                """
                        export class CrmFormFacade {
                          readonly CRM_RUNTIME_SCHEMA_BOUNDARY = 'Definition supplied by a synthetic backend';
                        }
                        """
        );
        var validator = dependency(
                "dependency-crm-form-validator",
                1,
                GitLabFrontendReachabilityDependencyKind.SERVICE,
                GitLabFrontendReachabilityDependencyCategory.FUNCTIONAL,
                "CrmScheduleValidator",
                List.of("validateSequence"),
                List.of(schedule.componentId()),
                List.of(),
                """
                        export class CrmScheduleValidator {
                          readonly CRM_DYNAMIC_FORM_VALIDATION = 'End date must follow start date';
                        }
                        """
        );
        var calculator = dependency(
                "dependency-crm-schedule-calculator",
                2,
                GitLabFrontendReachabilityDependencyKind.SERVICE,
                GitLabFrontendReachabilityDependencyCategory.FUNCTIONAL,
                "CrmScheduleCalculator",
                List.of("preserveBusinessRules"),
                List.of(schedule.componentId()),
                List.of(),
                """
                        export class CrmScheduleCalculator {
                          preserveBusinessRules() { return { keepSequence: true }; }
                        }
                        """
        );
        return scenario(
                "dynamic-form",
                List.of("/crm/engagements/:engagementId/schedule"),
                List.of(form, schedule),
                List.of(facade, validator, calculator),
                List.of(),
                List.of("Runtime CRM form schema must be resolved from backend-provided data."),
                List.of("Exact runtime field catalogue is produced outside the approved frontend repository."),
                List.of(
                        "CRM_DYNAMIC_FORM_MANUAL_OVERRIDE",
                        "CRM_DYNAMIC_FORM_VALIDATION",
                        "CRM_RUNTIME_SCHEMA_BOUNDARY"
                )
        );
    }

    private MatrixScenario reactiveAccess() {
        var component = component(
                "component-crm-live-contact",
                0,
                0,
                "CrmLiveContactComponent",
                List.of(
                        "dependency-crm-contact-api",
                        "dependency-crm-contact-socket",
                        "dependency-crm-contact-state"
                ),
                List.of(),
                """
                        export class CrmLiveContactComponent {
                          readonly CRM_ROLE_ACCESS = this.roles.has('CRM_ADVISOR');
                        }
                        """
        );
        var api = dependency(
                "dependency-crm-contact-api",
                0,
                GitLabFrontendReachabilityDependencyKind.BACKEND_CLIENT,
                GitLabFrontendReachabilityDependencyCategory.FUNCTIONAL,
                "CrmContactApi",
                List.of("loadContact"),
                List.of(component.componentId()),
                List.of(),
                """
                        export class CrmContactApi {
                          readonly CRM_REST_LOAD_CONTACT = '/synthetic-crm/contacts/:contactId';
                        }
                        """
        );
        var socket = dependency(
                "dependency-crm-contact-socket",
                1,
                GitLabFrontendReachabilityDependencyKind.WEBSOCKET,
                GitLabFrontendReachabilityDependencyCategory.REACTIVE,
                "CrmContactSocket",
                List.of("contactChanged"),
                List.of(component.componentId()),
                List.of("dependency-crm-contact-state"),
                """
                        export class CrmContactSocket {
                          readonly CRM_WEBSOCKET_CONTACT_REFRESH = 'contactChanged';
                        }
                        """
        );
        var state = dependency(
                "dependency-crm-contact-state",
                2,
                GitLabFrontendReachabilityDependencyKind.NGRX,
                GitLabFrontendReachabilityDependencyCategory.FUNCTIONAL,
                "CrmContactStateFacade",
                List.of("selectContact", "refreshContact"),
                List.of(component.componentId(), socket.dependencyId()),
                List.of(),
                """
                        export class CrmContactStateFacade {
                          readonly CRM_NGRX_CONTACT_STATE = this.store.select(selectSyntheticContact);
                        }
                        """
        );
        return scenario(
                "reactive-access",
                List.of("/crm/contacts/:contactId/live"),
                List.of(component),
                List.of(api, socket, state),
                List.of("CrmAdvisorGuard", "CRM_ADVISOR"),
                List.of(),
                List.of(),
                List.of(
                        "CRM_ROLE_ACCESS",
                        "CRM_REST_LOAD_CONTACT",
                        "CRM_WEBSOCKET_CONTACT_REFRESH",
                        "CRM_NGRX_CONTACT_STATE"
                )
        );
    }

    private MatrixScenario scenario(
            String id,
            List<String> routePatterns,
            List<GitLabFrontendReachabilityComponent> components,
            List<GitLabFrontendReachabilityDependency> dependencies,
            List<String> guards,
            List<String> researchGaps,
            List<String> visibilityLimits,
            List<String> expectedSourceMarkers
    ) {
        var routeNodeId = "route-" + id;
        var mainComponent = components.get(0);
        var target = new GitLabFrontendRouteTarget(mainComponent.symbol(), mainComponent.sourcePath());
        var routePattern = routePatterns.get(routePatterns.size() - 1);
        var integrationIdentity = new pl.mkn.tdw.integrations.gitlab.frontend.GitLabFrontendScreenIdentity(
                id,
                routeNodeId,
                routePattern,
                "primary",
                target
        );
        var routeSource = new GitLabFrontendSourceReference(
                "apps/crm-agent/src/app/" + id + ".routes.ts",
                "crm" + id.replace("-", "") + "Routes",
                10,
                30
        );
        var routeNode = new GitLabFrontendRouteNode(
                routeNodeId,
                null,
                integrationIdentity,
                "Synthetic CRM " + id,
                segment(routePattern),
                routePattern,
                "primary",
                GitLabFrontendRouteNodeKind.SCREEN,
                GitLabFrontendDiscoveryStatus.RESOLVED,
                routePatterns.size() > 1,
                routePattern.contains(":contactId") || routePattern.contains(":customerId")
                        ? List.of(routePattern.contains(":contactId") ? "contactId" : "customerId")
                        : List.of(),
                target,
                null,
                null,
                List.of(),
                routeSource,
                visibilityLimits
        );
        var chainSegments = new java.util.ArrayList<GitLabFrontendRouteChainSegment>();
        for (var index = 0; index < routePatterns.size(); index++) {
            var pattern = routePatterns.get(index);
            chainSegments.add(new GitLabFrontendRouteChainSegment(
                    index == routePatterns.size() - 1 ? routeNodeId : "route-" + id + "-" + index,
                    segment(pattern),
                    pattern,
                    "primary",
                    List.of(),
                    new GitLabFrontendSourceReference(
                            "apps/crm-agent/src/app/" + id + ".routes.ts",
                            "crm" + id.replace("-", "") + "Routes",
                            10 + index,
                            10 + index
                    )
            ));
        }
        var routeParameters = routeNode.routeParameters();
        var chain = new GitLabFrontendEffectiveRouteChain(integrationIdentity, chainSegments, routeParameters);
        var levels = components.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        GitLabFrontendReachabilityComponent::depth,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ))
                .entrySet().stream()
                .map(entry -> new GitLabFrontendReachabilityComponentLevel(entry.getKey(), entry.getValue()))
                .toList();
        var edges = edges(components, dependencies);
        var outline = outline(routePatterns, components, dependencies, guards);
        var sourceCharacters = components.stream().mapToInt(GitLabFrontendReachabilityComponent::sourceCharacters).sum()
                + dependencies.stream().mapToInt(GitLabFrontendReachabilityDependency::sourceCharacters).sum();
        var sliceCharacters = components.stream().mapToInt(GitLabFrontendReachabilityComponent::returnedCharacters).sum()
                + dependencies.stream().mapToInt(GitLabFrontendReachabilityDependency::returnedCharacters).sum();
        var status = researchGaps.isEmpty() ? "READY" : "PARTIAL";
        var graph = new GitLabFrontendScreenReachabilityGraph(
                new GitLabFrontendRepositoryScope(
                        "synthetic-crm",
                        "crm-agent-portal",
                        "main",
                        List.of("apps/crm-agent", "libs/crm")
                ),
                new GitLabFrontendSourceRevision("main", "crm-matrix-" + id),
                status,
                routeNode,
                chain,
                levels,
                dependencies,
                edges,
                List.of(),
                routePatterns.size() + components.size() * 2 + dependencies.size(),
                sourceCharacters,
                sliceCharacters,
                outline.length(),
                false,
                visibilityLimits,
                outline
        );
        var request = request(id);
        var coverageStatus = researchGaps.isEmpty()
                ? UiExplorerCoverageStatus.READY
                : UiExplorerCoverageStatus.PARTIAL;
        var sectionCoverage = request.resolvedSectionModes().stream()
                .filter(assignment -> assignment.mode() != UiExplorerSectionMode.OFF)
                .map(assignment -> new UiExplorerSectionContextCoverage(
                        assignment.sectionId(),
                        assignment.mode(),
                        coverageStatus,
                        List.of("ROUTE_CHAIN", "COMPONENT_BFS", "FUNCTIONAL_DEPENDENCIES"),
                        researchGaps.isEmpty()
                                ? "Synthetic CRM evidence is reachable."
                                : "Targeted evidence is required for the runtime boundary."
                ))
                .toList();
        var context = new UiExplorerScreenReachabilityContext(
                "crm-agent-portal",
                "CRM Agent Portal",
                new UiExplorerSourceScope(
                        "synthetic-crm",
                        "crm-agent-portal",
                        "main",
                        List.of("apps/crm-agent", "libs/crm")
                ),
                new UiExplorerScreenIdentity(
                        "crm-agent-portal",
                        id,
                        "Synthetic CRM " + id,
                        routePattern,
                        routePatterns.size() > 1 ? routePatterns.get(routePatterns.size() - 2) : "/crm"
                ),
                "RESOLVED",
                routePatterns.size() > 1,
                guards,
                routeParameters,
                visibilityLimits,
                new UiExplorerSourceReference(
                        null,
                        routeSource.path(),
                        routeSource.symbol(),
                        routeSource.startLine(),
                        routeSource.endLine()
                ),
                new UiExplorerSourceRevision("main", "crm-matrix-" + id),
                coverageStatus,
                graph,
                sectionCoverage,
                new UiExplorerReachabilityBoundary(
                        routePatterns.size(),
                        components.size(),
                        dependencies.size(),
                        edges.size(),
                        graph.sourceFileCount(),
                        sourceCharacters,
                        sliceCharacters,
                        outline.length(),
                        false
                ),
                researchGaps,
                visibilityLimits
        );
        return new MatrixScenario(id, request, context, expectedSourceMarkers);
    }

    private UiExplorerJobStartRequest request(String screenId) {
        var sectionModes = new LinkedHashMap<UiExplorerSectionId, UiExplorerSectionMode>();
        for (var sectionId : UiExplorerSectionId.values()) {
            sectionModes.put(sectionId, UiExplorerSectionMode.DEEP);
        }
        return new UiExplorerJobStartRequest(
                "crm-agent-portal",
                "main",
                screenId,
                "crm-matrix-" + screenId,
                sectionModes,
                "Document the strongly anonymized synthetic CRM scenario " + screenId + ".",
                "gpt-5.4",
                "medium"
        );
    }

    private GitLabFrontendReachabilityComponent component(
            String id,
            int order,
            int depth,
            String symbol,
            List<String> dependencyIds,
            List<String> childIds,
            String content
    ) {
        var sourcePath = "apps/crm-agent/src/app/" + id + "/" + symbol + ".ts";
        return new GitLabFrontendReachabilityComponent(
                id,
                order,
                depth,
                order == 0,
                order == 0 ? "SELECTED_SCREEN" : "TEMPLATE_CHILD",
                symbol,
                "synthetic-" + id,
                sourcePath,
                sourcePath.replace(".ts", ".html"),
                "OK",
                List.of(),
                List.of(),
                List.of(),
                dependencyIds,
                childIds,
                content,
                content.length() + 600,
                content.length(),
                false,
                List.of()
        );
    }

    private GitLabFrontendReachabilityDependency dependency(
            String id,
            int order,
            GitLabFrontendReachabilityDependencyKind kind,
            GitLabFrontendReachabilityDependencyCategory category,
            String symbol,
            List<String> methods,
            List<String> usedBy,
            List<String> downstream,
            String content
    ) {
        return new GitLabFrontendReachabilityDependency(
                id,
                order,
                kind,
                category,
                symbol,
                "libs/crm/data-access/src/lib/" + id + ".ts",
                "@synthetic-crm/data-access",
                "OK",
                methods,
                usedBy,
                downstream,
                content,
                content.length() + 400,
                content.length(),
                false,
                List.of()
        );
    }

    private List<GitLabFrontendReachabilityEdge> edges(
            List<GitLabFrontendReachabilityComponent> components,
            List<GitLabFrontendReachabilityDependency> dependencies
    ) {
        var result = new java.util.ArrayList<GitLabFrontendReachabilityEdge>();
        components.forEach(component -> {
            component.childComponentIds().forEach(childId -> result.add(new GitLabFrontendReachabilityEdge(
                    component.componentId(),
                    childId,
                    GitLabFrontendReachabilityEdgeKind.TEMPLATE_CHILD,
                    "synthetic CRM child",
                    component.sourcePath(),
                    component.symbol(),
                    null
            )));
            component.dependencyIds().forEach(dependencyId -> result.add(new GitLabFrontendReachabilityEdge(
                    component.componentId(),
                    dependencyId,
                    GitLabFrontendReachabilityEdgeKind.USES_DEPENDENCY,
                    "synthetic CRM dependency",
                    component.sourcePath(),
                    component.symbol(),
                    null
            )));
        });
        dependencies.forEach(dependency -> dependency.downstreamDependencyIds().forEach(downstreamId ->
                result.add(new GitLabFrontendReachabilityEdge(
                        dependency.dependencyId(),
                        downstreamId,
                        GitLabFrontendReachabilityEdgeKind.DEPENDENCY_CALL,
                        "synthetic CRM downstream dependency",
                        dependency.sourcePath(),
                        dependency.symbol(),
                        null
                ))
        ));
        return List.copyOf(result);
    }

    private String outline(
            List<String> routePatterns,
            List<GitLabFrontendReachabilityComponent> components,
            List<GitLabFrontendReachabilityDependency> dependencies,
            List<String> guards
    ) {
        var lines = new java.util.ArrayList<String>();
        lines.add("# Frontend screen reachability graph");
        lines.add("");
        lines.add("## Effective route chain");
        for (var index = 0; index < routePatterns.size(); index++) {
            lines.add((index + 1) + ". route `" + routePatterns.get(index) + "`");
        }
        if (!guards.isEmpty()) {
            lines.add("- access guards and roles: " + String.join(", ", guards));
        }
        lines.add("");
        lines.add("## Component breadth-first traversal");
        var byDepth = components.stream().collect(java.util.stream.Collectors.groupingBy(
                GitLabFrontendReachabilityComponent::depth,
                LinkedHashMap::new,
                java.util.stream.Collectors.toList()
        ));
        byDepth.forEach((depth, values) -> {
            lines.add("");
            lines.add("### Depth " + depth);
            values.forEach(component -> lines.add("- [C" + (component.breadthFirstOrder() + 1) + "] "
                    + component.symbol() + "; children=" + component.childComponentIds()
                    + "; dependencies=" + component.dependencyIds()));
        });
        lines.add("");
        lines.add("## Functional and supporting dependencies");
        dependencies.forEach(dependency -> lines.add("- [D" + (dependency.discoveryOrder() + 1) + "] "
                + dependency.symbol() + "; kind=" + dependency.kind()
                + "; methods=" + dependency.methods() + "; usedBy=" + dependency.usedBy()));
        return String.join(System.lineSeparator(), lines);
    }

    private String segment(String routePattern) {
        var parts = routePattern.split("/");
        return parts.length == 0 ? "" : parts[parts.length - 1];
    }

    private record MatrixScenario(
            String id,
            UiExplorerJobStartRequest request,
            UiExplorerScreenReachabilityContext context,
            List<String> expectedSourceMarkers
    ) {
    }

    private record PreparedCase(
            MatrixScenario scenario,
            UiExplorerPromptPreparation preparation
    ) {
    }
}
