package pl.mkn.tdw.integrations.gitlab.usecase;

public enum GitLabJavaTypeResolutionKind {
    SAME_FILE,
    EXACT_IMPORT,
    SAME_PACKAGE,
    NESTED_TYPE,
    TREE_LOOKUP,
    EXTERNAL_BOUNDARY,
    AMBIGUOUS,
    UNRESOLVED,
    PARSE_FAILED
}
