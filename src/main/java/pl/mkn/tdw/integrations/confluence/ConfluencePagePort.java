package pl.mkn.tdw.integrations.confluence;

import pl.mkn.tdw.integrations.confluence.contract.ConfluencePageContent;

import java.util.Optional;

public interface ConfluencePagePort {

    Optional<ConfluencePageContent> getPageContent(String pageUrl);
}
