package vn.drwp.identity.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import vn.drwp.identity.domain.Grant;
import vn.drwp.platform.support.Ids;

/** Bảng {@code idn_*} trong schema tenant hiện tại (DataSource định tuyến theo tenant). */
@Repository
public class IdentityRepository {

    private final JdbcClient jdbc;

    public IdentityRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record RoleRow(UUID id, String code, String name, String description, boolean system) {
    }

    public long policyVersion() {
        return jdbc.sql("SELECT version FROM idn_policy_version WHERE id = 1").query(Long.class).single();
    }

    public void bumpPolicyVersion() {
        jdbc.sql("UPDATE idn_policy_version SET version = version + 1 WHERE id = 1").update();
    }

    public List<Grant> grantsOfSubject(String subject, List<String> resources) {
        return jdbc.sql("""
                SELECT rp.resource_code, rp.allow_mask, rp.deny_mask
                FROM idn_user_role ur JOIN idn_role_permission rp ON rp.role_id = ur.role_id
                WHERE ur.subject = :subject AND rp.resource_code IN (:resources)
                """)
                .param("subject", subject)
                .param("resources", resources)
                .query((rs, n) -> new Grant(rs.getString("resource_code"), rs.getLong("allow_mask"), rs.getLong("deny_mask")))
                .list();
    }

    public Set<String> roleCodesOf(String subject) {
        return new TreeSet<>(jdbc.sql("""
                SELECT r.code FROM idn_user_role ur JOIN idn_role r ON r.id = ur.role_id WHERE ur.subject = :subject
                """).param("subject", subject).query(String.class).list());
    }

    public List<String> subjectsInRole(String roleCode) {
        return jdbc.sql("""
                SELECT ur.subject FROM idn_user_role ur JOIN idn_role r ON r.id = ur.role_id WHERE r.code = :code ORDER BY ur.subject
                """).param("code", roleCode).query(String.class).list();
    }

    public Optional<RoleRow> findRole(String code) {
        return jdbc.sql("SELECT id, code, name, description, system FROM idn_role WHERE code = :code")
                .param("code", code)
                .query((rs, n) -> new RoleRow(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                        rs.getString("description"), rs.getBoolean("system")))
                .optional();
    }

    public List<RoleRow> roles() {
        return jdbc.sql("SELECT id, code, name, description, system FROM idn_role ORDER BY code")
                .query((rs, n) -> new RoleRow(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString("name"),
                        rs.getString("description"), rs.getBoolean("system")))
                .list();
    }

    public UUID insertRoleIfAbsent(String code, String name, String description, boolean system) {
        UUID id = Ids.newId();
        jdbc.sql("""
                INSERT INTO idn_role (id, code, name, description, system) VALUES (:id, :code, :name, :description, :system)
                ON CONFLICT (code) DO NOTHING
                """).param("id", id).param("code", code).param("name", name).param("description", description)
                .param("system", system).update();
        return findRole(code).orElseThrow().id();
    }

    public int updateRole(String code, String name, String description) {
        return jdbc.sql("UPDATE idn_role SET name = :name, description = :description, updated_at = now() WHERE code = :code")
                .param("name", name).param("description", description).param("code", code).update();
    }

    public int deleteRole(String code) {
        return jdbc.sql("DELETE FROM idn_role WHERE code = :code AND system = false").param("code", code).update();
    }

    public List<Grant> grantsOfRole(UUID roleId) {
        return jdbc.sql("SELECT resource_code, allow_mask, deny_mask FROM idn_role_permission WHERE role_id = :id ORDER BY resource_code")
                .param("id", roleId)
                .query((rs, n) -> new Grant(rs.getString("resource_code"), rs.getLong("allow_mask"), rs.getLong("deny_mask")))
                .list();
    }

    public void replaceGrants(UUID roleId, List<Grant> grants) {
        jdbc.sql("DELETE FROM idn_role_permission WHERE role_id = :id").param("id", roleId).update();
        for (Grant g : grants) {
            jdbc.sql("""
                    INSERT INTO idn_role_permission (role_id, resource_code, allow_mask, deny_mask)
                    VALUES (:id, :resource, :allow, :deny)
                    ON CONFLICT (role_id, resource_code) DO UPDATE SET allow_mask = EXCLUDED.allow_mask,
                        deny_mask = EXCLUDED.deny_mask, updated_at = now()
                    """).param("id", roleId).param("resource", g.resource()).param("allow", g.allowMask())
                    .param("deny", g.denyMask()).update();
        }
    }

    public void assign(String subject, UUID roleId, String by) {
        jdbc.sql("""
                INSERT INTO idn_user_role (subject, role_id, granted_by) VALUES (:subject, :role, :by)
                ON CONFLICT (subject, role_id) DO NOTHING
                """).param("subject", subject).param("role", roleId).param("by", by).update();
    }

    public int unassign(String subject, UUID roleId) {
        return jdbc.sql("DELETE FROM idn_user_role WHERE subject = :subject AND role_id = :role")
                .param("subject", subject).param("role", roleId).update();
    }

    public int unassignAll(String subject) {
        return jdbc.sql("DELETE FROM idn_user_role WHERE subject = :subject").param("subject", subject).update();
    }

    public long countSubjectsInRole(UUID roleId) {
        return jdbc.sql("SELECT count(*) FROM idn_user_role WHERE role_id = :id").param("id", roleId).query(Long.class).single();
    }
}
