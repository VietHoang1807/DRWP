package vn.drwp.platform.web;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import vn.drwp.platform.kernel.error.ConflictException;
import vn.drwp.platform.kernel.error.DomainException;
import vn.drwp.platform.kernel.error.ForbiddenException;
import vn.drwp.platform.kernel.error.InvalidStateException;
import vn.drwp.platform.kernel.error.NotFoundException;
import vn.drwp.platform.kernel.error.TenantIsolationException;
import vn.drwp.platform.kernel.error.ValidationFailedException;
import vn.drwp.platform.support.CorrelationId;

/** RFC 9457 Problem Details; không trả stacktrace, SQL hay dữ liệu nội bộ. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ValidationFailedException.class)
    ProblemDetail validation(ValidationFailedException e) {
        ProblemDetail pd = problem(HttpStatus.UNPROCESSABLE_CONTENT, e.code(), e.getMessage());
        pd.setProperty("violations", e.violations());
        return pd;
    }

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail notFound(NotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.code(), e.getMessage());
    }

    @ExceptionHandler({ForbiddenException.class, AccessDeniedException.class})
    ProblemDetail forbidden(RuntimeException e) {
        return problem(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied");
    }

    @ExceptionHandler(TenantIsolationException.class)
    ProblemDetail tenant(TenantIsolationException e) {
        log.warn("Tenant isolation rejection: {}", e.getMessage());
        return problem(HttpStatus.FORBIDDEN, e.code(), "Tenant access denied");
    }

    @ExceptionHandler({ConflictException.class, InvalidStateException.class})
    ProblemDetail conflict(DomainException e) {
        return problem(HttpStatus.CONFLICT, e.code(), e.getMessage());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    ProblemDetail duplicate(DuplicateKeyException e) {
        return problem(HttpStatus.CONFLICT, "DUPLICATE", "Resource already exists");
    }

    @ExceptionHandler(DomainException.class)
    ProblemDetail domain(DomainException e) {
        return problem(HttpStatus.BAD_REQUEST, e.code(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalidBody(MethodArgumentNotValidException e) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body is invalid");
        List<Map<String, String>> errors = e.getBindingResult().getFieldErrors().stream()
                .map(f -> Map.of("field", f.getField(), "message", String.valueOf(f.getDefaultMessage())))
                .toList();
        pd.setProperty("violations", errors);
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail illegalArgument(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail tooLarge(MaxUploadSizeExceededException e) {
        return problem(HttpStatus.CONTENT_TOO_LARGE, "PAYLOAD_TOO_LARGE", "Upload exceeds the size limit");
    }

    static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(URI.create("urn:drwp:problem:" + code.toLowerCase()));
        pd.setProperty("code", code);
        String correlationId = CorrelationId.current();
        if (correlationId != null) {
            pd.setProperty("correlationId", correlationId);
        }
        return pd;
    }
}
