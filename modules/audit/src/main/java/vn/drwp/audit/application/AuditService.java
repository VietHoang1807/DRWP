package vn.drwp.audit.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.TreeMap;

import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.drwp.audit.infrastructure.AuditRepository;
import vn.drwp.audit.infrastructure.AuditRepository.AuditRow;
import vn.drwp.audit.infrastructure.AuditRepository.Filter;
import vn.drwp.identity.api.PermissionChecker;
import vn.drwp.platform.kernel.event.BusinessAuditEvent;
import vn.drwp.platform.kernel.security.Action;
import vn.drwp.platform.web.security.Actor;

@Service
public class AuditService {

    public static final String RESOURCE = "audit";

    public record Page(List<AuditRow> items, int page, int size, long total) {
    }

    private final AuditRepository repository;
    private final PermissionChecker permissions;

    AuditService(AuditRepository repository, PermissionChecker permissions) {
        this.repository = repository;
        this.permissions = permissions;
    }

    @ApplicationModuleListener
    public void on(BusinessAuditEvent event) {
        repository.append(dedupKey(event), event);
    }

    @Transactional(readOnly = true)
    public Page search(Actor actor, Filter filter, int page, int size) {
        permissions.require(actor.subject(), RESOURCE, Action.AUDIT);
        return new Page(repository.search(filter, size, (long) page * size), page, size, repository.count(filter));
    }

    /** Khóa xác định từ nội dung sự kiện: lần phát lại cho cùng khóa nên không ghi trùng. */
    static String dedupKey(BusinessAuditEvent e) {
        String material = String.join("|", e.tenantId(), String.valueOf(e.actor()), e.action(), String.valueOf(e.resourceType()),
                String.valueOf(e.resourceId()), e.outcome().name(), e.occurredAt().toString(), String.valueOf(e.correlationId()),
                new TreeMap<>(e.detail()).toString());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
