package vn.drwp.identity.api;

import vn.drwp.platform.kernel.security.Action;

/**
 * Kiểm tra quyền bitmask trong tenant hiện tại. Mask chỉ là điều kiện CẦN: module gọi còn phải kiểm
 * ownership, trạng thái workflow, assignment... (ABAC, tài liệu 02 mục 2).
 */
public interface PermissionChecker {

    /** Effective mask = OR(allow) &amp; ~OR(deny) trên các grant khớp resource. */
    long effectiveMask(String subject, String resource);

    boolean isAllowed(String subject, String resource, Action... actions);

    /** Ném ForbiddenException nếu thiếu quyền. */
    void require(String subject, String resource, Action... actions);
}
