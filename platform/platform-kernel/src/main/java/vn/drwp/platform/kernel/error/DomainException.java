package vn.drwp.platform.kernel.error;

/** Lỗi nghiệp vụ gốc. {@code code} ổn định để client xử lý, không chứa dữ liệu nhạy cảm. */
public class DomainException extends RuntimeException {

    private final String code;

    public DomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
