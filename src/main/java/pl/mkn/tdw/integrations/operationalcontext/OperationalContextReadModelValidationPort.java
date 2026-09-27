package pl.mkn.tdw.integrations.operationalcontext;

import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextDtos.OperationalContextCatalog;
import pl.mkn.tdw.integrations.operationalcontext.contract.OperationalContextRelationIndex.ValidationFinding;

import java.util.List;

public interface OperationalContextReadModelValidationPort {

    List<ValidationFinding> validate(OperationalContextCatalog catalog);

    List<ValidationFinding> validateCatalogContract(OperationalContextCatalog catalog);
}
