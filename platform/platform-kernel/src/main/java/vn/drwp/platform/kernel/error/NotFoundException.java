package vn.drwp.platform.kernel.error;

/** Không tìm thấy hoặc không được phép biết sự tồn tại (tránh lộ đối tượng của tenant khác). */
public class NotFoundException extends DomainException {
    public NotFoundException(String resource, Object id) {
        super("NOT_FOUND", resource + " not found: " + id);
    }
}
