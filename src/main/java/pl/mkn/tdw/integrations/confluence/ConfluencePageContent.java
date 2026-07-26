package pl.mkn.tdw.integrations.confluence;

import java.util.List;

public record ConfluencePageContent(
        String pageId,
        String title,
        String url,
        String content,
        String version,
        List<String> limitations
) {

    public ConfluencePageContent {
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
