package vn.drwp.platform.kernel.security;

import java.util.Collection;

/**
 * Phép toán bitmask: {@code effectiveAllow = OR(allow) & ~OR(deny)}; explicit deny thắng; default DENY.
 * Chỉ dùng bit 0–62 (tài liệu 02 mục 2).
 */
public final class PermissionMask {

    /** Tất cả bit đã định nghĩa trong {@link Action}. */
    public static final long ALL_DEFINED;

    static {
        long m = 0;
        for (Action a : Action.values()) {
            m |= a.mask();
        }
        ALL_DEFINED = m;
    }

    private PermissionMask() {
    }

    public static long of(Action... actions) {
        long m = 0;
        for (Action a : actions) {
            m |= a.mask();
        }
        return m;
    }

    public static long of(Collection<Action> actions) {
        long m = 0;
        for (Action a : actions) {
            m |= a.mask();
        }
        return m;
    }

    /** Mask hợp lệ khi không âm (bit 63 không dùng) và chỉ chứa bit đã định nghĩa. */
    public static boolean isValid(long mask) {
        return mask >= 0 && (mask & ~ALL_DEFINED) == 0;
    }

    public static void requireValid(long mask, String name) {
        if (!isValid(mask)) {
            throw new IllegalArgumentException(name + " contains undefined permission bits: " + mask);
        }
    }

    /** Mask yêu cầu phải khác 0 và hợp lệ. */
    public static void requireValidRequired(long required) {
        if (required == 0) {
            throw new IllegalArgumentException("required mask must not be 0");
        }
        requireValid(required, "required");
    }

    public static long effective(long orAllow, long orDeny) {
        return orAllow & ~orDeny;
    }

    public static boolean grants(long effective, long required) {
        requireValidRequired(required);
        return (effective & required) == required;
    }
}
