package pl.mkn.tdw.features.configdriftviewer.workbench;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import pl.mkn.tdw.features.configdriftviewer.ai.preparation
        .ConfigDriftViewerPromptPreparation;
import pl.mkn.tdw.features.configdriftviewer.ai.preparation
        .ConfigDriftViewerPromptPreparationService;
import pl.mkn.tdw.features.configdriftviewer.deep.ConfigDriftViewerDeepContextService;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerChangeKind;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerSensitivity;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .ConfigDriftViewerValueType;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .SanitizedConfigurationDocument;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model
        .SanitizedConfigurationNode;
import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffDocument;
import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffNode;
import pl.mkn.tdw.features.configdriftviewer.deterministic.projection
        .ConfigDriftViewerDiffProjection;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source
        .ConfigDriftViewerDeterministicBuildResult;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source
        .ConfigDriftViewerDeterministicContextService;
import pl.mkn.tdw.features.configdriftviewer.job.api
        .ConfigDriftViewerMode;
import pl.mkn.tdw.features.configdriftviewer.job.export
        .ConfigDriftViewerSnapshotSanitizer;
import pl.mkn.tdw.features.configdriftviewer.scope.ConfigDriftViewerScopeResolver;
import pl.mkn.tdw.features.configdriftviewer.workbench.api
        .ConfigDriftViewerWorkbenchAiInputResponse;
import pl.mkn.tdw.features.configdriftviewer.workbench.api
        .ConfigDriftViewerWorkbenchAnonymizationPage;
import pl.mkn.tdw.features.configdriftviewer.workbench.api
        .ConfigDriftViewerWorkbenchAnonymizationPage.ValueRepresentation;
import pl.mkn.tdw.features.configdriftviewer.workbench.api
        .ConfigDriftViewerWorkbenchArtifactResponse;
import pl.mkn.tdw.features.configdriftviewer.workbench.api
        .ConfigDriftViewerWorkbenchConfigurationDiffResponse;
import pl.mkn.tdw.features.configdriftviewer.workbench.api
        .ConfigDriftViewerWorkbenchDeepResponse;
import pl.mkn.tdw.features.configdriftviewer.workbench.api
        .ConfigDriftViewerWorkbenchMappingPage;
import pl.mkn.tdw.features.configdriftviewer.workbench.api
        .ConfigDriftViewerWorkbenchPreviewRequest;
import pl.mkn.tdw.features.configdriftviewer.workbench.api
        .ConfigDriftViewerWorkbenchPreviewResponse;
import pl.mkn.tdw.features.configdriftviewer.workbench.api
        .ConfigDriftViewerWorkbenchSourceResponse;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigDriftViewerWorkbenchPreviewService {

    private static final String DEEP_FAILURE_LIMIT =
            "DEEP enrichment did not complete; deterministic projection and sanitized AI input remain available.";

    private final ConfigDriftViewerScopeResolver scopeResolver;
    private final ConfigDriftViewerDeterministicContextService deterministicContextService;
    private final ConfigDriftViewerDeepContextService deepContextService;
    private final ConfigDriftViewerPromptPreparationService promptPreparationService;
    private final ConfigDriftViewerWorkbenchPreviewStore previewStore;

    public ConfigDriftViewerWorkbenchPreviewResponse preview(
            ConfigDriftViewerWorkbenchPreviewRequest request
    ) {
        var scope = scopeResolver.resolve(request.repositoryId(), request.systemId());
        var deterministicBuild = buildDeterministic(request, scope);
        var deterministic = deterministicBuild.context();
        var configurationDiff = deterministicBuild.configurationDiff();
        var visibilityLimits = new LinkedHashSet<String>();
        ConfigDriftViewerDeepContext deepContext = null;
        ConfigDriftViewerPromptPreparation preparation = null;
        List<ConfigDriftViewerWorkbenchMappingPage.Item> mappingItems = List.of();
        List<ConfigDriftViewerWorkbenchAnonymizationPage.Item> anonymizationItems = List.of();
        if (request.mode() == ConfigDriftViewerMode.DEEP) {
            deepContext = buildDeepContext(request, deterministic, visibilityLimits);
            preparation = preparePrompt(request, deterministic, deepContext);
            visibilityLimits.addAll(preparation.visibilityLimits());
            if (deepContext != null) {
                visibilityLimits.addAll(deepContext.visibilityLimits());
            }
            mappingItems = mappingItems(deterministic.documents(), configurationDiff);
            anonymizationItems = anonymizationItems(deterministic.documents());
        }

        var stored = previewStore.store(new ConfigDriftViewerWorkbenchPreviewSnapshot(
                request.mode(),
                deterministic,
                configurationDiff,
                deepContext,
                preparation,
                mappingItems,
                anonymizationItems
        ));

        return new ConfigDriftViewerWorkbenchPreviewResponse(
                stored.previewId(),
                stored.expiresAt(),
                request.mode(),
                request.repositoryId(),
                request.systemId(),
                request.sourceBranch(),
                request.targetBranch(),
                request.codeRef(),
                sourceSummary(deterministic),
                new ConfigDriftViewerWorkbenchPreviewResponse.Counts(
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
                        .map(entry -> new ConfigDriftViewerWorkbenchPreviewResponse.ArtifactSummary(
                                entry.getKey(),
                                mediaType(entry.getKey()),
                                entry.getValue().length(),
                                truncated(entry.getValue())
                        ))
                        .toList() : List.of(),
                List.copyOf(visibilityLimits)
        );
    }

    public ConfigDriftViewerWorkbenchSourceResponse source(String previewId) {
        var snapshot = previewStore.require(previewId);
        return new ConfigDriftViewerWorkbenchSourceResponse(
                previewId,
                snapshot.deterministic().configurationDirectory(),
                snapshot.deterministic().sourceCoverage(),
                snapshot.deterministic().targetCoverage()
        );
    }

    public ConfigDriftViewerWorkbenchConfigurationDiffResponse configurationDiff(
            String previewId
    ) {
        var snapshot = previewStore.require(previewId);
        return new ConfigDriftViewerWorkbenchConfigurationDiffResponse(
                previewId,
                snapshot.configurationDiff()
        );
    }

    public ConfigDriftViewerWorkbenchMappingPage mapping(
            String previewId,
            int offset,
            int limit,
            boolean changedOnly
    ) {
        var snapshot = previewStore.require(previewId);
        var filtered = changedOnly
                ? snapshot.mappingItems().stream()
                        .filter(item -> item.changeKind() != ConfigDriftViewerChangeKind.UNCHANGED)
                        .toList()
                : snapshot.mappingItems();
        var page = page(filtered, offset, limit);
        return new ConfigDriftViewerWorkbenchMappingPage(
                previewId,
                page.offset(),
                page.limit(),
                filtered.size(),
                snapshot.mappingItems().size(),
                changedOnly,
                page.items()
        );
    }

    public ConfigDriftViewerWorkbenchAnonymizationPage anonymization(
            String previewId,
            int offset,
            int limit
    ) {
        var snapshot = previewStore.require(previewId);
        var page = page(snapshot.anonymizationItems(), offset, limit);
        return new ConfigDriftViewerWorkbenchAnonymizationPage(
                previewId,
                page.offset(),
                page.limit(),
                snapshot.anonymizationItems().size(),
                page.items()
        );
    }

    public ConfigDriftViewerWorkbenchDeepResponse deep(String previewId) {
        var snapshot = previewStore.require(previewId);
        return new ConfigDriftViewerWorkbenchDeepResponse(
                previewId,
                snapshot.mode() == ConfigDriftViewerMode.DEEP,
                snapshot.deepContext()
        );
    }

    public ConfigDriftViewerWorkbenchAiInputResponse aiInput(String previewId) {
        var snapshot = previewStore.require(previewId);
        if (snapshot.preparation() == null) {
            return new ConfigDriftViewerWorkbenchAiInputResponse(
                    previewId,
                    false,
                    0,
                    null
            );
        }
        return new ConfigDriftViewerWorkbenchAiInputResponse(
                previewId,
                true,
                snapshot.preparation().prompt().length(),
                snapshot.preparation().prompt()
        );
    }

    public ConfigDriftViewerWorkbenchArtifactResponse artifact(
            String previewId,
            String name
    ) {
        var snapshot = previewStore.require(previewId);
        var content = snapshot.preparation() != null
                ? snapshot.preparation().artifactContents().get(name)
                : null;
        if (content == null) {
            throw new ConfigDriftViewerWorkbenchPreviewNotFoundException();
        }
        return new ConfigDriftViewerWorkbenchArtifactResponse(
                previewId,
                name,
                mediaType(name),
                content.length(),
                truncated(content),
                content
        );
    }

    private ConfigDriftViewerDeterministicBuildResult buildDeterministic(
            ConfigDriftViewerWorkbenchPreviewRequest request,
            pl.mkn.tdw.features.configdriftviewer.scope.ConfigDriftViewerScope scope
    ) {
        try {
            var build = deterministicContextService.build(
                    scope,
                    request.sourceBranch(),
                    request.targetBranch()
            );
            return new ConfigDriftViewerDeterministicBuildResult(
                    ConfigDriftViewerSnapshotSanitizer.sanitize(build.context()),
                    build.configurationDiff()
            );
        } catch (RuntimeException exception) {
            throw safeFailure("deterministic", exception);
        }
    }

    private ConfigDriftViewerPromptPreparation preparePrompt(
            ConfigDriftViewerWorkbenchPreviewRequest request,
            ConfigDriftViewerDeterministicContext deterministic,
            ConfigDriftViewerDeepContext deepContext
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

    private ConfigDriftViewerWorkbenchPreviewException safeFailure(
            String stage,
            RuntimeException exception
    ) {
        log.warn(
                "Runtime configuration workbench preview failed stage={} failureType={}",
                stage,
                exception.getClass().getSimpleName()
        );
        return new ConfigDriftViewerWorkbenchPreviewException();
    }

    private ConfigDriftViewerDeepContext buildDeepContext(
            ConfigDriftViewerWorkbenchPreviewRequest request,
            ConfigDriftViewerDeterministicContext deterministic,
            LinkedHashSet<String> visibilityLimits
    ) {
        if (request.mode() != ConfigDriftViewerMode.DEEP) {
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

    private ConfigDriftViewerWorkbenchPreviewResponse.SourceSummary sourceSummary(
            ConfigDriftViewerDeterministicContext deterministic
    ) {
        var source = deterministic.sourceCoverage();
        var target = deterministic.targetCoverage();
        return new ConfigDriftViewerWorkbenchPreviewResponse.SourceSummary(
                deterministic.configurationDirectory(),
                source != null && source.branchExists(),
                source != null && source.complete(),
                target != null && target.branchExists(),
                target != null && target.complete()
        );
    }

    private ConfigDriftViewerWorkbenchPreviewResponse.DeepSummary deepSummary(
            ConfigDriftViewerMode mode,
            ConfigDriftViewerDeepContext deep
    ) {
        if (mode != ConfigDriftViewerMode.DEEP) {
            return new ConfigDriftViewerWorkbenchPreviewResponse.DeepSummary(
                    false, null, null, 0, 0, 0, 0
            );
        }
        var preflight = deep != null ? deep.preflight() : null;
        var ownership = deep != null ? deep.ownership() : null;
        return new ConfigDriftViewerWorkbenchPreviewResponse.DeepSummary(
                true,
                deep != null && deep.status() != null ? deep.status().name() : "UNAVAILABLE",
                preflight != null && preflight.status() != null ? preflight.status().name() : null,
                preflight != null ? preflight.repositories().size() : 0,
                preflight != null ? preflight.blockers().size() : 0,
                deep != null ? deep.codeGrounding().size() : 0,
                ownership != null ? ownership.primaryOwners().size() : 0
        );
    }

    private List<ConfigDriftViewerWorkbenchMappingPage.Item> mappingItems(
            List<SanitizedConfigurationDocument> documents,
            ConfigDriftViewerDiffProjection configurationDiff
    ) {
        var items = new ArrayList<ConfigDriftViewerWorkbenchMappingPage.Item>();
        for (var document : documents) {
            var projected = projectionDocument(configurationDiff, document);
            collectMapping(document, document.root(), projected.root(), 0, items);
        }
        return List.copyOf(items);
    }

    private void collectMapping(
            SanitizedConfigurationDocument document,
            SanitizedConfigurationNode sanitized,
            ConfigDriftViewerDiffNode original,
            int depth,
            List<ConfigDriftViewerWorkbenchMappingPage.Item> items
    ) {
        if (sanitized == null || original == null
                || sanitized.children().size() != original.children().size()) {
            throw new IllegalArgumentException(
                    "Operator projection does not match sanitized configuration tree"
            );
        }
        items.add(new ConfigDriftViewerWorkbenchMappingPage.Item(
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

    private ConfigDriftViewerDiffDocument projectionDocument(
            ConfigDriftViewerDiffProjection configurationDiff,
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

    private int nodeCount(ConfigDriftViewerDiffProjection configurationDiff) {
        return configurationDiff.files().stream()
                .flatMap(file -> file.documents().stream())
                .mapToInt(document -> nodeCount(document.root()))
                .sum();
    }

    private int nodeCount(ConfigDriftViewerDiffNode node) {
        return 1 + node.children().stream().mapToInt(this::nodeCount).sum();
    }

    private List<ConfigDriftViewerWorkbenchAnonymizationPage.Item> anonymizationItems(
            List<SanitizedConfigurationDocument> documents
    ) {
        var items = new ArrayList<ConfigDriftViewerWorkbenchAnonymizationPage.Item>();
        for (var document : documents) {
            collectAnonymization(document, document.root(), items);
        }
        return List.copyOf(items);
    }

    private void collectAnonymization(
            SanitizedConfigurationDocument document,
            SanitizedConfigurationNode node,
            List<ConfigDriftViewerWorkbenchAnonymizationPage.Item> items
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
        items.add(new ConfigDriftViewerWorkbenchAnonymizationPage.Item(
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

    private ConfigDriftViewerWorkbenchPreviewResponse.AnonymizationSummary
            anonymizationSummary(
            List<ConfigDriftViewerWorkbenchAnonymizationPage.Item> items
    ) {
        return new ConfigDriftViewerWorkbenchPreviewResponse.AnonymizationSummary(
                items.size(),
                count(items, ValueRepresentation.PSEUDONYMIZED),
                count(items, ValueRepresentation.SUPPRESSED),
                count(items, ValueRepresentation.STRUCTURE_ONLY),
                count(items, ValueRepresentation.NOT_PRESENT)
        );
    }

    private ValueRepresentation representation(
            ConfigDriftViewerValueType type,
            ConfigDriftViewerSensitivity sensitivity,
            String valueToken
    ) {
        if (type == null) {
            return ValueRepresentation.NOT_PRESENT;
        }
        if (sensitivity == ConfigDriftViewerSensitivity.SENSITIVE) {
            return ValueRepresentation.SUPPRESSED;
        }
        return valueToken != null
                ? ValueRepresentation.PSEUDONYMIZED
                : ValueRepresentation.STRUCTURE_ONLY;
    }

    private String safeToken(ConfigDriftViewerSensitivity sensitivity, String valueToken) {
        return sensitivity == ConfigDriftViewerSensitivity.SENSITIVE ? null : valueToken;
    }

    private int count(
            List<ConfigDriftViewerWorkbenchAnonymizationPage.Item> items,
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
