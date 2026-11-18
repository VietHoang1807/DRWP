package vn.drwp.identity.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import vn.drwp.platform.kernel.security.Action;
import vn.drwp.platform.kernel.security.PermissionMask;

class EffectivePermissionTest {

    @Test
    void multiRoleOrWithDenyOverride() {
        List<Grant> grants = List.of(
                new Grant("record:*", PermissionMask.of(Action.VIEW, Action.CREATE), 0),
                new Grant("record:LEAVE", PermissionMask.of(Action.APPROVE), 0),
                new Grant("record:LEAVE", 0, PermissionMask.of(Action.CREATE)),   // vai trò khác cấm tạo
                new Grant("metadata", PermissionMask.of(Action.CONFIGURE), 0));
        long leave = EffectivePermission.compute(grants, "record:LEAVE");
        assertTrue(PermissionMask.grants(leave, PermissionMask.of(Action.VIEW, Action.APPROVE)));
        assertFalse(PermissionMask.grants(leave, Action.CREATE.mask()));
        long other = EffectivePermission.compute(grants, "record:OTHER");
        assertTrue(PermissionMask.grants(other, Action.CREATE.mask()));
        assertEquals(0L, EffectivePermission.compute(grants, "workflow"));
    }

    @Test
    void wildcardGrantCoversEverything() {
        long all = EffectivePermission.compute(List.of(new Grant("*", PermissionMask.ALL_DEFINED, 0)), "wf:LEAVE_APPROVAL");
        assertEquals(PermissionMask.ALL_DEFINED, all);
    }

    @Test
    void preventsPrivilegeEscalation() {
        long mine = PermissionMask.of(Action.VIEW, Action.CREATE);
        assertTrue(EffectivePermission.canDelegate(mine, new Grant("record:A", Action.VIEW.mask(), 0)));
        assertFalse(EffectivePermission.canDelegate(mine, new Grant("record:A", Action.APPROVE.mask(), 0)));
        assertFalse(EffectivePermission.canDelegate(mine, new Grant("record:A", 0, Action.MANAGE.mask())));
    }

    @Test
    void rejectsInvalidGrants() {
        assertThrows(IllegalArgumentException.class, () -> new Grant("record:A", 1L << 50, 0));
        assertThrows(IllegalArgumentException.class, () -> new Grant("record:A", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new Grant("bad resource", 1, 0));
    }
}
