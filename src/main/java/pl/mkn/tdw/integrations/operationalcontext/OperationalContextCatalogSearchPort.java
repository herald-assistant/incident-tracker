package pl.mkn.tdw.integrations.operationalcontext;

import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextCatalogSearchMatch;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextCatalog;

import java.util.List;

public interface OperationalContextCatalogSearchPort {

    List<OperationalContextCatalogSearchMatch> find(OperationalContextCatalog catalog, String query);
}
