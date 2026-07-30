package pl.mkn.tdw.features.runtimeconfigurationverification.ai;

import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationAffectedEntity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationCodeGrounding;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationCodeRefSource;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationCodeUsageKind;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepContextStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepCoverage;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepPreflight;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepPreflightStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationDeepRepositoryScope;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationGroundingConfidence;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationOperationalEntityType;
import pl.mkn.tdw.features.runtimeconfigurationverification.deep.model.RuntimeConfigurationPrimarySystem;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationChangeKind;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicContext;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDeterministicStatus;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationDifference;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationFinding;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationFindingSeverity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationSensitivity;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.RuntimeConfigurationValueType;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.SanitizedConfigurationDocument;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.model.SanitizedConfigurationNode;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationBranchCoverage;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationFileCoverage;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationFileRole;
import pl.mkn.tdw.features.runtimeconfigurationverification.deterministic.source.RuntimeConfigurationFileStatus;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution;

import java.util.List;

public final class RuntimeConfigurationAiTestFixtures {

    private RuntimeConfigurationAiTestFixtures() {
    }

    public static RuntimeConfigurationDeterministicContext deterministic(
            RuntimeConfigurationDeterministicStatus status
    ) {
        var unchanged = new SanitizedConfigurationNode(
                "timeout",
                "service.timeout",
                RuntimeConfigurationValueType.NUMBER,
                RuntimeConfigurationValueType.NUMBER,
                RuntimeConfigurationChangeKind.UNCHANGED,
                RuntimeConfigurationSensitivity.NON_SENSITIVE,
                "value-1",
                "value-1",
                null,
                null,
                List.of()
        );
        var secret = new SanitizedConfigurationNode(
                "password",
                "datasource.password",
                RuntimeConfigurationValueType.STRING,
                RuntimeConfigurationValueType.STRING,
                RuntimeConfigurationChangeKind.CHANGED,
                RuntimeConfigurationSensitivity.SENSITIVE,
                "raw-secret-source",
                "raw-secret-target",
                null,
                null,
                List.of()
        );
        var root = new SanitizedConfigurationNode(
                "root",
                "",
                RuntimeConfigurationValueType.MAP,
                RuntimeConfigurationValueType.MAP,
                RuntimeConfigurationChangeKind.CHANGED,
                RuntimeConfigurationSensitivity.NON_SENSITIVE,
                null,
                null,
                2,
                2,
                List.of(unchanged, secret)
        );
        var document = new SanitizedConfigurationDocument(
                RuntimeConfigurationFileRole.APPLICATION_YAML,
                "backend/application.yml.kv",
                "backend/application.yml.kv",
                0,
                true,
                true,
                "profile-1",
                "profile-1",
                root
        );
        var difference = new RuntimeConfigurationDifference(
                "difference-1",
                RuntimeConfigurationFileRole.APPLICATION_YAML,
                0,
                "datasource.password",
                RuntimeConfigurationChangeKind.CHANGED,
                RuntimeConfigurationValueType.STRING,
                RuntimeConfigurationValueType.STRING,
                RuntimeConfigurationSensitivity.SENSITIVE,
                "raw-difference-secret-source",
                "raw-difference-secret-target"
        );
        var finding = new RuntimeConfigurationFinding(
                "finding-1",
                "SENSITIVE_VALUE_CHANGED",
                RuntimeConfigurationFindingSeverity.WARNING,
                "datasource.password",
                List.of("difference-1"),
                List.of()
        );
        return new RuntimeConfigurationDeterministicContext(
                "runtime-config",
                "billing-api",
                "Billing API",
                "backend",
                "dev1",
                "zt001",
                status,
                coverage("dev1"),
                coverage("zt001"),
                List.of(document),
                List.of(),
                List.of(difference),
                List.of(finding)
        );
    }

    public static RuntimeConfigurationDeepContext deep() {
        var repository = new RuntimeConfigurationDeepRepositoryScope(
                "scope-1",
                "repository-1",
                "implementation",
                1,
                "platform/billing-api",
                "billing-api",
                "path-prefixes",
                List.of("src/main/java"),
                "release-1",
                "release-1",
                RuntimeConfigurationCodeRefSource.REQUESTED,
                true,
                false,
                true,
                List.of("The code ref is not confirmed as deployed.")
        );
        var preflight = new RuntimeConfigurationDeepPreflight(
                RuntimeConfigurationDeepPreflightStatus.READY,
                "runtime-config",
                "billing-api",
                "Billing API",
                "backend",
                List.of(repository),
                List.of(),
                List.of("The code ref is not confirmed as deployed.")
        );
        var affected = new RuntimeConfigurationAffectedEntity(
                "context-system-1",
                RuntimeConfigurationOperationalEntityType.SYSTEM,
                "payments-api",
                "Payments API",
                "Partner system",
                "CODE_CONFIRMED",
                RuntimeConfigurationGroundingConfidence.HIGH,
                List.of("difference-1"),
                List.of("code-1")
        );
        var grounding = new RuntimeConfigurationCodeGrounding(
                "code-1",
                "scope-1",
                "repository-1",
                "platform/billing-api",
                "release-1",
                "src/main/java/BillingProperties.java",
                42,
                "BillingProperties",
                "datasource.password",
                "difference-1",
                RuntimeConfigurationCodeUsageKind.CONFIGURATION_PROPERTIES,
                RuntimeConfigurationGroundingConfidence.HIGH
        );
        return new RuntimeConfigurationDeepContext(
                RuntimeConfigurationDeepContextStatus.COMPLETE,
                preflight,
                new RuntimeConfigurationPrimarySystem(
                        "billing-api",
                        "Billing API",
                        "internal-system",
                        "backend",
                        "runtime signal",
                        List.of("scope-1")
                ),
                List.of(affected),
                List.of(),
                List.of(),
                List.of(),
                List.of(grounding),
                OperationalContextOwnershipResolution.unknown(
                        List.of("system:billing-api"),
                        List.of("Owner is not resolved.")
                ),
                new RuntimeConfigurationDeepCoverage(1, 1, 1, 1, 1, List.of(), List.of()),
                List.of("The code ref is not confirmed as deployed.")
        );
    }

    private static RuntimeConfigurationBranchCoverage coverage(String branch) {
        return new RuntimeConfigurationBranchCoverage(
                branch,
                true,
                List.of(
                        file(RuntimeConfigurationFileRole.GLOBAL_VAR, "global.var"),
                        file(RuntimeConfigurationFileRole.LOCAL_VAR, "backend/local.var"),
                        file(RuntimeConfigurationFileRole.APPLICATION_YAML, "backend/application.yml.kv")
                )
        );
    }

    private static RuntimeConfigurationFileCoverage file(
            RuntimeConfigurationFileRole role,
            String path
    ) {
        return new RuntimeConfigurationFileCoverage(
                role,
                path,
                RuntimeConfigurationFileStatus.AVAILABLE,
                "commit",
                "commit",
                "2026-07-30T10:00:00Z",
                10L,
                null
        );
    }
}
