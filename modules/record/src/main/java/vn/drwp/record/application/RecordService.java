package vn.drwp.record.application;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.drwp.identity.api.PermissionChecker;
import vn.drwp.metadata.api.FormDefinitionReader;
import vn.drwp.metadata.api.FormDefinitionReader.PublishedForm;
import vn.drwp.platform.kernel.error.ConflictException;
import vn.drwp.platform.kernel.error.NotFoundException;
import vn.drwp.platform.kernel.error.ValidationFailedException;
import vn.drwp.platform.kernel.error.Violation;
import vn.drwp.platform.kernel.event.BusinessAuditEvent;
import vn.drwp.platform.kernel.security.Action;
import vn.drwp.platform.kernel.security.ResourceCode;
import vn.drwp.platform.messaging.OutboxWriter;
import vn.drwp.platform.support.CorrelationId;
import vn.drwp.platform.support.Ids;
import vn.drwp.platform.tenancy.TenantContext;
import vn.drwp.platform.web.security.Actor;
import vn.drwp.record.api.RecordStatus;
import vn.drwp.record.api.RecordStatusChanged;
import vn.drwp.record.api.RecordSubmitted;
import vn.drwp.record.domain.DynamicRecord;
import vn.drwp.record.domain.RecordQuery;
import vn.drwp.record.infrastructure.RecordRepository;

/**
 * Use case hồ sơ. Quyền: {@code record:<FORM_CODE>} — CREATE để tạo; VIEW để xem hồ sơ của người khác.
 * Chủ hồ sơ luôn xem/sửa (khi còn sửa được) hồ sơ của mình (ABAC ownership). Hồ sơ không được phép xem trả 404 (chống BOLA dò ID).
 */
@Service
public class RecordService {

    public static final String RESOURCE_TYPE = "record";

    public record RecordView(UUID id, String recordCode, String formCode, UUID formVersionId, int formVersionNo,
                             RecordStatus status, Map<String, Object> data, String owner, UUID workflowInstanceId,
                             Instant createdAt, Instant submittedAt, Instant completedAt, long version) {
    }

    public record PageView(List<RecordView> items, int page, int size, long total) {
    }

    private final RecordRepository repository;
    private final FormDefinitionReader forms;
    private final PermissionChecker permissions;
    private final ApplicationEventPublisher events;
    private final OutboxWriter outbox;

    RecordService(RecordRepository repository, FormDefinitionReader forms, PermissionChecker permissions,
                  ApplicationEventPublisher events, OutboxWriter outbox) {
        this.repository = repository;
        this.forms = forms;
        this.permissions = permissions;
        this.events = events;
        this.outbox = outbox;
    }

    static String resource(String formCode) {
        return ResourceCode.of(RESOURCE_TYPE, formCode);
    }

    @Transactional
    public RecordView create(Actor actor, String formCode, Map<String, Object> data, boolean submit) {
        permissions.require(actor.subject(), resource(formCode), Action.CREATE);
        PublishedForm form = forms.latestPublished(formCode).orElseThrow(() -> new NotFoundException("Published form", formCode));
        Map<String, Object> payload = data == null ? Map.of() : data;
        requireValid(forms.validate(form.formVersionId(), payload, submit));
        Instant now = Instant.now();
        DynamicRecord record = DynamicRecord.draft(Ids.newId(), repository.nextCode(formCode, now), formCode,
                form.formVersionId(), actor.subject(), payload, now);
        repository.insert(record);
        repository.insertRevision(record.id(), "CREATED", record.status(), record.mutableDataCopy(), actor.subject());
        changed(record, "CREATED");
        audit(actor, "RECORD_CREATED", record, Map.of());
        if (submit) {
            DynamicRecord reloaded = load(record.id());
            return doSubmit(actor, reloaded);
        }
        return view(load(record.id()), true);
    }

    @Transactional
    public RecordView update(Actor actor, UUID id, Map<String, Object> data, Long expectedVersion) {
        DynamicRecord record = loadVisible(actor, id);
        checkVersion(record, expectedVersion);
        Map<String, Object> payload = data == null ? Map.of() : data;
        requireValid(forms.validate(record.formVersionId(), payload, false));
        record.updateData(actor.subject(), payload);
        repository.update(record);
        repository.insertRevision(record.id(), "UPDATED", record.status(), record.mutableDataCopy(), actor.subject());
        changed(record, "UPDATED");
        return view(load(id), true);
    }

    @Transactional
    public RecordView submit(Actor actor, UUID id, Long expectedVersion) {
        DynamicRecord record = loadVisible(actor, id);
        checkVersion(record, expectedVersion);
        return doSubmit(actor, record);
    }

    private RecordView doSubmit(Actor actor, DynamicRecord record) {
        // Validate đầy đủ theo đúng phiên bản biểu mẫu mà hồ sơ gắn (bất biến)
        requireValid(forms.validate(record.formVersionId(), record.mutableDataCopy(), true));
        RecordStatus from = record.status();
        Instant now = Instant.now();
        record.submit(actor.subject(), now);
        repository.update(record);
        repository.insertRevision(record.id(), "SUBMITTED", record.status(), record.mutableDataCopy(), actor.subject());
        String tenant = TenantContext.require().value();
        // ADR 016: Workflow khởi tạo quy trình trong transaction riêng sau commit
        events.publishEvent(new RecordSubmitted(tenant, record.id(), record.recordCode(), record.formCode(),
                record.formVersionId(), actor.subject(), Ids.newId(), now));
        events.publishEvent(new RecordStatusChanged(tenant, record.id(), record.recordCode(), record.formCode(), from,
                record.status(), record.owner(), actor.subject(), now));
        changed(record, "SUBMITTED");
        audit(actor, "RECORD_SUBMITTED", record, Map.of("submission", String.valueOf(record.submissionCount())));
        return view(load(record.id()), true);
    }

    @Transactional
    public RecordView cancel(Actor actor, UUID id) {
        DynamicRecord record = loadVisible(actor, id);
        RecordStatus from = record.status();
        record.cancel(actor.subject(), Instant.now());
        repository.update(record);
        repository.insertRevision(record.id(), "CANCELLED", record.status(), record.mutableDataCopy(), actor.subject());
        events.publishEvent(new RecordStatusChanged(TenantContext.require().value(), record.id(), record.recordCode(),
                record.formCode(), from, record.status(), record.owner(), actor.subject(), Instant.now()));
        changed(record, "CANCELLED");
        audit(actor, "RECORD_CANCELLED", record, Map.of());
        return view(load(id), true);
    }

    @Transactional(readOnly = true)
    public RecordView get(Actor actor, UUID id) {
        return view(loadVisible(actor, id), true);
    }

    @Transactional(readOnly = true)
    public List<RecordRepository.Revision> revisions(Actor actor, UUID id) {
        loadVisible(actor, id);
        return repository.revisions(id);
    }

    @Transactional(readOnly = true)
    public PageView search(Actor actor, RecordQuery query) {
        String restrictToOwner = null;
        Set<String> filterable = Set.of();
        if (query.formCode() != null) {
            if (!permissions.isAllowed(actor.subject(), resource(query.formCode()), Action.VIEW)) {
                restrictToOwner = actor.subject();
            }
            filterable = forms.filterableFields(query.formCode());
        } else {
            if (!query.dataEquals().isEmpty()) {
                throw new IllegalArgumentException("Data filters require formCode");
            }
            if (!permissions.isAllowed(actor.subject(), "record:*", Action.VIEW)) {
                restrictToOwner = actor.subject();
            }
        }
        RecordRepository.Page page = repository.search(query, filterable, restrictToOwner);
        List<RecordView> items = page.items().stream().map(r -> view(r, false)).toList();
        return new PageView(items, query.page(), query.size(), page.total());
    }

    /** Dùng cho chính sách đính kèm và tham chiếu: có quyền xem không. */
    boolean canView(String subject, DynamicRecord record) {
        return record.isOwner(subject) || permissions.isAllowed(subject, resource(record.formCode()), Action.VIEW);
    }

    private DynamicRecord loadVisible(Actor actor, UUID id) {
        DynamicRecord record = load(id);
        if (!canView(actor.subject(), record)) {
            throw new NotFoundException("Record", id);
        }
        return record;
    }

    private DynamicRecord load(UUID id) {
        return repository.find(id).orElseThrow(() -> new NotFoundException("Record", id));
    }

    private static void checkVersion(DynamicRecord record, Long expectedVersion) {
        if (expectedVersion != null && expectedVersion != record.version()) {
            throw new ConflictException("STALE_RECORD", "Record version is " + record.version() + ", expected " + expectedVersion);
        }
    }

    private static void requireValid(List<Violation> violations) {
        if (!violations.isEmpty()) {
            throw new ValidationFailedException(violations);
        }
    }

    private RecordView view(DynamicRecord r, boolean includeSensitive) {
        PublishedForm form = forms.version(r.formVersionId());
        Map<String, Object> data = r.mutableDataCopy();
        if (!includeSensitive) {
            Set<String> sensitive = forms.sensitiveFields(r.formVersionId());
            data = new LinkedHashMap<>(data);
            data.keySet().removeAll(sensitive);
        }
        return new RecordView(r.id(), r.recordCode(), r.formCode(), r.formVersionId(), form.versionNo(), r.status(), data,
                r.owner(), r.workflowInstanceId(), r.createdAt(), r.submittedAt(), r.completedAt(), r.version());
    }

    void changed(DynamicRecord r, String changeType) {
        // Sự kiện externalized cho search/reporting: chỉ metadata, không có dữ liệu động (tối thiểu hóa PII)
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recordId", r.id().toString());
        payload.put("recordCode", r.recordCode());
        payload.put("formCode", r.formCode());
        payload.put("formVersionId", r.formVersionId().toString());
        payload.put("status", r.status().name());
        payload.put("changeType", changeType);
        outbox.append("record", r.id().toString(), "RecordChanged", 1, payload);
    }

    void audit(Actor actor, String action, DynamicRecord r, Map<String, String> detail) {
        Map<String, String> d = new LinkedHashMap<>(detail);
        d.put("recordCode", r.recordCode());
        d.put("status", r.status().name());
        events.publishEvent(new BusinessAuditEvent(TenantContext.require().value(), actor.subject(), action, "record",
                r.id().toString(), BusinessAuditEvent.Outcome.SUCCESS, d, CorrelationId.current(), null));
    }
}
