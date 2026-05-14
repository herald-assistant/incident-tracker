package pl.mkn.incidenttracker.integrations.operationalcontext;

import org.springframework.util.StringUtils;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCodeSearchReadModel.CodeSearchHints;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCodeSearchReadModel.DatabaseHints;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextCodeSearchReadModel.WorkflowHints;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextIntegration;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextProcess;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextDtos.OperationalContextProcessStep;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextImplementationReadModel.ImplementationView;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextFlowReadModel.FlowEdgeView;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextFlowReadModel.FlowStepView;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextFlowReadModel.FlowTriggerView;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextFlowReadModel.ImplementationRef;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextFlowReadModel.IntegrationHints;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.EntityKey;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.EntityRef;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.Provenance;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.SourceRef;
import pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextRelationIndex.ValidationFinding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.mapList;
import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.text;
import static pl.mkn.incidenttracker.integrations.operationalcontext.OperationalContextMaps.textList;

public class OperationalContextFlowReadModelBuilder {

    private static final String PROCESS = "process";
    private static final String SYSTEM = "system";
    private static final String BOUNDED_CONTEXT = "bounded-context";
    private static final String INTEGRATION = "integration";
    private static final String DATASTORE = "datastore";

    private final OperationalContextRelationIndexBuilder relationIndexBuilder;
    private final OperationalContextImplementationReadModelBuilder implementationReadModelBuilder;

    public OperationalContextFlowReadModelBuilder() {
        this(new OperationalContextRelationIndexBuilder());
    }

    public OperationalContextFlowReadModelBuilder(
            OperationalContextRelationIndexBuilder relationIndexBuilder
    ) {
        this.relationIndexBuilder = relationIndexBuilder;
        this.implementationReadModelBuilder = new OperationalContextImplementationReadModelBuilder(relationIndexBuilder);
    }

    public OperationalContextFlowReadModel buildForEntity(
            OperationalContextCatalog catalog,
            String entityType,
            String entityId
    ) {
        var safeCatalog = catalog != null ? catalog : OperationalContextCatalog.empty();
        var target = new EntityKey(normalizeEntityType(entityType), entityId);
        var relationIndex = relationIndexBuilder.build(safeCatalog);
        var analysisTarget = relationIndex.entities().getOrDefault(target, EntityRef.fromKey(target));
        var findings = new ArrayList<ValidationFinding>();

        if (!PROCESS.equals(target.type())) {
            findings.add(new ValidationFinding(
                    "warning",
                    "FLOW_MODEL_TARGET_NOT_PROCESS",
                    "Flow read model projection currently requires process target, got " + target.value() + ".",
                    List.of()
            ));
            return OperationalContextFlowReadModel.empty(analysisTarget, findings);
        }

        var process = processesById(safeCatalog).get(target.id());
        if (process == null) {
            findings.add(new ValidationFinding(
                    "warning",
                    "UNKNOWN_FLOW_PROCESS",
                    "No process found for flow read model target " + target.value() + ".",
                    List.of(sourceRef(target.id(), "$.processes[id=" + target.id() + "]", "flow-process"))
            ));
            return OperationalContextFlowReadModel.empty(analysisTarget, findings);
        }

        var implementationModel = implementationReadModelBuilder.buildForEntity(safeCatalog, PROCESS, process.id());
        findings.addAll(implementationModel.validationFindings());
        if (process.steps().isEmpty()) {
            findings.add(new ValidationFinding(
                    "warning",
                    "FLOW_WITHOUT_STEPS",
                    "Process " + process.id() + " has no ordered steps for flow projection.",
                    List.of(sourceRef(process.id(), "$.processes[id=" + process.id() + "].steps", "flow-steps"))
            ));
        }

        var integrationsById = integrationsById(safeCatalog);
        var steps = new ArrayList<FlowStepView>();
        for (var index = 0; index < process.steps().size(); index++) {
            var step = process.steps().get(index);
            steps.add(stepView(
                    relationIndex,
                    process,
                    step,
                    index + 1,
                    implementationModel.implementations(),
                    integrationsById,
                    findings
            ));
        }

        return new OperationalContextFlowReadModel(
                "operational-context.flow",
                1,
                implementationModel.profile(),
                analysisTarget,
                triggerView(relationIndex, process, steps),
                steps,
                edges(process, steps),
                involved(steps, FlowEntityField.SYSTEMS),
                involved(steps, FlowEntityField.BOUNDED_CONTEXTS),
                involved(steps, FlowEntityField.INTEGRATIONS),
                involved(steps, FlowEntityField.DATA_STORES),
                implementationModel.limitations(),
                distinctFindings(findings)
        );
    }

    private FlowStepView stepView(
            OperationalContextRelationIndex relationIndex,
            OperationalContextProcess process,
            OperationalContextProcessStep step,
            int order,
            List<ImplementationView> processImplementations,
            Map<String, OperationalContextIntegration> integrationsById,
            List<ValidationFinding> findings
    ) {
        var systems = refs(relationIndex, SYSTEM, step.references().systems());
        if (systems.isEmpty()) {
            systems = refs(relationIndex, SYSTEM, process.participants().primarySystems());
        }
        var boundedContexts = refs(relationIndex, BOUNDED_CONTEXT, step.references().boundedContexts());
        if (boundedContexts.isEmpty()) {
            boundedContexts = refs(relationIndex, BOUNDED_CONTEXT, process.references().boundedContexts());
        }
        var integrations = refs(relationIndex, INTEGRATION, step.references().integrations());
        var dataStores = refs(relationIndex, DATASTORE, step.references().dataStores());
        var integrationHints = integrationHints(step.references().integrations(), integrationsById);
        var selectedImplementations = selectedImplementations(processImplementations, systems, boundedContexts);
        var implementationRefs = selectedImplementations.stream()
                .map(this::implementationRef)
                .toList();
        var codeSearchScopes = selectedImplementations.stream()
                .map(ImplementationView::codeSearchScope)
                .filter(scope -> scope != null)
                .distinct()
                .toList();
        var endpointHints = distinct(
                step.matchSignals().valuesForKeys("endpointPrefixes", "endpointTemplates", "endpoints", "endpointHints"),
                textList(step.payload(), "endpoint")
        );
        var queueTopicHints = distinct(
                step.matchSignals().valuesForKeys("queues", "queueNames", "topics", "exchanges", "routingKeys"),
                textList(step.payload(), "queue"),
                textList(step.payload(), "topic")
        );
        var classHints = step.matchSignals().valuesForKeys("classHints", "entrypoints");
        var codeHints = codeHints(step, selectedImplementations, endpointHints, queueTopicHints, classHints);
        var kind = stepKind(step, endpointHints, queueTopicHints, dataStores, integrations);
        var gaps = stepGaps(kind, endpointHints, queueTopicHints, classHints, dataStores, integrations, implementationRefs);

        if (!gaps.isEmpty()) {
            findings.add(new ValidationFinding(
                    "warning",
                    "FLOW_STEP_WITH_LIMITED_TECHNICAL_ANCHOR",
                    "Flow step " + process.id() + "/" + step.id() + " has limited technical anchors: " + String.join("; ", gaps),
                    List.of(stepSourceRef(process, step, "flow-step"))
            ));
        }

        return new FlowStepView(
                step.id(),
                order,
                step.name(),
                kind,
                step.summary(),
                systems,
                boundedContexts,
                integrations,
                dataStores,
                implementationRefs,
                codeSearchScopes,
                endpointHints,
                queueTopicHints,
                classHints,
                codeHints,
                integrationHints,
                gaps,
                new Provenance(
                        false,
                        "derived-from-process-step-and-implementation-read-model",
                        "medium",
                        List.of(stepSourceRef(process, step, "flow-step")),
                        List.of()
                )
        );
    }

    private FlowTriggerView triggerView(
            OperationalContextRelationIndex relationIndex,
            OperationalContextProcess process,
            List<FlowStepView> steps
    ) {
        var triggerMaps = mapList(process.payload(), "lifecycle.triggers");
        var endpoints = new ArrayList<String>();
        var queues = new ArrayList<String>();
        var topics = new ArrayList<String>();
        var kind = text(triggerMaps.stream()
                .map(trigger -> text(trigger, "type"))
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null));

        for (var trigger : triggerMaps) {
            endpoints.addAll(textList(trigger, "endpoint"));
            queues.addAll(textList(trigger, "queue"));
            topics.addAll(textList(trigger, "topic"));
            topics.addAll(textList(trigger, "exchange"));
        }

        if (endpoints.isEmpty() && !steps.isEmpty()) {
            endpoints.addAll(steps.get(0).endpointHints());
        }
        if (queues.isEmpty() && !steps.isEmpty()) {
            queues.addAll(steps.get(0).queueTopicHints());
        }
        if (topics.isEmpty() && !steps.isEmpty()) {
            topics.addAll(steps.get(0).queueTopicHints());
        }

        var channel = channel(kind, endpoints, queues, topics);
        return new FlowTriggerView(
                firstNonBlank(kind, "process"),
                channel,
                distinct(endpoints),
                distinct(queues),
                distinct(topics),
                refs(relationIndex, SYSTEM, process.participants().externalSystems()),
                refs(relationIndex, SYSTEM, process.participants().primarySystems()),
                new Provenance(
                        false,
                        triggerMaps.isEmpty() ? "derived-from-first-flow-step" : "derived-from-process-lifecycle-triggers",
                        triggerMaps.isEmpty() ? "medium" : "high",
                        List.of(sourceRef(process.id(), "$.processes[id=" + process.id() + "].lifecycle.triggers", "flow-trigger")),
                        List.of()
                )
        );
    }

    private List<FlowEdgeView> edges(
            OperationalContextProcess process,
            List<FlowStepView> steps
    ) {
        var edges = new ArrayList<FlowEdgeView>();
        for (var index = 0; index < steps.size() - 1; index++) {
            var source = steps.get(index);
            var target = steps.get(index + 1);
            edges.add(new FlowEdgeView(
                    source.id(),
                    target.id(),
                    "next-step",
                    viaEntities(target),
                    new Provenance(
                            false,
                            "derived-from-process-step-order",
                            "medium",
                            List.of(sourceRef(process.id(), "$.processes[id=" + process.id() + "].steps", "flow-step-order")),
                            List.of()
                    )
            ));
        }
        return edges;
    }

    private List<EntityRef> viaEntities(FlowStepView target) {
        var result = new LinkedHashMap<String, EntityRef>();
        addRefs(result, target.integrations());
        addRefs(result, target.dataStores());
        addRefs(result, target.systems());
        addRefs(result, target.boundedContexts());
        return List.copyOf(result.values());
    }

    private List<ImplementationView> selectedImplementations(
            List<ImplementationView> processImplementations,
            List<EntityRef> systems,
            List<EntityRef> boundedContexts
    ) {
        var selected = processImplementations.stream()
                .filter(implementation -> "implementation".equals(implementation.implementationKind()))
                .filter(implementation -> systems.isEmpty() || intersects(implementation.systems(), systems))
                .filter(implementation -> boundedContexts.isEmpty() || intersects(implementation.boundedContexts(), boundedContexts))
                .toList();

        if (!selected.isEmpty()) {
            return selected;
        }

        return processImplementations.stream()
                .filter(implementation -> "implementation".equals(implementation.implementationKind()))
                .filter(implementation -> "primary".equals(implementation.lifecycleRole())
                        || "target-implementation".equals(implementation.lifecycleRole())
                        || "source-implementation".equals(implementation.lifecycleRole()))
                .sorted(Comparator.comparing(implementation -> implementation.priority() != null
                        ? implementation.priority()
                        : Integer.MAX_VALUE))
                .limit(2)
                .toList();
    }

    private ImplementationRef implementationRef(ImplementationView implementation) {
        return new ImplementationRef(
                implementation.id(),
                implementation.lifecycleRole(),
                implementation.migrationStatus(),
                implementation.codeSearchScope(),
                implementation.repository(),
                implementation.module(),
                implementation.packagePrefixes(),
                implementation.sourceRoots(),
                implementation.hints()
        );
    }

    private CodeSearchHints codeHints(
            OperationalContextProcessStep step,
            List<ImplementationView> implementations,
            List<String> endpointHints,
            List<String> queueTopicHints,
            List<String> classHints
    ) {
        return new CodeSearchHints(
                distinct(
                        step.matchSignals().valuesForKeys("packagePrefixes"),
                        implementations.stream().map(ImplementationView::packagePrefixes).flatMap(List::stream).toList()
                ),
                distinct(
                        classHints,
                        implementations.stream().map(implementation -> implementation.hints().classHints()).flatMap(List::stream).toList()
                ),
                distinct(
                        endpointHints,
                        implementations.stream().map(implementation -> implementation.hints().endpointHints()).flatMap(List::stream).toList()
                ),
                distinct(
                        queueTopicHints,
                        implementations.stream().map(implementation -> implementation.hints().queueTopicHints()).flatMap(List::stream).toList()
                ),
                new DatabaseHints(
                        distinct(step.matchSignals().valuesForKeys("datasourceNames")),
                        List.of(),
                        distinct(step.matchSignals().valuesForKeys("schemas")),
                        distinct(step.matchSignals().valuesForKeys("tables")),
                        distinct(step.matchSignals().valuesForKeys("entities")),
                        implementations.stream()
                                .map(implementation -> implementation.hints().databaseHints().migrations())
                                .flatMap(List::stream)
                                .distinct()
                                .toList()
                ),
                new WorkflowHints(List.of(), List.of(), List.of())
        );
    }

    private IntegrationHints integrationHints(
            List<String> integrationIds,
            Map<String, OperationalContextIntegration> integrationsById
    ) {
        var protocols = new ArrayList<String>();
        var methods = new ArrayList<String>();
        var endpoints = new ArrayList<String>();
        var queues = new ArrayList<String>();
        var topics = new ArrayList<String>();
        var datasourceNames = new ArrayList<String>();
        var schemas = new ArrayList<String>();
        var tables = new ArrayList<String>();
        var classHints = new ArrayList<String>();
        var failureModes = new ArrayList<String>();

        for (var integrationId : integrationIds) {
            var integration = integrationsById.get(integrationId);
            if (integration == null) {
                continue;
            }
            protocols.addAll(integration.transport().protocols());
            methods.addAll(integration.transport().http().methods());
            endpoints.addAll(integration.transport().http().endpointPrefixes());
            endpoints.addAll(integration.transport().http().endpointTemplates());
            queues.addAll(integration.transport().messaging().queues());
            topics.addAll(integration.transport().messaging().topics());
            topics.addAll(integration.transport().messaging().exchanges());
            datasourceNames.addAll(integration.transport().database().datasourceNames());
            schemas.addAll(integration.transport().database().schemas());
            tables.addAll(integration.transport().database().tables());
            classHints.addAll(integration.implementation().classHints());
            classHints.addAll(integration.implementation().clientClasses());
            classHints.addAll(integration.implementation().controllerClasses());
            classHints.addAll(integration.implementation().listenerClasses());
            failureModes.addAll(integration.failureModes());
        }

        return new IntegrationHints(
                protocols,
                methods,
                endpoints,
                queues,
                topics,
                datasourceNames,
                schemas,
                tables,
                classHints,
                failureModes
        );
    }

    private String stepKind(
            OperationalContextProcessStep step,
            List<String> endpointHints,
            List<String> queueTopicHints,
            List<EntityRef> dataStores,
            List<EntityRef> integrations
    ) {
        if (StringUtils.hasText(step.type())) {
            return step.type();
        }
        if (!dataStores.isEmpty() || !step.matchSignals().valuesForKeys("tables", "schemas", "datasourceNames").isEmpty()) {
            return "database-access";
        }
        if (!integrations.isEmpty()) {
            return "integration-call";
        }
        if (!endpointHints.isEmpty()) {
            return "http-endpoint";
        }
        if (!queueTopicHints.isEmpty()) {
            return "async-event";
        }
        return "code";
    }

    private List<String> stepGaps(
            String kind,
            List<String> endpointHints,
            List<String> queueTopicHints,
            List<String> classHints,
            List<EntityRef> dataStores,
            List<EntityRef> integrations,
            List<ImplementationRef> implementations
    ) {
        var gaps = new ArrayList<String>();
        if (implementations.isEmpty() && integrations.isEmpty() && dataStores.isEmpty()) {
            gaps.add("No implementation, integration or datastore reference was projected.");
        }
        if (("http-endpoint".equals(kind) || "request".equals(kind)) && endpointHints.isEmpty()) {
            gaps.add("HTTP-like step has no endpoint hints.");
        }
        if ("async-event".equals(kind) && queueTopicHints.isEmpty()) {
            gaps.add("Async-like step has no queue/topic hints.");
        }
        if ("code".equals(kind) && classHints.isEmpty()) {
            gaps.add("Code step has no class hints.");
        }
        return gaps;
    }

    private List<EntityRef> involved(
            List<FlowStepView> steps,
            FlowEntityField field
    ) {
        var result = new LinkedHashMap<String, EntityRef>();
        for (var step : steps) {
            switch (field) {
                case SYSTEMS -> addRefs(result, step.systems());
                case BOUNDED_CONTEXTS -> addRefs(result, step.boundedContexts());
                case INTEGRATIONS -> addRefs(result, step.integrations());
                case DATA_STORES -> addRefs(result, step.dataStores());
            }
        }
        return List.copyOf(result.values());
    }

    private void addRefs(Map<String, EntityRef> refs, List<EntityRef> source) {
        for (var ref : source) {
            refs.putIfAbsent(ref.type() + ":" + ref.id(), ref);
        }
    }

    private List<EntityRef> refs(
            OperationalContextRelationIndex relationIndex,
            String type,
            List<String> ids
    ) {
        return ids.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .map(id -> ref(relationIndex, type, id))
                .distinct()
                .toList();
    }

    private EntityRef ref(
            OperationalContextRelationIndex relationIndex,
            String type,
            String id
    ) {
        var key = new EntityKey(type, id);
        return relationIndex.entities().getOrDefault(key, EntityRef.fromKey(key));
    }

    private boolean intersects(List<EntityRef> left, List<EntityRef> right) {
        var rightKeys = right.stream()
                .map(ref -> ref.type() + ":" + ref.id())
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);
        return left.stream().anyMatch(ref -> rightKeys.contains(ref.type() + ":" + ref.id()));
    }

    private String channel(
            String kind,
            List<String> endpoints,
            List<String> queues,
            List<String> topics
    ) {
        var normalizedKind = StringUtils.hasText(kind) ? kind.trim().toLowerCase() : "";
        if (!endpoints.isEmpty() || normalizedKind.contains("api") || normalizedKind.contains("http")) {
            return "http";
        }
        if (!queues.isEmpty() || !topics.isEmpty() || normalizedKind.contains("event")) {
            return "messaging";
        }
        return "process";
    }

    private Map<String, OperationalContextProcess> processesById(OperationalContextCatalog catalog) {
        var result = new LinkedHashMap<String, OperationalContextProcess>();
        for (var process : catalog.processes()) {
            if (StringUtils.hasText(process.id())) {
                result.putIfAbsent(process.id(), process);
            }
        }
        return result;
    }

    private Map<String, OperationalContextIntegration> integrationsById(OperationalContextCatalog catalog) {
        var result = new LinkedHashMap<String, OperationalContextIntegration>();
        for (var integration : catalog.integrations()) {
            if (StringUtils.hasText(integration.id())) {
                result.putIfAbsent(integration.id(), integration);
            }
        }
        return result;
    }

    private SourceRef stepSourceRef(
            OperationalContextProcess process,
            OperationalContextProcessStep step,
            String relationRole
    ) {
        return sourceRef(
                process.id(),
                "$.processes[id=" + process.id() + "].steps[id=" + step.id() + "]",
                relationRole
        );
    }

    private SourceRef sourceRef(
            String processId,
            String fieldPath,
            String relationRole
    ) {
        return new SourceRef(
                "src/main/resources/operational-context/processes.yml",
                PROCESS,
                processId,
                fieldPath,
                relationRole
        );
    }

    @SafeVarargs
    private final List<String> distinct(List<String>... values) {
        var result = new LinkedHashSet<String>();
        for (var current : values) {
            if (current == null) {
                continue;
            }
            for (var value : current) {
                if (StringUtils.hasText(value)) {
                    result.add(value.trim());
                }
            }
        }
        return List.copyOf(result);
    }

    private List<ValidationFinding> distinctFindings(List<ValidationFinding> findings) {
        var result = new LinkedHashMap<String, ValidationFinding>();
        for (var finding : findings) {
            result.putIfAbsent(finding.code() + "|" + finding.message(), finding);
        }
        return List.copyOf(result.values());
    }

    private String normalizeEntityType(String entityType) {
        if (!StringUtils.hasText(entityType)) {
            return "";
        }
        var normalized = entityType.trim().replace("_", "-").replace(" ", "-");
        return switch (normalized) {
            case "processes" -> PROCESS;
            case "systems" -> SYSTEM;
            case "boundedContext", "boundedContexts", "bounded-contexts" -> BOUNDED_CONTEXT;
            case "integrations" -> INTEGRATION;
            case "dataStores", "data-stores" -> DATASTORE;
            default -> normalized;
        };
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (var value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private enum FlowEntityField {
        SYSTEMS,
        BOUNDED_CONTEXTS,
        INTEGRATIONS,
        DATA_STORES
    }
}
