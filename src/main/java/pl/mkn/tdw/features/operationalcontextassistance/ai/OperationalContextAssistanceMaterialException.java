package pl.mkn.tdw.features.operationalcontextassistance.ai;

import pl.mkn.tdw.shared.error.UserFacingApplicationException;
import pl.mkn.tdw.shared.error.UserFacingErrorType;

public final class OperationalContextAssistanceMaterialException extends UserFacingApplicationException {

    public OperationalContextAssistanceMaterialException(String message) {
        super("OPCTX_ASSISTANCE_MATERIAL_UNAVAILABLE", UserFacingErrorType.UNPROCESSABLE_ENTITY, message);
    }
}
