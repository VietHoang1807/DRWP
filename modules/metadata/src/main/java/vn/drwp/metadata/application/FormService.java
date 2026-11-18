package vn.drwp.metadata.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.drwp.identity.api.PermissionChecker;
import vn.drwp.metadata.api.FormPublished;
import vn.drwp.metadata.domain.FormSchema;
import vn.drwp.metadata.domain.FormValidator;
import vn.drwp.metadata.infrastructure.MetadataRepository;
import vn.drwp.metadata.infrastructure.MetadataRepository.FormRow;
import vn.drwp.metadata.infrastructure.MetadataRepository.VersionRow;
import vn.drwp.platform.json.Json;
import vn.drwp.platform.kernel.error.ConflictException;
import vn.drwp.platform.kernel.error.NotFoundException;
import vn.drwp.platform.kernel.error.Violation;
import vn.drwp.platform.kernel.event.BusinessAuditEvent;
import vn.drwp.platform.kernel.security.Action;
import vn.drwp.platform.support.CorrelationId;
import vn.drwp.platform.support.Ids;
import vn.drwp.platform.tenancy.TenantContext;
import vn.drwp.platform.web.security.Actor;

/**
 * Vòng đời biểu mẫu: tạo (v1 DRAFT) -> sửa nháp -> phát hành (bất biến) -> nháp mới -> deprecate.
 * Quyền: VIEW/CONFIGURE trên resource {@code metadata}.
 */
@Service
public class FormService {

    public static final String RESOURCE = "metadata";
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    public record FormView(String code, String name, String description, String status, Integer latestPublishedVersion,
                           Integer draftVersion) {
    }

    public record VersionView(UUID id, String formCode, int versionNo, String status, Map<String, Object> schema,
                              String checksum, String createdBy, Instant createdAt, String publishedBy, Instant publishedAt) {
        static VersionView of(VersionRow v) {
            return new VersionView(v.id(), v.formCode(), v.versionNo(), v.status().name(), v.schema(), v.checksum(),
                    v.createdBy(), v.createdAt(), v.publishedBy(), v.publishedAt());
        }
    }

    private final MetadataRepository repository;
    private final PermissionChecker permissions;
    private final ApplicationEventPublisher events;
    private final Json json;

    FormService(MetadataRepository repository, PermissionChecker permissions, ApplicationEventPublisher events, Json json) {
        this.repository = repository;
        this.permissions = permissions;
        this.events = events;
        this.json = json;
    }

    /** Danh sách biểu mẫu: mọi thành viên tenant được xem (cần để hiển thị form nhập liệu). */
    @Transactional(readOnly = true)
    public List<FormView> list() {
        return repository.forms().stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public FormView get(String code) {
        return view(loadForm(code));
    }

    @Transactional(readOnly = true)
    public VersionView latestPublished(String code) {
        return repository.latestPublished(code).map(VersionView::of)
                .orElseThrow(() -> new NotFoundException("Published form", code));
    }

    @Transactional(readOnly = true)
    public List<VersionView> versions(Actor actor, String code) {
        permissions.require(actor.subject(), RESOURCE, Action.VIEW);
        loadForm(code);
        return repository.versions(code).stream().map(VersionView::of).toList();
    }

    @Transactional(readOnly = true)
    public VersionView version(Actor actor, String code, int versionNo) {
        VersionRow v = loadVersion(code, versionNo);
        if (v.status() == vn.drwp.metadata.domain.FormVersionStatus.DRAFT) {
            permissions.require(actor.subject(), RESOURCE, Action.VIEW);
        }
        return VersionView.of(v);
    }

    @Transactional
    public VersionView create(Actor actor, String code, String name, String description, Map<String, Object> schemaRaw) {
        permissions.require(actor.subject(), RESOURCE, Action.CONFIGURE);
        if (code == null || !CODE.matcher(code).matches()) {
            throw new IllegalArgumentException("Form code must match " + CODE.pattern());
        }
        if (repository.findForm(code).isPresent()) {
            throw new ConflictException("FORM_EXISTS", "Form already exists: " + code);
        }
        FormSchema schema = FormSchema.parse(schemaRaw);
        UUID formId = Ids.newId();
        UUID versionId = Ids.newId();
        repository.insertForm(formId, code, name, description, actor.subject());
        repository.insertVersion(versionId, formId, 1, schema.toTree(), actor.subject());
        audit(actor, "FORM_CREATED", code, Map.of());
        return VersionView.of(repository.version(versionId).orElseThrow());
    }

    @Transactional
    public FormView update(Actor actor, String code, String name, String description, String status) {
        permissions.require(actor.subject(), RESOURCE, Action.CONFIGURE);
        FormRow f = loadForm(code);
        String target = status == null ? f.status() : status;
        if (!target.equals("ACTIVE") && !target.equals("ARCHIVED")) {
            throw new IllegalArgumentException("status must be ACTIVE or ARCHIVED");
        }
        repository.updateForm(code, name == null ? f.name() : name, description, target);
        audit(actor, "FORM_UPDATED", code, Map.of("status", target));
        return get(code);
    }

    /** Tạo nháp mới từ schema truyền vào, hoặc sao chép phiên bản mới nhất. */
    @Transactional
    public VersionView newDraft(Actor actor, String code, Map<String, Object> schemaRaw) {
        permissions.require(actor.subject(), RESOURCE, Action.CONFIGURE);
        FormRow f = loadForm(code);
        boolean hasDraft = repository.versions(code).stream()
                .anyMatch(v -> v.status() == vn.drwp.metadata.domain.FormVersionStatus.DRAFT);
        if (hasDraft) {
            throw new ConflictException("DRAFT_EXISTS", "Form " + code + " already has a draft");
        }
        Map<String, Object> base = schemaRaw != null ? schemaRaw
                : repository.versions(code).stream().findFirst().map(VersionRow::schema)
                .orElseThrow(() -> new NotFoundException("Form version", code));
        FormSchema schema = FormSchema.parse(base);
        UUID id = Ids.newId();
        repository.insertVersion(id, f.id(), repository.maxVersionNo(f.id()) + 1, schema.toTree(), actor.subject());
        return VersionView.of(repository.version(id).orElseThrow());
    }

    @Transactional
    public VersionView updateDraft(Actor actor, String code, int versionNo, Map<String, Object> schemaRaw) {
        permissions.require(actor.subject(), RESOURCE, Action.CONFIGURE);
        VersionRow v = loadVersion(code, versionNo);
        v.status().requireEditable();
        FormSchema schema = FormSchema.parse(schemaRaw);
        if (repository.updateDraftSchema(v.id(), schema.toTree()) != 1) {
            throw new ConflictException("CONCURRENT_UPDATE", "Draft changed concurrently");
        }
        return VersionView.of(repository.version(v.id()).orElseThrow());
    }

    @Transactional
    public void deleteDraft(Actor actor, String code, int versionNo) {
        permissions.require(actor.subject(), RESOURCE, Action.CONFIGURE);
        VersionRow v = loadVersion(code, versionNo);
        v.status().requireEditable();
        repository.deleteDraft(v.id());
    }

    @Transactional
    public VersionView publish(Actor actor, String code, int versionNo) {
        permissions.require(actor.subject(), RESOURCE, Action.CONFIGURE);
        VersionRow v = loadVersion(code, versionNo);
        v.status().requireCanPublish();
        FormSchema schema = FormSchema.parse(v.schema()); // kiểm tra lại trước khi đóng băng
        String checksum = sha256(json.write(schema.toTree()));
        if (repository.publish(v.id(), checksum, actor.subject()) != 1) {
            throw new ConflictException("CONCURRENT_UPDATE", "Version changed concurrently");
        }
        events.publishEvent(new FormPublished(TenantContext.require().value(), code, v.id(), versionNo, actor.subject(), Instant.now()));
        audit(actor, "FORM_PUBLISHED", code, Map.of("version", String.valueOf(versionNo), "checksum", checksum));
        return VersionView.of(repository.version(v.id()).orElseThrow());
    }

    @Transactional
    public VersionView deprecate(Actor actor, String code, int versionNo) {
        permissions.require(actor.subject(), RESOURCE, Action.CONFIGURE);
        VersionRow v = loadVersion(code, versionNo);
        v.status().requireCanDeprecate();
        repository.deprecate(v.id());
        audit(actor, "FORM_DEPRECATED", code, Map.of("version", String.valueOf(versionNo)));
        return VersionView.of(repository.version(v.id()).orElseThrow());
    }

    /** Xem trước kết quả validate (cho form designer). */
    @Transactional(readOnly = true)
    public List<Violation> preview(String code, int versionNo, Map<String, Object> data, boolean full) {
        VersionRow v = loadVersion(code, versionNo);
        return FormValidator.validate(FormSchema.parse(v.schema()), data, full);
    }

    private FormView view(FormRow f) {
        List<VersionRow> versions = repository.versions(f.code());
        Integer published = versions.stream().filter(v -> v.status() == vn.drwp.metadata.domain.FormVersionStatus.PUBLISHED)
                .map(VersionRow::versionNo).findFirst().orElse(null);
        Integer draft = versions.stream().filter(v -> v.status() == vn.drwp.metadata.domain.FormVersionStatus.DRAFT)
                .map(VersionRow::versionNo).findFirst().orElse(null);
        return new FormView(f.code(), f.name(), f.description(), f.status(), published, draft);
    }

    private FormRow loadForm(String code) {
        return repository.findForm(code).orElseThrow(() -> new NotFoundException("Form", code));
    }

    private VersionRow loadVersion(String code, int versionNo) {
        return repository.version(code, versionNo).orElseThrow(() -> new NotFoundException("Form version", code + "@" + versionNo));
    }

    private void audit(Actor actor, String action, String code, Map<String, String> detail) {
        events.publishEvent(new BusinessAuditEvent(TenantContext.require().value(), actor.subject(), action, "form", code,
                BusinessAuditEvent.Outcome.SUCCESS, detail, CorrelationId.current(), null));
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
