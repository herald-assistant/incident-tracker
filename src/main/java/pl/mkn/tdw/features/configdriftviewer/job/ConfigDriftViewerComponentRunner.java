package pl.mkn.tdw.features.configdriftviewer.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.configdriftviewer.ai.ConfigDriftViewerAiAssessmentService;
import pl.mkn.tdw.features.configdriftviewer.ai.ConfigDriftViewerAiRunner;
import pl.mkn.tdw.features.configdriftviewer.ai.preparation.ConfigDriftViewerPromptPreparationService;
import pl.mkn.tdw.features.configdriftviewer.ai.report.ConfigDriftViewerReportFactory;
import pl.mkn.tdw.features.configdriftviewer.deep.ConfigDriftViewerDeepContextListener;
import pl.mkn.tdw.features.configdriftviewer.deep.ConfigDriftViewerDeepContextService;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerDeterministicBuildResult;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerDeterministicContextListener;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerDeterministicContextService;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerJobStartRequest;
import pl.mkn.tdw.features.configdriftviewer.job.api.ConfigDriftViewerMode;
import pl.mkn.tdw.features.configdriftviewer.job.state.ConfigDriftViewerJobState;
import pl.mkn.tdw.features.configdriftviewer.presentation.ConfigDriftViewerDiffAnnotationService;
import pl.mkn.tdw.features.configdriftviewer.scope.ConfigDriftViewerScopeResolver;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

@Component
@Slf4j
@RequiredArgsConstructor
public class ConfigDriftViewerComponentRunner {

    private final ConfigDriftViewerScopeResolver scopeResolver;
    private final ConfigDriftViewerDeterministicContextService deterministicContextService;
    private final ConfigDriftViewerDeepContextService deepContextService;
    private final ConfigDriftViewerPromptPreparationService promptPreparationService;
    private final ConfigDriftViewerAiRunner aiRunner;
    private final ConfigDriftViewerAiAssessmentService assessmentService;
    private final ConfigDriftViewerReportFactory reportFactory;
    private final ConfigDriftViewerDiffAnnotationService diffAnnotationService;

    public void run(
            ConfigDriftViewerJobState component,
            ConfigDriftViewerJobStartRequest request,
            AnalysisAiAuthRef authRef,
            Runnable stateUpdated
    ) {
        var systemId = component.systemId();
        try {
            var scope = scopeResolver.resolve(request.repositoryId(), systemId);
            component.markScopeResolved(scope);
            stateUpdated.run();
            var deterministicBuild = deterministicContextService.build(
                    scope,
                    request.sourceBranch(),
                    request.targetBranch(),
                    deterministicListener(component, stateUpdated)
            );
            var deterministic = deterministicBuild.context();
            if (request.mode() == ConfigDriftViewerMode.BASIC) {
                stateUpdated.run();
                return;
            }
            var deep = buildDeepContext(component, request, deterministic, stateUpdated);
            component.markAiStarted(null);
            stateUpdated.run();

            String safePrompt = null;
            try {
                var preparation = promptPreparationService.prepare(request, deterministic, deep);
                safePrompt = preparation.prompt();
                component.markAiStarted(safePrompt);
                stateUpdated.run();
                var aiRun = aiRunner.run(
                        component.componentRunId(),
                        request,
                        deterministic,
                        deep,
                        preparation,
                        java.util.Objects.requireNonNull(authRef),
                        section -> {
                            component.markAiToolEvidenceUpdated(section);
                            stateUpdated.run();
                        },
                        event -> {
                            component.markAiActivity(event);
                            stateUpdated.run();
                        }
                );
                component.markCompleted(
                        deep,
                        aiRun,
                        safePrompt,
                        diffAnnotationService.create(
                                deterministic,
                                aiRun != null && aiRun.assessment() != null
                                        ? aiRun.assessment().aiSecondOpinion()
                                        : null
                        )
                );
            } catch (RuntimeException exception) {
                log.warn(
                        "Config Drift Viewer AI failed componentRunId={} failureType={}",
                        component.componentRunId(),
                        exception.getClass().getSimpleName()
                );
                var scaffold = reportFactory.createInitialReport(
                        "runtime-configuration-" + component.componentRunId(),
                        deterministic,
                        deep
                );
                var fallback = assessmentService.assess(null, deterministic, deep, scaffold, null);
                component.markAiFailed(
                        deep,
                        fallback,
                        safePrompt,
                        diffAnnotationService.create(
                                deterministic,
                                fallback != null ? fallback.aiSecondOpinion() : null
                        )
                );
            }
            stateUpdated.run();
        } catch (RuntimeException exception) {
            log.warn(
                    "Config Drift Viewer failed componentRunId={} systemId={} failureType={}",
                    component.componentRunId(),
                    systemId,
                    exception.getClass().getSimpleName()
            );
            component.markFailed(
                    "RUNTIME_CONFIGURATION_VERIFICATION_FAILED",
                    "Configuration verification did not complete. Check access and configuration coverage, then retry."
            );
            stateUpdated.run();
        }
    }

    private ConfigDriftViewerDeepContext buildDeepContext(
            ConfigDriftViewerJobState component,
            ConfigDriftViewerJobStartRequest request,
            ConfigDriftViewerDeterministicContext deterministic,
            Runnable stateUpdated
    ) {
        try {
            return deepContextService.build(
                    request.mode(),
                    request.repositoryId(),
                    component.systemId(),
                    request.codeRef(),
                    deterministic,
                    deepListener(component, stateUpdated)
            ).orElse(null);
        } catch (RuntimeException exception) {
            log.warn(
                    "Config Drift Viewer DEEP enrichment failed systemId={} failureType={}",
                    component.systemId(),
                    exception.getClass().getSimpleName()
            );
            component.markDeepFailed();
            stateUpdated.run();
            return null;
        }
    }

    private ConfigDriftViewerDeterministicContextListener deterministicListener(
            ConfigDriftViewerJobState component,
            Runnable stateUpdated
    ) {
        return new ConfigDriftViewerDeterministicContextListener() {
            @Override
            public void onSourceStarted() {
                component.markSourceStarted();
                stateUpdated.run();
            }

            @Override
            public void onSourceCompleted() {
                component.markSourceCompleted();
                stateUpdated.run();
            }

            @Override
            public void onParseStarted() {
                component.markParseStarted();
                stateUpdated.run();
            }

            @Override
            public void onParseCompleted() {
                component.markParseCompleted();
                stateUpdated.run();
            }

            @Override
            public void onDiffStarted() {
                component.markDiffStarted();
                stateUpdated.run();
            }

            @Override
            public void onDiffCompleted(ConfigDriftViewerDeterministicBuildResult result) {
                component.markDiffCompleted(result);
                stateUpdated.run();
            }
        };
    }

    private ConfigDriftViewerDeepContextListener deepListener(
            ConfigDriftViewerJobState component,
            Runnable stateUpdated
    ) {
        return new ConfigDriftViewerDeepContextListener() {
            @Override
            public void onOperationalContextStarted() {
                component.markOperationalContextStarted();
                stateUpdated.run();
            }

            @Override
            public void onOperationalContextCompleted() {
                component.markOperationalContextCompleted();
                stateUpdated.run();
            }

            @Override
            public void onCodeGroundingStarted() {
                component.markCodeGroundingStarted();
                stateUpdated.run();
            }

            @Override
            public void onCodeGroundingCompleted() {
                component.markCodeGroundingCompleted();
                stateUpdated.run();
            }

            @Override
            public void onOwnershipStarted() {
                component.markOwnershipStarted();
                stateUpdated.run();
            }

            @Override
            public void onOwnershipCompleted(ConfigDriftViewerDeepContext context) {
                component.markOwnershipCompleted(context);
                stateUpdated.run();
            }
        };
    }
}
