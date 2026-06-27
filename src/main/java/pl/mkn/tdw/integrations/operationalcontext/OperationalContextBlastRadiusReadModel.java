package pl.mkn.tdw.integrations.operationalcontext;

import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCodeSearchReadModel.ReadModelProfile;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextFlowReadModel.FlowEdgeView;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextFlowReadModel.ImplementationRef;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.EntityRef;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.Provenance;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.ValidationFinding;

import java.util.List;

public record OperationalContextBlastRadiusReadModel(
        String contract,
        int contractVersion,
        ReadModelProfile profile,
        EntityRef analysisTarget,
        List<FlowImpactView> impactedFlows,
        List<ImpactNodeView> impactedSystems,
        List<ImpactNodeView> impactedBoundedContexts,
        List<ImpactNodeView> impactedIntegrations,
        List<ImpactNodeView> impactedDataStores,
        List<ImplementationImpactView> impactedImplementations,
        List<String> suggestedNextEvidence,
        List<String> limitations,
        List<ValidationFinding> validationFindings
) {

    public OperationalContextBlastRadiusReadModel {
        contract = contract != null ? contract : "operational-context.blast-radius";
        contractVersion = contractVersion > 0 ? contractVersion : 1;
        profile = profile != null ? profile : ReadModelProfile.defaultProfile();
        impactedFlows = copyList(impactedFlows);
        impactedSystems = copyList(impactedSystems);
        impactedBoundedContexts = copyList(impactedBoundedContexts);
        impactedIntegrations = copyList(impactedIntegrations);
        impactedDataStores = copyList(impactedDataStores);
        impactedImplementations = copyList(impactedImplementations);
        suggestedNextEvidence = copyTextList(suggestedNextEvidence);
        limitations = copyTextList(limitations);
        validationFindings = copyList(validationFindings);
    }

    public record FlowImpactView(
            EntityRef flow,
            List<StepImpactView> impactedSteps,
            List<FlowEdgeView> downstreamEdges,
            String confidence,
            List<String> reasons,
            Provenance provenance
    ) {

        public FlowImpactView {
            impactedSteps = copyList(impactedSteps);
            downstreamEdges = copyList(downstreamEdges);
            confidence = textOrDefault(confidence, "medium");
            reasons = copyTextList(reasons);
            provenance = provenance != null
                    ? provenance
                    : new Provenance(false, "unknown", "unknown", List.of(), List.of());
        }
    }

    public record StepImpactView(
            String stepId,
            int order,
            String name,
            String kind,
            String impactType,
            List<String> reasons,
            List<EntityRef> systems,
            List<EntityRef> boundedContexts,
            List<EntityRef> integrations,
            List<EntityRef> dataStores,
            List<ImplementationRef> implementations
    ) {

        public StepImpactView {
            stepId = textOrDefault(stepId, "unknown-step");
            name = textOrDefault(name, stepId);
            kind = textOrDefault(kind, "unknown");
            impactType = textOrDefault(impactType, "downstream");
            reasons = copyTextList(reasons);
            systems = copyList(systems);
            boundedContexts = copyList(boundedContexts);
            integrations = copyList(integrations);
            dataStores = copyList(dataStores);
            implementations = copyList(implementations);
        }
    }

    public record ImpactNodeView(
            EntityRef entity,
            String impactType,
            String direction,
            String criticality,
            String confidence,
            List<String> reasons,
            Provenance provenance
    ) {

        public ImpactNodeView {
            impactType = textOrDefault(impactType, "downstream");
            direction = textOrDefault(direction, "downstream");
            criticality = textOrDefault(criticality, "unknown");
            confidence = textOrDefault(confidence, "medium");
            reasons = copyTextList(reasons);
            provenance = provenance != null
                    ? provenance
                    : new Provenance(false, "unknown", "unknown", List.of(), List.of());
        }
    }

    public record ImplementationImpactView(
            ImplementationRef implementation,
            String impactType,
            String confidence,
            List<String> reasons
    ) {

        public ImplementationImpactView {
            impactType = textOrDefault(impactType, "downstream-code");
            confidence = textOrDefault(confidence, "medium");
            reasons = copyTextList(reasons);
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
                .map(OperationalContextBlastRadiusReadModel::text)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
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
