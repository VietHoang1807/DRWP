package vn.drwp.identity.domain;

import vn.drwp.platform.kernel.security.PermissionMask;
import vn.drwp.platform.kernel.security.ResourceCode;

/** Một dòng {@code role_permission}: allow/deny mask trên một resource code. */
public record Grant(String resource, long allowMask, long denyMask) {

    public Grant {
        ResourceCode.requireValid(resource);
        PermissionMask.requireValid(allowMask, "allowMask");
        PermissionMask.requireValid(denyMask, "denyMask");
        if (allowMask == 0 && denyMask == 0) {
            throw new IllegalArgumentException("Grant for " + resource + " has neither allow nor deny bits");
        }
    }
}
