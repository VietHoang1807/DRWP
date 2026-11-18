package vn.drwp.platform.kernel.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class PermissionMaskTest {

    @Test
    void bitsMatchCatalogue() {
        assertEquals(1L, Action.VIEW.mask());
        assertEquals(16L, Action.APPROVE.mask());
        assertEquals(2048L, Action.MANAGE.mask());
        assertEquals(4095L, PermissionMask.ALL_DEFINED);
    }

    @Test
    void explicitDenyWins() {
        long allow = PermissionMask.of(Action.VIEW, Action.APPROVE);
        long deny = PermissionMask.of(Action.APPROVE);
        long effective = PermissionMask.effective(allow, deny);
        assertTrue(PermissionMask.grants(effective, Action.VIEW.mask()));
        assertFalse(PermissionMask.grants(effective, Action.APPROVE.mask()));
    }

    @Test
    void requiresAllRequestedBits() {
        long effective = PermissionMask.of(Action.VIEW);
        assertFalse(PermissionMask.grants(effective, PermissionMask.of(Action.VIEW, Action.EXPORT)));
    }

    @Test
    void rejectsZeroAndUndefinedBits() {
        assertThrows(IllegalArgumentException.class, () -> PermissionMask.grants(1L, 0L));
        assertThrows(IllegalArgumentException.class, () -> PermissionMask.grants(1L, 1L << 40));
        assertFalse(PermissionMask.isValid(-1L));
        assertFalse(PermissionMask.isValid(1L << 62));
        assertTrue(PermissionMask.isValid(PermissionMask.ALL_DEFINED));
    }

    @Test
    void fromMaskRoundTrip() {
        assertEquals(Set.of(Action.CREATE, Action.AUDIT), Action.fromMask(PermissionMask.of(Action.CREATE, Action.AUDIT)));
    }

    @Test
    void resourceMatchingFromSpecificToWildcard() {
        assertEquals(List.of("record:LEAVE", "record:*", "record", "*"), ResourceCode.matchingGrants("record:LEAVE"));
        assertEquals(List.of("metadata", "*"), ResourceCode.matchingGrants("metadata"));
        assertThrows(IllegalArgumentException.class, () -> ResourceCode.requireValid("record:leave; drop"));
        assertThrows(IllegalArgumentException.class, () -> ResourceCode.requireValid("Record:LEAVE"));
    }
}
