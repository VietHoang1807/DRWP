package vn.drwp.record.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import vn.drwp.record.api.RecordStatus;
import vn.drwp.record.domain.RecordQuery;

class RecordSqlBuilderTest {

    @Test
    void bindsEverythingAsParameters() {
        RecordQuery q = new RecordQuery("LEAVE", RecordStatus.IN_PROGRESS, null, null, null, null,
                Map.of("leaveType", "ANNUAL' OR '1'='1"), RecordQuery.Sort.UPDATED_AT, true, 0, 20);
        RecordSqlBuilder.Built b = RecordSqlBuilder.build(q, Set.of("leaveType"), "alice");
        assertEquals(" WHERE form_code = :formCode AND status = :status AND created_by = :owner AND dynamic_data ->> :dk0 = :dv0",
                b.where());
        assertEquals(" ORDER BY updated_at DESC, id", b.orderBy());
        assertEquals("ANNUAL' OR '1'='1", b.params().get("dv0"));
        assertEquals("leaveType", b.params().get("dk0"));
        assertFalse(b.where().contains("ANNUAL"));
    }

    @Test
    void rejectsNonWhitelistedDataKeys() {
        RecordQuery q = new RecordQuery("LEAVE", null, null, null, null, null, Map.of("salary", "1"), null, false, 0, 20);
        assertThrows(IllegalArgumentException.class, () -> RecordSqlBuilder.build(q, Set.of("leaveType"), null));
    }

    @Test
    void emptyQuery() {
        RecordSqlBuilder.Built b = RecordSqlBuilder.build(new RecordQuery(null, null, null, null, null, null, null, null, false, 0, 10),
                Set.of(), null);
        assertEquals("", b.where());
        assertTrue(b.orderBy().contains("created_at ASC"));
    }
}
