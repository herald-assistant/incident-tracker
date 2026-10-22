package pl.mkn.tdw.integrations.operationalcontext;

import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextNavigation;

public interface OperationalContextNavigationPort {
    OperationalContextNavigation build(OperationalContextCatalog catalog);
}
