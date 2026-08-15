package pl.mkn.tdw.features.uiexplorer.context;

import java.util.List;

public record UiExplorerSourceContextFile(
        String path,
        List<String> roles,
        String content,
        int returnedCharacters,
        boolean truncated
) {

    public UiExplorerSourceContextFile {
        roles = roles != null ? List.copyOf(roles) : List.of();
        content = content != null ? content : "";
    }
}
