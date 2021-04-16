package app.mesmedicaments.api.interactions;

import app.mesmedicaments.interactions.service.InteractionService;
import app.mesmedicaments.interactions.service.InteractionServiceException;
import lombok.experimental.PackagePrivate;
import lombok.experimental.UtilityClass;

@UtilityClass
@PackagePrivate
class InteractionServiceProxy {

    static private InteractionService service = null;

    static InteractionService getInteractionService() throws InteractionServiceException {
        if (service == null)
            service = new InteractionService();
        return service;
    }

}
