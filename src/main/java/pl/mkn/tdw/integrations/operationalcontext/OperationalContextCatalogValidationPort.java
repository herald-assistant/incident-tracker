package pl.mkn.tdw.integrations.operationalcontext;

import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextRelationIndex.ValidationFinding;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextValidationReport;

public interface OperationalContextCatalogValidationPort {

    OperationalContextValidationReport validate(OperationalContextCatalog catalog);

    String fingerprint(ValidationFinding finding);
}
