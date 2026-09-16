package pl.mkn.tdw.features.uxinspector.ai.tools;

import java.util.Set;

public final class UxInspectorToolNames {
    public static final String LIST_TARGET_CANDIDATES = "uxi_list_target_candidates";
    public static final String READ_TARGET_SLICE = "uxi_read_target_slice";
    public static final Set<String> ALL = Set.of(LIST_TARGET_CANDIDATES, READ_TARGET_SLICE);
    private UxInspectorToolNames() {}
}

