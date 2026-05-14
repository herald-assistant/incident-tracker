package pl.mkn.incidenttracker.integrations.operationalcontext;

import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCodeSearchReadModel.CodeSearchHints;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCodeSearchReadModel.ReadModelProfile;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextImplementationReadModel.ModuleImplementationView;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.EntityRef;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.Provenance;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.ValidationFinding;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record OperationalContextFlowReadModel(
        String contract,
        int contractVersion,
        ReadModelProfile profile,
        EntityRef analysisTarget,
        FlowTriggerView trigger,
        List<FlowStepView> steps,
        List<FlowEdgeView> edges,
        List<EntityRef> involvedSystems,
        List<EntityRef> involvedBoundedContexts,
        List<EntityRef> involvedIntegrations,
        List<EntityRef> involvedDataStores,
        List<String> limitations,
        List<ValidationFinding> validationFindings
) {

    public OperationalContextFlowReadModel {
        contract = contract != null ? contract : "operational-context.flow";
        contractVersion = contractVersion > 0 ? contractVersion : 1;
        profile = profile != null ? profile : ReadModelProfile.defaultProfile();
        trigger = trigger != null ? trigger : FlowTriggerView.empty();
        steps = copyList(steps);
        edges = copyList(edges);
        involvedSystems = copyList(involvedSystems);
        involvedBoundedContexts = copyList(involvedBoundedContexts);
        involvedIntegrations = copyList(involvedIntegrations);
        involvedDataStores = copyList(involvedDataStores);
        limitations = copyTextList(limitations);
        validationFindings = copyList(validationFindings);
    }

    public static OperationalContextFlowReadModel empty(
            EntityRef analysisTarget,
            List<ValidationFinding> validationFindings
    ) {
        return new OperationalContextFlowReadModel(
                "operational-context.flow",
                1,
                ReadModelProfile.defaultProfile(),
                analysisTarget,
                FlowTriggerView.empty(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                validationFindings
        );
    }

    public record FlowTriggerView(
            String kind,
            String channel,
            List<String> endpoints,
            List<String> queues,
            List<String> topics,
            List<EntityRef> sources,
            List<EntityRef> targets,
            Provenance provenance
    ) {

        public FlowTriggerView {
            kind = textOrDefault(kind, "process");
            channel = textOrDefault(channel, "unknown");
            endpoints = copyTextList(endpoints);
            queues = copyTextList(queues);
            topics = copyTextList(topics);
            sources = copyList(sources);
            targets = copyList(targets);
            provenance = provenance != null
                    ? provenance
                    : new Provenance(false, "unknown", "unknown", List.of(), List.of());
        }

        public static FlowTriggerView empty() {
            return new FlowTriggerView(
                    "process",
                    "unknown",
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    new Provenance(false, "unknown", "unknown", List.of(), List.of())
            );
        }
    }

    public record FlowStepView(
            String id,
            int order,
            String name,
            String kind,
            String summary,
            List<EntityRef> systems,
            List<EntityRef> boundedContexts,
            List<EntityRef> integrations,
            List<EntityRef> dataStores,
            List<ImplementationRef> implementations,
            List<EntityRef> codeSearchScopes,
            List<String> endpointHints,
            List<String> queueTopicHints,
            List<String> classHints,
            CodeSearchHints codeHints,
            IntegrationHints integrationHints,
            List<String> gaps,
            Provenance provenance
    ) {

        public FlowStepView {
            id = textOrDefault(id, "unknown-step");
            name = textOrDefault(name, id);
            kind = textOrDefault(kind, "code");
            summary = text(summary);
            systems = copyList(systems);
            boundedContexts = copyList(boundedContexts);
            integrations = copyList(integrations);
            dataStores = copyList(dataStores);
            implementations = copyList(implementations);
            codeSearchScopes = copyList(codeSearchScopes);
            endpointHints = copyTextList(endpointHints);
            queueTopicHints = copyTextList(queueTopicHints);
            classHints = copyTextList(classHints);
            codeHints = codeHints != null ? codeHints : CodeSearchHints.empty();
            integrationHints = integrationHints != null ? integrationHints : IntegrationHints.empty();
            gaps = copyTextList(gaps);
            provenance = provenance != null
                    ? provenance
                    : new Provenance(false, "unknown", "unknown", List.of(), List.of());
        }
    }

    public record ImplementationRef(
            String id,
            String lifecycleRole,
            String migrationStatus,
            EntityRef codeSearchScope,
            EntityRef repository,
            ModuleImplementationView module,
            List<String> packagePrefixes,
            List<String> sourceRoots,
            CodeSearchHints hints
    ) {

        public ImplementationRef {
            id = textOrDefault(id, "unknown-implementation");
            lifecycleRole = textOrDefault(lifecycleRole, "unknown");
            migrationStatus = textOrDefault(migrationStatus, "unknown");
            packagePrefixes = copyTextList(packagePrefixes);
            sourceRoots = copyTextList(sourceRoots);
            hints = hints != null ? hints : CodeSearchHints.empty();
        }
    }

    public record IntegrationHints(
            List<String> protocols,
            List<String> methods,
            List<String> endpoints,
            List<String> queues,
            List<String> topics,
            List<String> datasourceNames,
            List<String> schemas,
            List<String> tables,
            List<String> classHints,
            List<String> failureModes
    ) {

        public IntegrationHints {
            protocols = copyTextList(protocols);
            methods = copyTextList(methods);
            endpoints = copyTextList(endpoints);
            queues = copyTextList(queues);
            topics = copyTextList(topics);
            datasourceNames = copyTextList(datasourceNames);
            schemas = copyTextList(schemas);
            tables = copyTextList(tables);
            classHints = copyTextList(classHints);
            failureModes = copyTextList(failureModes);
        }

        public static IntegrationHints empty() {
            return new IntegrationHints(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of()
            );
        }
    }

    public record FlowEdgeView(
            String sourceStepId,
            String targetStepId,
            String relationType,
            List<EntityRef> viaEntities,
            Provenance provenance
    ) {

        public FlowEdgeView {
            sourceStepId = textOrDefault(sourceStepId, "unknown-source-step");
            targetStepId = textOrDefault(targetStepId, "unknown-target-step");
            relationType = textOrDefault(relationType, "next-step");
            viaEntities = copyList(viaEntities);
            provenance = provenance != null
                    ? provenance
                    : new Provenance(false, "unknown", "unknown", List.of(), List.of());
        }
    }

    static <T> List<T> copyList(List<T> values) {
        return values != null ? List.copyOf(values) : List.of();
    }

    static List<String> copyTextList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(OperationalContextFlowReadModel::text)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    static Map<String, Object> copyMap(Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static String textOrDefault(String value, String defaultValue) {
        var normalized = text(value);
        return normalized != null ? normalized : defaultValue;
    }

    private static String text(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
