package vn.drwp.platform.kernel.error;

import java.util.List;

public class ValidationFailedException extends DomainException {

    private final List<Violation> violations;

    public ValidationFailedException(List<Violation> violations) {
        super("VALIDATION_FAILED", "Validation failed with " + violations.size() + " violation(s)");
        this.violations = List.copyOf(violations);
    }

    public static ValidationFailedException single(String field, String code, String message) {
        return new ValidationFailedException(List.of(new Violation(field, code, message)));
    }

    public List<Violation> violations() {
        return violations;
    }
}
