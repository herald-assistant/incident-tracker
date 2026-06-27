package pl.mkn.tdw.integrations.gitlab.usecase;

public enum GitLabJavaInjectionSource {
    LOMBOK_REQUIRED_ARGS,
    CONSTRUCTOR,
    AUTOWIRED_FIELD,
    AUTOWIRED_CONSTRUCTOR,
    AUTOWIRED_SETTER
}
