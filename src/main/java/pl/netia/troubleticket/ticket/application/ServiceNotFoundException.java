package pl.netia.troubleticket.ticket.application;

/**
 * The service does not exist, is not active or belongs to another tenant; the caller is not told which.
 */
public class ServiceNotFoundException extends RuntimeException {

    public ServiceNotFoundException(long serviceId) {
        super("Service " + serviceId + " does not exist, is not active or belongs to another tenant");
    }
}
