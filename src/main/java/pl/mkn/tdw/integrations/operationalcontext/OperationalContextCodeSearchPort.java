package pl.mkn.tdw.integrations.operationalcontext;

import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextCodeSearchReadModel;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextCatalog;

public interface OperationalContextCodeSearchPort {

    OperationalContextCodeSearchReadModel buildForEntity(
            OperationalContextCatalog catalog, String entityType, String entityId);
}
