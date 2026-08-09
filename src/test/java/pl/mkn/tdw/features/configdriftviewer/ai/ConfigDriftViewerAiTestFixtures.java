package pl.mkn.tdw.features.configdriftviewer.ai;

import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerAffectedEntity;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerCodeGrounding;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerCodeRefSource;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerCodeUsageKind;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepContext;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepContextStatus;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepCoverage;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepPreflight;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepPreflightStatus;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerDeepRepositoryScope;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerGroundingConfidence;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerOperationalEntityType;
import pl.mkn.tdw.features.configdriftviewer.deep.model.ConfigDriftViewerPrimarySystem;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerChangeKind;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicContext;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDeterministicStatus;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerDifference;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerFinding;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerFindingSeverity;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerSensitivity;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.ConfigDriftViewerValueType;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.SanitizedConfigurationDocument;
import pl.mkn.tdw.features.configdriftviewer.deterministic.model.SanitizedConfigurationNode;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerBranchCoverage;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerFileCoverage;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerFileRole;
import pl.mkn.tdw.features.configdriftviewer.deterministic.source.ConfigDriftViewerFileStatus;
import pl.mkn.tdw.integrations.operationalcontext.OperationalContextOwnershipResolution;

import java.util.List;

public final class ConfigDriftViewerAiTestFixtures {

    private ConfigDriftViewerAiTestFixtures() {
    }

    public static ConfigDriftViewerDeterministicContext deterministic(
            ConfigDriftViewerDeterministicStatus status
    ) {
        var unchanged = new SanitizedConfigurationNode(
                "timeout",
                "service.timeout",
                ConfigDriftViewerValueType.NUMBER,
                ConfigDriftViewerValueType.NUMBER,
                ConfigDriftViewerChangeKind.UNCHANGED,
                ConfigDriftViewerSensitivity.NON_SENSITIVE,
                "value-1",
                "value-1",
                null,
                null,
                List.of()
        );
        var secret = new SanitizedConfigurationNode(
                "password",
                "datasource.password",
                ConfigDriftViewerValueType.STRING,
                ConfigDriftViewerValueType.STRING,
                ConfigDriftViewerChangeKind.CHANGED,
                ConfigDriftViewerSensitivity.SENSITIVE,
                "raw-secret-source",
                "raw-secret-target",
                null,
                null,
                List.of()
        );
        var root = new SanitizedConfigurationNode(
                "root",
                "",
                ConfigDriftViewerValueType.MAP,
                ConfigDriftViewerValueType.MAP,
                ConfigDriftViewerChangeKind.CHANGED,
                ConfigDriftViewerSensitivity.NON_SENSITIVE,
                null,
                null,
                2,
                2,
                List.of(unchanged, secret)
        );
        var document = new SanitizedConfigurationDocument(
                ConfigDriftViewerFileRole.APPLICATION_YAML,
                "backend/application.yml.kv",
                "backend/application.yml.kv",
                0,
                true,
                true,
                "profile-1",
                "profile-1",
                root
        );
        var difference = new ConfigDriftViewerDifference(
                "difference-1",
                ConfigDriftViewerFileRole.APPLICATION_YAML,
                0,
                "datasource.password",
                ConfigDriftViewerChangeKind.CHANGED,
                ConfigDriftViewerValueType.STRING,
                ConfigDriftViewerValueType.STRING,
                ConfigDriftViewerSensitivity.SENSITIVE,
                "raw-difference-secret-source",
                "raw-difference-secret-target"
        );
        var finding = new ConfigDriftViewerFinding(
                "finding-1",
                "SENSITIVE_VALUE_CHANGED",
                ConfigDriftViewerFindingSeverity.WARNING,
                "datasource.password",
                List.of("difference-1"),
                List.of()
        );
        return new ConfigDriftViewerDeterministicContext(
                "runtime-config",
                "crm-api",
                "CRM API",
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

    public static ConfigDriftViewerDeepContext deep() {
        var repository = new ConfigDriftViewerDeepRepositoryScope(
                "scope-1",
                "repository-1",
                "implementation",
                1,
                "platform/crm-api",
                "crm-api",
                "path-prefixes",
                List.of("src/main/java"),
                "release-1",
                "release-1",
                ConfigDriftViewerCodeRefSource.REQUESTED,
                true,
                false,
                true,
                List.of("The code ref is not confirmed as deployed.")
        );
        var preflight = new ConfigDriftViewerDeepPreflight(
                ConfigDriftViewerDeepPreflightStatus.READY,
                "runtime-config",
                "crm-api",
                "CRM API",
                "backend",
                List.of(repository),
                List.of(),
                List.of("The code ref is not confirmed as deployed.")
        );
        var affected = new ConfigDriftViewerAffectedEntity(
                "context-system-1",
                ConfigDriftViewerOperationalEntityType.SYSTEM,
                "customer-profile-api",
                "Customer Profile API",
                "Partner system",
                "CODE_CONFIRMED",
                ConfigDriftViewerGroundingConfidence.HIGH,
                List.of("difference-1"),
                List.of("code-1")
        );
        var grounding = new ConfigDriftViewerCodeGrounding(
                "code-1",
                "scope-1",
                "repository-1",
                "platform/crm-api",
                "release-1",
                "src/main/java/CrmProperties.java",
                42,
                "CrmProperties",
                "datasource.password",
                "difference-1",
                ConfigDriftViewerCodeUsageKind.CONFIGURATION_PROPERTIES,
                ConfigDriftViewerGroundingConfidence.HIGH
        );
        return new ConfigDriftViewerDeepContext(
                ConfigDriftViewerDeepContextStatus.COMPLETE,
                preflight,
                new ConfigDriftViewerPrimarySystem(
                        "crm-api",
                        "CRM API",
                        "internal-service",
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
                        List.of("system:crm-api"),
                        List.of("Owner is not resolved.")
                ),
                new ConfigDriftViewerDeepCoverage(1, 1, 1, 1, 1, List.of(), List.of()),
                List.of("The code ref is not confirmed as deployed.")
        );
    }

    private static ConfigDriftViewerBranchCoverage coverage(String branch) {
        return new ConfigDriftViewerBranchCoverage(
                branch,
                true,
                List.of(
                        file(ConfigDriftViewerFileRole.GLOBAL_VAR, "global.var"),
                        file(ConfigDriftViewerFileRole.LOCAL_VAR, "backend/local.var"),
                        file(ConfigDriftViewerFileRole.APPLICATION_YAML, "backend/application.yml.kv")
                )
        );
    }

    private static ConfigDriftViewerFileCoverage file(
            ConfigDriftViewerFileRole role,
            String path
    ) {
        return new ConfigDriftViewerFileCoverage(
                role,
                path,
                ConfigDriftViewerFileStatus.AVAILABLE,
                "commit",
                "commit",
                "2026-07-30T10:00:00Z",
                10L,
                null
        );
    }
}
