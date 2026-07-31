package pl.mkn.tdw.features.runtimeconfigurationverification.workbench;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation
        .RuntimeConfigurationPromptPreparation;
import pl.mkn.tdw.features.runtimeconfigurationverification.ai.preparation
        .RuntimeConfigurationPromptPreparationService;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.RuntimeConfigurationDeepContextService;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationChangeKind;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationSensitivity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .RuntimeConfigurationValueType;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .SanitizedConfigurationDocument;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model
        .SanitizedConfigurationNode;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.projection
        .RuntimeConfigurationDiffDocument;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.projection
        .RuntimeConfigurationDiffNode;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.projection
        .RuntimeConfigurationDiffProjection;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source
        .RuntimeConfigurationDeterministicBuildResult;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source
        .RuntimeConfigurationDeterministicContextService;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.api
        .RuntimeConfigurationVerificationMode;
import pl.mkn.tdw.features.runtimeconfigurationverification.job.export
        .RuntimeConfigurationVerificationSnapshotSanitizer;
import pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScopeResolver;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api
        .RuntimeConfigurationWorkbenchAiInputResponse;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api
        .RuntimeConfigurationWorkbenchAnonymizationPage;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api
        .RuntimeConfigurationWorkbenchAnonymizationPage.ValueRepresentation;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api
        .RuntimeConfigurationWorkbenchArtifactResponse;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api
        .RuntimeConfigurationWorkbenchConfigurationDiffResponse;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api
        .RuntimeConfigurationWorkbenchDeepResponse;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api
        .RuntimeConfigurationWorkbenchMappingPage;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api
        .RuntimeConfigurationWorkbenchPreviewRequest;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api
        .RuntimeConfigurationWorkbenchPreviewResponse;
import pl.mkn.tdw.features.runtimeconfigurationverification.workbench.api
        .RuntimeConfigurationWorkbenchSourceResponse;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RuntimeConfigurationWorkbenchPreviewService {

    private static final String DEEP_FAILURE_LIMIT =
            "DEEP enrichment did not complete; deterministic projection and sanitized AI input remain available.";

    private final RuntimeConfigurationScopeResolver scopeResolver;
    private final RuntimeConfigurationDeterministicContextService deterministicContextService;
    private final RuntimeConfigurationDeepContextService deepContextService;
    private final RuntimeConfigurationPromptPreparationService promptPreparationService;
    private final RuntimeConfigurationWorkbenchPreviewStore previewStore;

    public RuntimeConfigurationWorkbenchPreviewResponse preview(
            RuntimeConfigurationWorkbenchPreviewRequest request
    ) {
        var scope = scopeResolver.resolve(request.repositoryId(), request.systemId());
        var deterministicBuild = buildDeterministic(request, scope);
        var deterministic = deterministicBuild.context();
        var configurationDiff = deterministicBuild.configurationDiff();
        var visibilityLimits = new LinkedHashSet<String>();
        RuntimeConfigurationDeepContext deepContext = null;
        RuntimeConfigurationPromptPreparation preparation = null;
        List<RuntimeConfigurationWorkbenchMappingPage.Item> mappingItems = List.of();
        List<RuntimeConfigurationWorkbenchAnonymizationPage.Item> anonymizationItems = List.of();
        if (request.mode() == RuntimeConfigurationVerificationMode.DEEP) {
            deepContext = buildDeepContext(request, deterministic, visibilityLimits);
            preparation = preparePrompt(request, deterministic, deepContext);
            visibilityLimits.addAll(preparation.visibilityLimits());
            if (deepContext != null) {
                visibilityLimits.addAll(deepContext.visibilityLimits());
            }
            mappingItems = mappingItems(deterministic.documents(), configurationDiff);
            anonymizationItems = anonymizationItems(deterministic.documents());
        }

        var stored = previewStore.store(new RuntimeConfigurationWorkbenchPreviewSnapshot(
                request.mode(),
                deterministic,
                configurationDiff,
                deepContext,
                preparation,
                mappingItems,
                anonymizationItems
        ));

        return new RuntimeConfigurationWorkbenchPreviewResponse(
                stored.previewId(),
                stored.expiresAt(),
                request.mode(),
                request.repositoryId(),
                request.systemId(),
                request.sourceBranch(),
                request.targetBranch(),
                request.codeRef(),
                sourceSummary(deterministic),
                new RuntimeConfigurationWorkbenchPreviewResponse.Counts(
                        deterministic.documents().size(),
                        nodeCount(configurationDiff),
                        deterministic.differences().size(),
                        deterministic.findings().size(),
                        deterministic.references().size()
                ),
                anonymizationSummary(anonymizationItems),
                deepSummary(request.mode(), deepContext),
                preparation != null,
                preparation != null ? preparation.artifactContents().entrySet().stream()
                        .map(entry -> new RuntimeConfigurationWorkbenchPreviewResponse.ArtifactSummary(
                                entry.getKey(),
                                mediaType(entry.getKey()),
                                entry.getValue().length(),
                                truncated(entry.getValue())
                        ))
                        .toList() : List.of(),
                List.copyOf(visibilityLimits)
        );
    }

    public RuntimeConfigurationWorkbenchSourceResponse source(String previewId) {
        var snapshot = previewStore.require(previewId);
        return new RuntimeConfigurationWorkbenchSourceResponse(
                previewId,
                snapshot.deterministic().configurationDirectory(),
                snapshot.deterministic().sourceCoverage(),
                snapshot.deterministic().targetCoverage()
        );
    }

    public RuntimeConfigurationWorkbenchConfigurationDiffResponse configurationDiff(
            String previewId
    ) {
        var snapshot = previewStore.require(previewId);
        return new RuntimeConfigurationWorkbenchConfigurationDiffResponse(
                previewId,
                snapshot.configurationDiff()
        );
    }

    public RuntimeConfigurationWorkbenchMappingPage mapping(
            String previewId,
            int offset,
            int limit,
            boolean changedOnly
    ) {
        var snapshot = previewStore.require(previewId);
        var filtered = changedOnly
                ? snapshot.mappingItems().stream()
                        .filter(item -> item.changeKind() != RuntimeConfigurationChangeKind.UNCHANGED)
                        .toList()
                : snapshot.mappingItems();
        var page = page(filtered, offset, limit);
        return new RuntimeConfigurationWorkbenchMappingPage(
                previewId,
                page.offset(),
                page.limit(),
                filtered.size(),
                snapshot.mappingItems().size(),
                changedOnly,
                page.items()
        );
    }

    public RuntimeConfigurationWorkbenchAnonymizationPage anonymization(
            String previewId,
            int offset,
            int limit
    ) {
        var snapshot = previewStore.require(previewId);
        var page = page(snapshot.anonymizationItems(), offset, limit);
        return new RuntimeConfigurationWorkbenchAnonymizationPage(
                previewId,
                page.offset(),
                page.limit(),
                snapshot.anonymizationItems().size(),
                page.items()
        );
    }

    public RuntimeConfigurationWorkbenchDeepResponse deep(String previewId) {
        var snapshot = previewStore.require(previewId);
        return new RuntimeConfigurationWorkbenchDeepResponse(
                previewId,
                snapshot.mode() == RuntimeConfigurationVerificationMode.DEEP,
                snapshot.deepContext()
        );
    }

    public RuntimeConfigurationWorkbenchAiInputResponse aiInput(String previewId) {
        var snapshot = previewStore.require(previewId);
        if (snapshot.preparation() == null) {
            return new RuntimeConfigurationWorkbenchAiInputResponse(
                    previewId,
                    false,
                    0,
                    null
            );
        }
        return new RuntimeConfigurationWorkbenchAiInputResponse(
                previewId,
                true,
                snapshot.preparation().prompt().length(),
                snapshot.preparation().prompt()
        );
    }

    public RuntimeConfigurationWorkbenchArtifactResponse artifact(
            String previewId,
            String name
    ) {
        var snapshot = previewStore.require(previewId);
        var content = snapshot.preparation() != null
                ? snapshot.preparation().artifactContents().get(name)
                : null;
        if (content == null) {
            throw new RuntimeConfigurationWorkbenchPreviewNotFoundException();
        }
        return new RuntimeConfigurationWorkbenchArtifactResponse(
                previewId,
                name,
                mediaType(name),
                content.length(),
                truncated(content),
                content
        );
    }

    private RuntimeConfigurationDeterministicBuildResult buildDeterministic(
            RuntimeConfigurationWorkbenchPreviewRequest request,
            pl.mkn.tdw.features.runtimeconfigurationverification.scope.RuntimeConfigurationScope scope
    ) {
        try {
            var build = deterministicContextService.build(
                    scope,
                    request.sourceBranch(),
                    request.targetBranch()
            );
            return new RuntimeConfigurationDeterministicBuildResult(
                    RuntimeConfigurationVerificationSnapshotSanitizer.sanitize(build.context()),
                    build.configurationDiff()
            );
        } catch (RuntimeException exception) {
            throw safeFailure("deterministic", exception);
        }
    }

    private RuntimeConfigurationPromptPreparation preparePrompt(
            RuntimeConfigurationWorkbenchPreviewRequest request,
            RuntimeConfigurationDeterministicContext deterministic,
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
            RuntimeConfigurationDeterministicContext deterministic,
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

    private RuntimeConfigurationWorkbenchPreviewResponse.SourceSummary sourceSummary(
            RuntimeConfigurationDeterministicContext deterministic
    ) {
        var source = deterministic.sourceCoverage();
        var target = deterministic.targetCoverage();
        return new RuntimeConfigurationWorkbenchPreviewResponse.SourceSummary(
                deterministic.configurationDirectory(),
                source != null && source.branchExists(),
                source != null && source.complete(),
                target != null && target.branchExists(),
                target != null && target.complete()
        );
    }

    private RuntimeConfigurationWorkbenchPreviewResponse.DeepSummary deepSummary(
            RuntimeConfigurationVerificationMode mode,
            RuntimeConfigurationDeepContext deep
    ) {
        if (mode != RuntimeConfigurationVerificationMode.DEEP) {
            return new RuntimeConfigurationWorkbenchPreviewResponse.DeepSummary(
                    false, null, null, 0, 0, 0, 0
            );
        }
        var preflight = deep != null ? deep.preflight() : null;
        var ownership = deep != null ? deep.ownership() : null;
        return new RuntimeConfigurationWorkbenchPreviewResponse.DeepSummary(
                true,
                deep != null && deep.status() != null ? deep.status().name() : "UNAVAILABLE",
                preflight != null && preflight.status() != null ? preflight.status().name() : null,
                preflight != null ? preflight.repositories().size() : 0,
                preflight != null ? preflight.blockers().size() : 0,
                deep != null ? deep.codeGrounding().size() : 0,
                ownership != null ? ownership.primaryOwners().size() : 0
        );
    }

    private List<RuntimeConfigurationWorkbenchMappingPage.Item> mappingItems(
            List<SanitizedConfigurationDocument> documents,
            RuntimeConfigurationDiffProjection configurationDiff
    ) {
        var items = new ArrayList<RuntimeConfigurationWorkbenchMappingPage.Item>();
        for (var document : documents) {
            var projected = projectionDocument(configurationDiff, document);
            collectMapping(document, document.root(), projected.root(), 0, items);
        }
        return List.copyOf(items);
    }

    private void collectMapping(
            SanitizedConfigurationDocument document,
            SanitizedConfigurationNode sanitized,
            RuntimeConfigurationDiffNode original,
            int depth,
            List<RuntimeConfigurationWorkbenchMappingPage.Item> items
    ) {
        if (sanitized == null || original == null
                || sanitized.children().size() != original.children().size()) {
            throw new IllegalArgumentException(
                    "Operator projection does not match sanitized configuration tree"
            );
        }
        items.add(new RuntimeConfigurationWorkbenchMappingPage.Item(
                document.role(),
                document.documentIndex(),
                depth,
                original.name(),
                original.path(),
                sanitized.name(),
                sanitized.path(),
                sanitized.sourceType(),
                sanitized.targetType(),
                original.changeKind(),
                sanitized.sensitivity(),
                safeToken(sanitized.sensitivity(), sanitized.sourceValueToken()),
                safeToken(sanitized.sensitivity(), sanitized.targetValueToken()),
                original.differenceIds()
        ));
        for (var index = 0; index < sanitized.children().size(); index++) {
            collectMapping(
                    document,
                    sanitized.children().get(index),
                    original.children().get(index),
                    depth + 1,
                    items
            );
        }
    }

    private RuntimeConfigurationDiffDocument projectionDocument(
            RuntimeConfigurationDiffProjection configurationDiff,
            SanitizedConfigurationDocument document
    ) {
        return configurationDiff.files().stream()
                .filter(file -> file.role() == document.role())
                .flatMap(file -> file.documents().stream())
                .filter(candidate -> candidate.documentIndex() == document.documentIndex())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Missing operator projection document for "
                                + document.role()
                                + " #"
                                + document.documentIndex()
                ));
    }

    private int nodeCount(RuntimeConfigurationDiffProjection configurationDiff) {
        return configurationDiff.files().stream()
                .flatMap(file -> file.documents().stream())
                .mapToInt(document -> nodeCount(document.root()))
                .sum();
    }

    private int nodeCount(RuntimeConfigurationDiffNode node) {
        return 1 + node.children().stream().mapToInt(this::nodeCount).sum();
    }

    private List<RuntimeConfigurationWorkbenchAnonymizationPage.Item> anonymizationItems(
            List<SanitizedConfigurationDocument> documents
    ) {
        var items = new ArrayList<RuntimeConfigurationWorkbenchAnonymizationPage.Item>();
        for (var document : documents) {
            collectAnonymization(document, document.root(), items);
        }
        return List.copyOf(items);
    }

    private void collectAnonymization(
            SanitizedConfigurationDocument document,
            SanitizedConfigurationNode node,
            List<RuntimeConfigurationWorkbenchAnonymizationPage.Item> items
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
        items.add(new RuntimeConfigurationWorkbenchAnonymizationPage.Item(
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
            collectAnonymization(document, child, items);
        }
    }

    private RuntimeConfigurationWorkbenchPreviewResponse.AnonymizationSummary
            anonymizationSummary(
            List<RuntimeConfigurationWorkbenchAnonymizationPage.Item> items
    ) {
        return new RuntimeConfigurationWorkbenchPreviewResponse.AnonymizationSummary(
                items.size(),
                count(items, ValueRepresentation.PSEUDONYMIZED),
                count(items, ValueRepresentation.SUPPRESSED),
                count(items, ValueRepresentation.STRUCTURE_ONLY),
                count(items, ValueRepresentation.NOT_PRESENT)
        );
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
        return valueToken != null
                ? ValueRepresentation.PSEUDONYMIZED
                : ValueRepresentation.STRUCTURE_ONLY;
    }

    private String safeToken(RuntimeConfigurationSensitivity sensitivity, String valueToken) {
        return sensitivity == RuntimeConfigurationSensitivity.SENSITIVE ? null : valueToken;
    }

    private int count(
            List<RuntimeConfigurationWorkbenchAnonymizationPage.Item> items,
            ValueRepresentation representation
    ) {
        return (int) items.stream()
                .flatMap(item -> java.util.stream.Stream.of(
                        item.sourceRepresentation(),
                        item.targetRepresentation()
                ))
                .filter(representation::equals)
                .count();
    }

    private <T> Page<T> page(List<T> values, int requestedOffset, int requestedLimit) {
        var offset = Math.max(0, Math.min(requestedOffset, values.size()));
        var limit = Math.max(1, Math.min(requestedLimit, 200));
        var end = Math.min(values.size(), offset + limit);
        return new Page<>(offset, limit, List.copyOf(values.subList(offset, end)));
    }

    private String mediaType(String name) {
        return name != null && (name.endsWith(".yaml") || name.endsWith(".yml"))
                ? "application/yaml"
                : "application/json";
    }

    private boolean truncated(String content) {
        return content != null
                && (content.contains("\"truncated\":true")
                || content.contains("\"truncated\": true")
                || content.contains("truncated: true"));
    }

    private record Page<T>(int offset, int limit, List<T> items) {
    }
}
