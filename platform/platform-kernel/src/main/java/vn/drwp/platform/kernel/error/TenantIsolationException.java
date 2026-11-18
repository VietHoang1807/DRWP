package vn.drwp.platform.kernel.error;

/**
 * Vi phạm cách ly tenant: thiếu tenant context, lệch tenant giữa context và sự kiện,
 * tenant không ACTIVE... Luôn fail-closed (tài liệu 02 mục 1 điểm 3).
 */
public class TenantIsolationException extends DomainException {
    public TenantIsolationException(String message) {
        super("TENANT_ISOLATION", message);
    }
}
