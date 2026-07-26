package pl.mkn.tdw.integrations.confluence;

import java.util.Optional;

public interface ConfluencePagePort {

    Optional<ConfluencePageContent> getPageContent(String pageUrl);
}
