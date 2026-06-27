package pl.mkn.tdw.integrations.operationalcontext;

import org.springframework.util.StringUtils;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextBlastRadiusReadModel.FlowImpactView;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextBlastRadiusReadModel.ImpactNodeView;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextBlastRadiusReadModel.ImplementationImpactView;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextBlastRadiusReadModel.StepImpactView;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextDtos.OperationalContextProcess;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextFlowReadModel.FlowStepView;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextFlowReadModel.ImplementationRef;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.EntityKey;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.EntityRef;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.Provenance;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.SourceRef;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.ValidationFinding;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public class OperationalContextBlastRadiusReadModelBuilder {

    private static final String PROCESS = "process";
    private static final String SYSTEM = "system";
    private static final String REPOSITORY = "repository";
    private static final String CODE_SEARCH_SCOPE = "code-search-scope";
    private static final String BOUNDED_CONTEXT = "bounded-context";
    private static final String INTEGRATION = "integration";
    private static final String DATASTORE = "datastore";
    private static final String ENDPOINT = "endpoint";
    private static final String CLASS = "class";
    private static final String TABLE = "table";
    private static final String QUEUE = "queue";
    private static final String TOPIC = "topic";

    private final OperationalContextRelationIndexBuilder relationIndexBuilder;
    private final OperationalContextFlowReadModelBuilder flowReadModelBuilder;

    public OperationalContextBlastRadiusReadModelBuilder() {
        this(new OperationalContextRelationIndexBuilder());
    }

    public OperationalContextBlastRadiusReadModelBuilder(
            OperationalContextRelationIndexBuilder relationIndexBuilder
    ) {
        this.relationIndexBuilder = relationIndexBuilder;
        this.flowReadModelBuilder = new OperationalContextFlowReadModelBuilder(relationIndexBuilder);
    }

    public OperationalContextBlastRadiusReadModel buildForEntity(
            OperationalContextCatalog catalog,
            String entityType,
            String entityId
    ) {
        var safeCatalog = catalog != null ? catalog : OperationalContextCatalog.empty();
        var target = new EntityKey(normalizeEntityType(entityType), entityId);
        var relationIndex = relationIndexBuilder.build(safeCatalog);
        var analysisTarget = relationIndex.entities().getOrDefault(target, EntityRef.fromKey(target));
        var findings = new ArrayList<ValidationFinding>();
        var flowImpacts = new ArrayList<FlowImpactView>();
        var limitations = new ArrayList<String>();

        for (var process : safeCatalog.processes()) {
            var flow = flowReadModelBuilder.buildForEntity(safeCatalog, PROCESS, process.id());
            findings.addAll(flow.validationFindings());
            var matchedStepOrders = matchedStepOrders(target, flow.steps());
            if (PROCESS.equals(target.type()) && process.id().equals(target.id())) {
                matchedStepOrders = allStepOrders(flow.steps());
            }
            if (matchedStepOrders.isEmpty() && processMatchesTarget(safeCatalog, target, process)) {
                matchedStepOrders = flow.steps().isEmpty() ? List.of() : List.of(flow.steps().get(0).order());
            }
            if (matchedStepOrders.isEmpty()) {
                continue;
            }

            var firstImpactedOrder = matchedStepOrders.stream().mapToInt(Integer::intValue).min().orElse(1);
            var directStepOrders = List.copyOf(matchedStepOrders);
            var impactedSteps = flow.steps().stream()
                    .filter(step -> step.order() >= firstImpactedOrder)
                    .map(step -> stepImpact(target, step, directStepOrders.contains(step.order())))
                    .toList();
            var downstreamEdges = flow.edges().stream()
                    .filter(edge -> impactedSteps.stream().anyMatch(step -> step.stepId().equals(edge.sourceStepId())))
                    .toList();
            flowImpacts.add(new FlowImpactView(
                    flow.analysisTarget(),
                    impactedSteps,
                    downstreamEdges,
                    confidenceFor(target, matchedStepOrders),
                    reasonsFor(target, matchedStepOrders),
                    new Provenance(
                            false,
                            "derived-from-flow-read-model",
                            "medium",
                            List.of(sourceRef(process.id(), "flow-impact")),
                            List.of()
                    )
            ));
            limitations.addAll(flow.limitations());
        }

        if (flowImpacts.isEmpty()) {
            findings.add(new ValidationFinding(
                    "warning",
                    "BLAST_RADIUS_NO_MATCHING_FLOW",
                    "No flow step or process matched blast-radius target " + target.value() + ".",
                    List.of()
            ));
        }

        return new OperationalContextBlastRadiusReadModel(
                "operational-context.blast-radius",
                1,
                OperationalContextCodeSearchReadModel.ReadModelProfile.defaultProfile(),
                analysisTarget,
                flowImpacts,
                impactedNodes(flowImpacts, ImpactEntityField.SYSTEMS),
                impactedNodes(flowImpacts, ImpactEntityField.BOUNDED_CONTEXTS),
                impactedNodes(flowImpacts, ImpactEntityField.INTEGRATIONS),
                impactedNodes(flowImpacts, ImpactEntityField.DATA_STORES),
                impactedImplementations(flowImpacts),
                suggestedNextEvidence(target, flowImpacts),
                limitations,
                distinctFindings(findings)
        );
    }

    private StepImpactView stepImpact(
            EntityKey target,
            FlowStepView step,
            boolean directMatch
    ) {
        return new StepImpactView(
                step.id(),
                step.order(),
                step.name(),
                step.kind(),
                directMatch ? "direct-hit" : "downstream",
                directMatch ? reasonsFor(target, List.of(step.order())) : List.of("Downstream step in impacted flow."),
                step.systems(),
                step.boundedContexts(),
                step.integrations(),
                step.dataStores(),
                step.implementations()
        );
    }

    private List<Integer> matchedStepOrders(
            EntityKey target,
            List<FlowStepView> steps
    ) {
        return steps.stream()
                .filter(step -> stepMatchesTarget(target, step))
                .map(FlowStepView::order)
                .toList();
    }

    private boolean stepMatchesTarget(
            EntityKey target,
            FlowStepView step
    ) {
        return switch (target.type()) {
            case SYSTEM -> containsEntity(step.systems(), target);
            case REPOSITORY -> step.implementations().stream()
                    .anyMatch(implementation -> implementation.repository() != null
                            && REPOSITORY.equals(implementation.repository().type())
                            && target.id().equals(implementation.repository().id()));
            case CODE_SEARCH_SCOPE -> containsEntity(step.codeSearchScopes(), target);
            case BOUNDED_CONTEXT -> containsEntity(step.boundedContexts(), target);
            case INTEGRATION -> containsEntity(step.integrations(), target);
            case DATASTORE -> containsEntity(step.dataStores(), target);
            case ENDPOINT -> containsSignal(step.endpointHints(), target.id())
                    || containsSignal(step.integrationHints().endpoints(), target.id());
            case CLASS -> containsSignal(step.classHints(), target.id())
                    || containsSignal(step.codeHints().classHints(), target.id())
                    || containsSignal(step.integrationHints().classHints(), target.id());
            case TABLE -> containsSignal(step.codeHints().databaseHints().tables(), target.id())
                    || containsSignal(step.integrationHints().tables(), target.id());
            case QUEUE -> containsSignal(step.queueTopicHints(), target.id())
                    || containsSignal(step.integrationHints().queues(), target.id());
            case TOPIC -> containsSignal(step.queueTopicHints(), target.id())
                    || containsSignal(step.integrationHints().topics(), target.id());
            default -> false;
        };
    }

    private boolean processMatchesTarget(
            OperationalContextCatalog catalog,
            EntityKey target,
            OperationalContextProcess process
    ) {
        return switch (target.type()) {
            case SYSTEM -> process.participants().primarySystems().contains(target.id())
                    || process.participants().supportingSystems().contains(target.id())
                    || process.participants().externalSystems().contains(target.id())
                    || process.references().systems().contains(target.id());
            case REPOSITORY -> process.references().repositories().contains(target.id());
            case CODE_SEARCH_SCOPE -> processUsesCodeSearchScope(catalog, target.id(), process);
            case BOUNDED_CONTEXT -> process.references().boundedContexts().contains(target.id());
            case INTEGRATION -> process.references().integrations().contains(target.id());
            case DATASTORE -> process.references().dataStores().contains(target.id());
            case ENDPOINT -> process.matchSignals().allValues().stream().anyMatch(value -> signalMatches(value, target.id()));
            case CLASS -> process.matchSignals().valuesForKeys("classHints", "entrypoints").stream()
                    .anyMatch(value -> signalMatches(value, target.id()));
            case TABLE -> process.matchSignals().valuesForKeys("tables").stream()
                    .anyMatch(value -> signalMatches(value, target.id()));
            case QUEUE -> process.matchSignals().valuesForKeys("queues", "queueNames").stream()
                    .anyMatch(value -> signalMatches(value, target.id()));
            case TOPIC -> process.matchSignals().valuesForKeys("topics", "exchanges").stream()
                    .anyMatch(value -> signalMatches(value, target.id()));
            default -> false;
        };
    }

    private boolean processUsesCodeSearchScope(
            OperationalContextCatalog catalog,
            String scopeId,
            OperationalContextProcess process
    ) {
        return catalog.codeSearchScopes().stream()
                .filter(scope -> scopeId.equals(scope.id()))
                .anyMatch(scope -> switch (normalizeEntityType(scope.target().type())) {
                    case PROCESS -> process.id().equals(scope.target().id());
                    case SYSTEM -> process.participants().primarySystems().contains(scope.target().id())
                            || process.references().systems().contains(scope.target().id());
                    case BOUNDED_CONTEXT -> process.references().boundedContexts().contains(scope.target().id());
                    case INTEGRATION -> process.references().integrations().contains(scope.target().id());
                    default -> false;
                });
    }

    private List<Integer> allStepOrders(List<FlowStepView> steps) {
        return steps.stream().map(FlowStepView::order).toList();
    }

    private List<ImpactNodeView> impactedNodes(
            List<FlowImpactView> flowImpacts,
            ImpactEntityField field
    ) {
        var result = new LinkedHashMap<String, ImpactNodeView>();
        for (var flowImpact : flowImpacts) {
            for (var step : flowImpact.impactedSteps()) {
                for (var entity : entities(step, field)) {
                    result.putIfAbsent(entity.type() + ":" + entity.id(), new ImpactNodeView(
                            entity,
                            "downstream",
                            "downstream",
                            "unknown",
                            "medium",
                            List.of("Entity appears in impacted flow step " + step.stepId() + "."),
                            flowImpact.provenance()
                    ));
                }
            }
        }
        return List.copyOf(result.values());
    }

    private List<EntityRef> entities(
            StepImpactView step,
            ImpactEntityField field
    ) {
        return switch (field) {
            case SYSTEMS -> step.systems();
            case BOUNDED_CONTEXTS -> step.boundedContexts();
            case INTEGRATIONS -> step.integrations();
            case DATA_STORES -> step.dataStores();
        };
    }

    private List<ImplementationImpactView> impactedImplementations(List<FlowImpactView> flowImpacts) {
        var result = new LinkedHashMap<String, ImplementationImpactView>();
        for (var flowImpact : flowImpacts) {
            for (var step : flowImpact.impactedSteps()) {
                for (var implementation : step.implementations()) {
                    result.putIfAbsent(implementation.id(), new ImplementationImpactView(
                            implementation,
                            "downstream-code",
                            "medium",
                            List.of("Implementation appears in impacted flow step " + step.stepId() + ".")
                    ));
                }
            }
        }
        return List.copyOf(result.values());
    }

    private List<String> suggestedNextEvidence(
            EntityKey target,
            List<FlowImpactView> flowImpacts
    ) {
        var suggestions = new LinkedHashSet<String>();
        if (flowImpacts.isEmpty()) {
            suggestions.add("Add or refine flow steps, code-search scope targets, or integration/data-store references for " + target.value() + ".");
            return List.copyOf(suggestions);
        }

        suggestions.add("Inspect impacted flow steps before reading broad raw catalog entries.");
        suggestions.add("Use code-search scopes from impacted implementations to fetch targeted source code.");
        if (flowImpacts.stream().flatMap(flow -> flow.impactedSteps().stream()).anyMatch(step -> !step.dataStores().isEmpty())) {
            suggestions.add("For data symptoms, validate tables/entities with DB tools after code grounding.");
        }
        if (flowImpacts.stream().flatMap(flow -> flow.impactedSteps().stream()).anyMatch(step -> !step.integrations().isEmpty())) {
            suggestions.add("For integration symptoms, verify source/target systems and transport-specific failure modes.");
        }
        return List.copyOf(suggestions);
    }

    private List<String> reasonsFor(EntityKey target, List<Integer> matchedStepOrders) {
        if (matchedStepOrders.isEmpty()) {
            return List.of("Target " + target.value() + " matched process-level facts.");
        }
        return List.of("Target " + target.value() + " matched flow step order " + matchedStepOrders.get(0) + ".");
    }

    private String confidenceFor(EntityKey target, List<Integer> matchedStepOrders) {
        if (SetOf.MODELED_ENTITY_TYPES.contains(target.type()) && !matchedStepOrders.isEmpty()) {
            return "high";
        }
        return matchedStepOrders.isEmpty() ? "medium" : "medium";
    }

    private boolean containsEntity(List<EntityRef> refs, EntityKey target) {
        return refs.stream().anyMatch(ref -> ref.type().equals(target.type()) && ref.id().equals(target.id()));
    }

    private boolean intersects(List<String> left, List<String> right) {
        var rightValues = new LinkedHashSet<>(right);
        return left.stream().anyMatch(rightValues::contains);
    }

    private boolean containsSignal(List<String> values, String target) {
        return values.stream().anyMatch(value -> signalMatches(value, target));
    }

    private boolean signalMatches(String value, String target) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(target)) {
            return false;
        }
        var normalizedValue = value.trim().toLowerCase();
        var normalizedTarget = target.trim().toLowerCase();
        return normalizedValue.contains(normalizedTarget) || normalizedTarget.contains(normalizedValue);
    }

    private SourceRef sourceRef(String processId, String relationRole) {
        return new SourceRef(
                "src/main/resources/operational-context/processes.yml",
                PROCESS,
                processId,
                "$.processes[id=" + processId + "]",
                relationRole
        );
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
            case "repositories", "repo", "repos" -> REPOSITORY;
            case "codeSearchScope", "codeSearchScopes", "code-search-scopes",
                 "codesearchscope", "codesearchscopes" -> CODE_SEARCH_SCOPE;
            case "boundedContext", "boundedContexts", "bounded-contexts" -> BOUNDED_CONTEXT;
            case "integrations" -> INTEGRATION;
            case "dataStores", "data-stores" -> DATASTORE;
            case "endpoints" -> ENDPOINT;
            case "classes" -> CLASS;
            case "tables" -> TABLE;
            case "queues" -> QUEUE;
            case "topics", "exchanges" -> TOPIC;
            default -> normalized;
        };
    }

    private enum ImpactEntityField {
        SYSTEMS,
        BOUNDED_CONTEXTS,
        INTEGRATIONS,
        DATA_STORES
    }

    private static final class SetOf {

        private static final java.util.Set<String> MODELED_ENTITY_TYPES = java.util.Set.of(
                PROCESS,
                SYSTEM,
                REPOSITORY,
                CODE_SEARCH_SCOPE,
                BOUNDED_CONTEXT,
                INTEGRATION,
                DATASTORE
        );
    }
}
