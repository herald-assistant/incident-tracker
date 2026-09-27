package pl.mkn.tdw.integrations.operationalcontext;

import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextOwnershipRequest;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextOwnershipResolution;

public interface OperationalContextOwnershipPort {

    OperationalContextOwnershipResolution resolve(
            OperationalContextCatalog catalog, OperationalContextOwnershipRequest request);
}
