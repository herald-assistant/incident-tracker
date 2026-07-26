package pl.mkn.tdw.integrations.jira;

import java.util.List;

public record JiraConfluencePage(
        String pageId,
        String title,
        String url,
        String content,
        String version,
        List<String> limitations
) {

    public JiraConfluencePage {
        limitations = limitations != null ? List.copyOf(limitations) : List.of();
    }
}
