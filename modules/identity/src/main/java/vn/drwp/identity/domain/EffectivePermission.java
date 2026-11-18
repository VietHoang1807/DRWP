package vn.drwp.identity.domain;

import java.util.Collection;
import java.util.List;

import vn.drwp.platform.kernel.security.PermissionMask;
import vn.drwp.platform.kernel.security.ResourceCode;

/** Tính quyền hiệu lực đa vai trò: OR allow, OR deny, explicit deny thắng, mặc định DENY. */
public final class EffectivePermission {

    private EffectivePermission() {
    }

    public static long compute(Collection<Grant> grantsOfAllRoles, String resource) {
        List<String> matching = ResourceCode.matchingGrants(resource);
        long allow = 0;
        long deny = 0;
        for (Grant g : grantsOfAllRoles) {
            if (matching.contains(g.resource())) {
                allow |= g.allowMask();
                deny |= g.denyMask();
            }
        }
        return PermissionMask.effective(allow, deny);
    }

    /**
     * Chống leo thang đặc quyền: người cấp chỉ được cấp các bit mà chính họ đang có trên resource đó.
     */
    public static boolean canDelegate(long granterEffective, Grant grant) {
        long requested = grant.allowMask() | grant.denyMask();
        return (granterEffective & requested) == requested;
    }
}
