package pl.mkn.tdw.features.uiexplorer.context;

import java.util.List;

public record UiExplorerSourceSlice(
        String sliceId,
        String path,
        List<String> roles,
        String kind,
        String symbol,
        int startLine,
        int endLine,
        String content,
        int returnedCharacters,
        String contentSha256
) {
    public UiExplorerSourceSlice {
        roles = roles != null ? List.copyOf(roles) : List.of();
        content = content != null ? content : "";
    }
}
