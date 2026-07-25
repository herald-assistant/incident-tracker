package pl.mkn.tdw.features.changeverification.ai.copilot;

final class ChangeVerificationCopilotToolContextKeys {

    static final String FEATURE = "feature";
    static final String FEATURE_VALUE = "change-verification";
    static final String RUN_KIND = "runKind";
    static final String RUN_KIND_COMPLIANCE = "compliance";
    static final String RUN_KIND_SMOKE_PACK = "smoke-pack";
    static final String REPOSITORY_SCOPE_RESOLVED = "changeVerificationRepositoryScopeResolved";
    static final String ALLOWED_REPOSITORIES = "changeVerificationAllowedRepositories";
    static final String DATABASE_APPLICATION = "changeVerificationDatabaseApplication";
    static final String DATABASE_READONLY_ONLY = "changeVerificationDatabaseReadonlyOnly";

    private ChangeVerificationCopilotToolContextKeys() {
    }
}
