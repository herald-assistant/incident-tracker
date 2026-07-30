package pl.mkn.tdw.features.runtimeconfigurationverification.workbench;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation.RuntimeConfigurationPromptPreparationService;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.RuntimeConfigurationDeepContextService;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationSensitivity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationValueType;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.SanitizedConfigurationDocument;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.SanitizedConfigurationNode;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationDeterministicContextService;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api.RuntimeConfigurationVerificationMode;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.export
        .RuntimeConfigurationVerificationSnapshotSanitizer;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScopeResolver;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api
        .RuntimeConfigurationWorkbenchPreviewRequest;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api
        .RuntimeConfigurationWorkbenchPreviewResponse;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api
        .RuntimeConfigurationWorkbenchPreviewResponse.AnonymizationDecision;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api
        .RuntimeConfigurationWorkbenchPreviewResponse.AnonymizationSummary;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api
        .RuntimeConfigurationWorkbenchPreviewResponse.ArtifactSummary;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api
        .RuntimeConfigurationWorkbenchPreviewResponse.SourceAcquisition;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api
        .RuntimeConfigurationWorkbenchPreviewResponse.ValueRepresentation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RuntimeConfigurationWorkbenchPreviewService {

    private static final String DEEP_FAILURE_LIMIT =
            "DEEP enrichment did not complete; deterministic mapping and BASIC-safe AI input remain available.";

    private final RuntimeConfigurationScopeResolver scopeResolver;
    private final RuntimeConfigurationDeterministicContextService deterministicContextService;
    private final RuntimeConfigurationDeepContextService deepContextService;
    private final RuntimeConfigurationPromptPreparationService promptPreparationService;

    public RuntimeConfigurationWorkbenchPreviewResponse preview(
            RuntimeConfigurationWorkbenchPreviewRequest request
    ) {
        var scope = scopeResolver.resolve(request.repositoryId(), request.systemId());
        var deterministic = buildDeterministic(request, scope);

        var visibilityLimits = new LinkedHashSet<String>();
        var deepContext = buildDeepContext(request, deterministic, visibilityLimits);
        var preparation = preparePrompt(request, deterministic, deepContext);
        visibilityLimits.addAll(preparation.visibilityLimits());
        if (deepContext != null) {
            visibilityLimits.addAll(deepContext.visibilityLimits());
        }

        var artifacts = preparation.artifactContents().entrySet().stream()
                .map(entry -> new ArtifactSummary(
                        entry.getKey(),
                        entry.getValue().length(),
                        entry.getValue().contains("\"truncated\":true")
                ))
                .toList();

        return new RuntimeConfigurationWorkbenchPreviewResponse(
                request.mode(),
                request.repositoryId(),
                request.systemId(),
                request.sourceBranch(),
                request.targetBranch(),
                request.codeRef(),
                new SourceAcquisition(
                        deterministic.configurationDirectory(),
                        deterministic.sourceCoverage(),
                        deterministic.targetCoverage()
                ),
                deterministic,
                anonymization(deterministic.documents()),
                deepContext,
                preparation.prompt(),
                preparation.artifactContents(),
                artifacts,
                List.copyOf(visibilityLimits)
        );
    }

    private pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
            .RuntimeConfigurationDeterministicContext buildDeterministic(
            RuntimeConfigurationWorkbenchPreviewRequest request,
            pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScope scope
    ) {
        try {
            return RuntimeConfigurationVerificationSnapshotSanitizer.sanitize(
                    deterministicContextService.build(
                            scope,
                            request.sourceBranch(),
                            request.targetBranch()
                    )
            );
        } catch (RuntimeException exception) {
            throw safeFailure("deterministic", exception);
        }
    }

    private pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation
            .RuntimeConfigurationPromptPreparation preparePrompt(
            RuntimeConfigurationWorkbenchPreviewRequest request,
            pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
                    .RuntimeConfigurationDeterministicContext deterministic,
            RuntimeConfigurationDeepContext deepContext
    ) {
        try {
            return promptPreparationService.prepare(
                    request.asPreparationRequest(),
                    deterministic,
                    deepContext
            );
        } catch (RuntimeException exception) {
            throw safeFailure("preparation", exception);
        }
    }

    private RuntimeConfigurationWorkbenchPreviewException safeFailure(
            String stage,
            RuntimeException exception
    ) {
        log.warn(
                "Runtime configuration workbench preview failed stage={} failureType={}",
                stage,
                exception.getClass().getSimpleName()
        );
        return new RuntimeConfigurationWorkbenchPreviewException();
    }

    private RuntimeConfigurationDeepContext buildDeepContext(
            RuntimeConfigurationWorkbenchPreviewRequest request,
            pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
                    .RuntimeConfigurationDeterministicContext deterministic,
            LinkedHashSet<String> visibilityLimits
    ) {
        if (request.mode() != RuntimeConfigurationVerificationMode.DEEP) {
            return null;
        }
        try {
            return deepContextService.build(
                    request.mode(),
                    request.repositoryId(),
                    request.systemId(),
                    request.codeRef(),
                    deterministic
            ).orElse(null);
        } catch (RuntimeException exception) {
            visibilityLimits.add(DEEP_FAILURE_LIMIT);
            return null;
        }
    }

    private AnonymizationSummary anonymization(List<SanitizedConfigurationDocument> documents) {
        var decisions = new ArrayList<AnonymizationDecision>();
        for (var document : documents) {
            collect(document, document.root(), decisions);
        }
        return new AnonymizationSummary(
                decisions.size(),
                count(decisions, ValueRepresentation.PSEUDONYMIZED),
                count(decisions, ValueRepresentation.SUPPRESSED),
                count(decisions, ValueRepresentation.STRUCTURE_ONLY),
                count(decisions, ValueRepresentation.NOT_PRESENT),
                decisions
        );
    }

    private void collect(
            SanitizedConfigurationDocument document,
            SanitizedConfigurationNode node,
            List<AnonymizationDecision> decisions
    ) {
        if (node == null) {
            return;
        }
        var sourceRepresentation = representation(
                node.sourceType(),
                node.sensitivity(),
                node.sourceValueToken()
        );
        var targetRepresentation = representation(
                node.targetType(),
                node.sensitivity(),
                node.targetValueToken()
        );
        decisions.add(new AnonymizationDecision(
                document.role(),
                document.documentIndex(),
                node.path(),
                node.relation(),
                node.sensitivity(),
                node.sourceType(),
                node.targetType(),
                sourceRepresentation,
                targetRepresentation,
                safeToken(node.sensitivity(), node.sourceValueToken()),
                safeToken(node.sensitivity(), node.targetValueToken())
        ));
        for (var child : node.children()) {
            collect(document, child, decisions);
        }
    }

    private ValueRepresentation representation(
            RuntimeConfigurationValueType type,
            RuntimeConfigurationSensitivity sensitivity,
            String valueToken
    ) {
        if (type == null) {
            return ValueRepresentation.NOT_PRESENT;
        }
        if (sensitivity == RuntimeConfigurationSensitivity.SENSITIVE) {
            return ValueRepresentation.SUPPRESSED;
        }
        if (valueToken != null) {
            return ValueRepresentation.PSEUDONYMIZED;
        }
        return ValueRepresentation.STRUCTURE_ONLY;
    }

    private String safeToken(RuntimeConfigurationSensitivity sensitivity, String valueToken) {
        return sensitivity == RuntimeConfigurationSensitivity.SENSITIVE ? null : valueToken;
    }

    private int count(
            List<AnonymizationDecision> decisions,
            ValueRepresentation representation
    ) {
        return (int) decisions.stream()
                .flatMap(decision -> java.util.stream.Stream.of(
                        decision.sourceRepresentation(),
                        decision.targetRepresentation()
                ))
                .filter(representation::equals)
                .count();
    }
}
