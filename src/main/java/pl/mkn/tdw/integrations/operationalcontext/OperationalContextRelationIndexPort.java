package pl.mkn.tdw.integrations.operationalcontext;

import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextRelationIndex;

public interface OperationalContextRelationIndexPort {

    OperationalContextRelationIndex build(OperationalContextCatalog catalog);
}
