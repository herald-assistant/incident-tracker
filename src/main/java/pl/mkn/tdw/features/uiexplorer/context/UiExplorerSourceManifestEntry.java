package pl.mkn.tdw.features.uiexplorer.context;

import java.util.List;

public record UiExplorerSourceManifestEntry(
        String path,
        List<String> roles,
        int sourceCharacters,
        String contentSha256,
        int sliceCount
) {
    public UiExplorerSourceManifestEntry {
        roles = roles != null ? List.copyOf(roles) : List.of();
    }
}
