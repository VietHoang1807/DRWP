package vn.drwp.audit.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import vn.drwp.platform.json.Json;
import vn.drwp.platform.kernel.event.BusinessAuditEvent;
import vn.drwp.platform.support.SqlTime;

@Repository
public class AuditRepository {

    private final JdbcClient jdbc;
    private final Json json;

    public AuditRepository(JdbcClient jdbc, Json json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record AuditRow(long id, Instant occurredAt, String actor, String action, String resourceType, String resourceId,
                           String outcome, String correlationId, Map<String, Object> detail) {
    }

    public record Filter(String resourceType, String resourceId, String actor, String action, String outcome,
                         Instant from, Instant to) {
    }

    public void append(String dedupKey, BusinessAuditEvent e) {
        jdbc.sql("""
                INSERT INTO aud_event (dedup_key, occurred_at, actor, action, resource_type, resource_id, outcome,
                                       correlation_id, detail)
                VALUES (:key, :at, :actor, :action, :rtype, :rid, :outcome, :corr, CAST(:detail AS jsonb))
                ON CONFLICT (dedup_key) DO NOTHING
                """).param("key", dedupKey).param("at", SqlTime.ts(e.occurredAt())).param("actor", e.actor())
                .param("action", e.action()).param("rtype", e.resourceType()).param("rid", e.resourceId())
                .param("outcome", e.outcome().name()).param("corr", e.correlationId()).param("detail", json.write(e.detail()))
                .update();
    }

    public List<AuditRow> search(Filter f, int limit, long offset) {
        Map<String, Object> params = new LinkedHashMap<>();
        String where = where(f, params);
        return jdbc.sql("SELECT id, occurred_at, actor, action, resource_type, resource_id, outcome, correlation_id, detail::text AS detail"
                        + " FROM aud_event" + where + " ORDER BY occurred_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .params(params).param("limit", limit).param("offset", offset)
                .query((rs, n) -> new AuditRow(rs.getLong("id"), SqlTime.instant(rs, "occurred_at"), rs.getString("actor"),
                        rs.getString("action"), rs.getString("resource_type"), rs.getString("resource_id"),
                        rs.getString("outcome"), rs.getString("correlation_id"), json.readMap(rs.getString("detail"))))
                .list();
    }

    public long count(Filter f) {
        Map<String, Object> params = new LinkedHashMap<>();
        return jdbc.sql("SELECT count(*) FROM aud_event" + where(f, params)).params(params).query(Long.class).single();
    }

    private static String where(Filter f, Map<String, Object> params) {
        List<String> c = new ArrayList<>();
        add(c, params, "resource_type = :rtype", "rtype", f.resourceType());
        add(c, params, "resource_id = :rid", "rid", f.resourceId());
        add(c, params, "actor = :actor", "actor", f.actor());
        add(c, params, "action = :action", "action", f.action());
        add(c, params, "outcome = :outcome", "outcome", f.outcome());
        add(c, params, "occurred_at >= :from", "from", f.from() == null ? null : Timestamp.from(f.from()));
        add(c, params, "occurred_at < :to", "to", f.to() == null ? null : Timestamp.from(f.to()));
        return c.isEmpty() ? "" : " WHERE " + String.join(" AND ", c);
    }

    private static void add(List<String> clauses, Map<String, Object> params, String clause, String name, Object value) {
        if (value != null) {
            clauses.add(clause);
            params.put(name, value);
        }
    }
}
