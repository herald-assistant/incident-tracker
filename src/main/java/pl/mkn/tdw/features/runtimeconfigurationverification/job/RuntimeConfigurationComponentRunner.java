package pl.mkn.tdw.features.runtimeconfigurationverification.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.RuntimeConfigurationAiAssessmentService;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.RuntimeConfigurationAiRunner;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation.RuntimeConfigurationPromptPreparationService;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.report.RuntimeConfigurationReportFactory;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.RuntimeConfigurationDeepContextListener;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.RuntimeConfigurationDeepContextService;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationDeterministicBuildResult;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationDeterministicContextListener;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationDeterministicContextService;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationJobStartRequest;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.state.RuntimeConfigurationVerificationJobState;
import pl.mkn.tdw.features.runtimeconfigurationverification.presentation.RuntimeConfigurationDiffAnnotationService;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScopeResolver;
import pl.mkn.tdw.shared.ai.AnalysisAiAuthRef;

@Component
@Slf4j
@RequiredArgsConstructor
public class RuntimeConfigurationComponentRunner {

    private final RuntimeConfigurationScopeResolver scopeResolver;
    private final RuntimeConfigurationDeterministicContextService deterministicContextService;
    private final RuntimeConfigurationDeepContextService deepContextService;
    private final RuntimeConfigurationPromptPreparationService promptPreparationService;
    private final RuntimeConfigurationAiRunner aiRunner;
    private final RuntimeConfigurationAiAssessmentService assessmentService;
    private final RuntimeConfigurationReportFactory reportFactory;
    private final RuntimeConfigurationDiffAnnotationService diffAnnotationService;

    public void run(
            RuntimeConfigurationVerificationJobState component,
            RuntimeConfigurationVerificationJobStartRequest request,
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
            if (request.mode() == RuntimeConfigurationVerificationMode.BASIC) {
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
                        "Runtime Configuration Verification AI failed componentRunId={} failureType={}",
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
                    "Runtime Configuration Verification failed componentRunId={} systemId={} failureType={}",
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

    private RuntimeConfigurationDeepContext buildDeepContext(
            RuntimeConfigurationVerificationJobState component,
            RuntimeConfigurationVerificationJobStartRequest request,
            RuntimeConfigurationDeterministicContext deterministic,
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
                    "Runtime Configuration Verification DEEP enrichment failed systemId={} failureType={}",
                    component.systemId(),
                    exception.getClass().getSimpleName()
            );
            component.markDeepFailed();
            stateUpdated.run();
            return null;
        }
    }

    private RuntimeConfigurationDeterministicContextListener deterministicListener(
            RuntimeConfigurationVerificationJobState component,
            Runnable stateUpdated
    ) {
        return new RuntimeConfigurationDeterministicContextListener() {
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
            public void onDiffCompleted(RuntimeConfigurationDeterministicBuildResult result) {
                component.markDiffCompleted(result);
                stateUpdated.run();
            }
        };
    }

    private RuntimeConfigurationDeepContextListener deepListener(
            RuntimeConfigurationVerificationJobState component,
            Runnable stateUpdated
    ) {
        return new RuntimeConfigurationDeepContextListener() {
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
            public void onOwnershipCompleted(RuntimeConfigurationDeepContext context) {
                component.markOwnershipCompleted(context);
                stateUpdated.run();
            }
        };
    }
}
