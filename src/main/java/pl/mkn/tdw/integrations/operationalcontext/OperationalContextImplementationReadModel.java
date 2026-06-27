package pl.mkn.tdw.integrations.operationalcontext;

import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCodeSearchReadModel.CodeSearchHints;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextCodeSearchReadModel.ReadModelProfile;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.EntityRef;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.Provenance;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextRelationIndex.ValidationFinding;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record OperationalContextImplementationReadModel(
        String contract,
        int contractVersion,
        ReadModelProfile profile,
        EntityRef analysisTarget,
        List<ImplementationView> implementations,
        List<String> limitations,
        List<ValidationFinding> validationFindings
) {

    public OperationalContextImplementationReadModel {
        contract = contract != null ? contract : "operational-context.implementation-map";
        contractVersion = contractVersion > 0 ? contractVersion : 1;
        profile = profile != null ? profile : ReadModelProfile.defaultProfile();
        implementations = copyList(implementations);
        limitations = copyTextList(limitations);
        validationFindings = copyList(validationFindings);
    }

    public static OperationalContextImplementationReadModel empty(
            EntityRef analysisTarget,
            List<ValidationFinding> validationFindings
    ) {
        return new OperationalContextImplementationReadModel(
                "operational-context.implementation-map",
                1,
                ReadModelProfile.defaultProfile(),
                analysisTarget,
                List.of(),
                List.of(),
                validationFindings
        );
    }

    public record ImplementationView(
            String id,
            String implementationKind,
            String lifecycleRole,
            String migrationStatus,
            String implementationRole,
            Integer priority,
            EntityRef codeSearchScope,
            List<EntityRef> boundedContexts,
            List<EntityRef> systems,
            List<EntityRef> processes,
            EntityRef repository,
            ModuleImplementationView module,
            List<String> packagePrefixes,
            List<String> sourceRoots,
            List<String> importantPaths,
            CodeSearchHints hints,
            Map<String, Object> metadata,
            Provenance provenance
    ) {

        public ImplementationView {
            id = textOrDefault(id, "unknown-implementation");
            implementationKind = textOrDefault(implementationKind, "implementation");
            lifecycleRole = textOrDefault(lifecycleRole, "primary");
            migrationStatus = textOrDefault(migrationStatus, "unknown");
            implementationRole = textOrDefault(implementationRole, lifecycleRole);
            boundedContexts = copyList(boundedContexts);
            systems = copyList(systems);
            processes = copyList(processes);
            packagePrefixes = copyTextList(packagePrefixes);
            sourceRoots = copyTextList(sourceRoots);
            importantPaths = copyTextList(importantPaths);
            hints = hints != null ? hints : CodeSearchHints.empty();
            metadata = copyMap(metadata);
            provenance = provenance != null
                    ? provenance
                    : new Provenance(false, "unknown", "unknown", List.of(), List.of());
        }
    }

    public record ModuleImplementationView(
            String id,
            String name,
            String moduleType,
            String lifecycleStatus,
            List<String> paths,
            List<String> packages,
            List<String> sourceRoots,
            List<String> importantPaths
    ) {

        public ModuleImplementationView {
            id = text(id);
            name = text(name);
            moduleType = text(moduleType);
            lifecycleStatus = text(lifecycleStatus);
            paths = copyTextList(paths);
            packages = copyTextList(packages);
            sourceRoots = copyTextList(sourceRoots);
            importantPaths = copyTextList(importantPaths);
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
                .map(OperationalContextImplementationReadModel::text)
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
