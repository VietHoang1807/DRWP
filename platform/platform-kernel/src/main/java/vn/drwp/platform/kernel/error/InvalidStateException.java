package vn.drwp.platform.kernel.error;

public class InvalidStateException extends DomainException {
    public InvalidStateException(String message) {
        super("INVALID_STATE", message);
    }
}
