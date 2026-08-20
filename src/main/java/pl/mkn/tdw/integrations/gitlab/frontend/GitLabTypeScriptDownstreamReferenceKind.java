package pl.mkn.tdw.integrations.gitlab.frontend;

public enum GitLabTypeScriptDownstreamReferenceKind {
    METHOD_CALL,
    PROPERTY_ACCESS,
    BACKEND_OPERATION,
    NGRX_DISPATCH,
    NGRX_SELECT,
    NGRX_ACTION,
    RXJS_PIPELINE,
    IMPORTED_FUNCTION
}
