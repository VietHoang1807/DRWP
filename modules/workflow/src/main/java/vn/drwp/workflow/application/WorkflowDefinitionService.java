package vn.drwp.workflow.application;

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
import vn.drwp.platform.json.Json;
import vn.drwp.platform.kernel.error.ConflictException;
import vn.drwp.platform.kernel.error.InvalidStateException;
import vn.drwp.platform.kernel.error.NotFoundException;
import vn.drwp.platform.kernel.event.BusinessAuditEvent;
import vn.drwp.platform.kernel.security.Action;
import vn.drwp.platform.support.CorrelationId;
import vn.drwp.platform.support.Ids;
import vn.drwp.platform.tenancy.TenantContext;
import vn.drwp.platform.web.security.Actor;
import vn.drwp.workflow.domain.WorkflowSpec;
import vn.drwp.workflow.infrastructure.WorkflowRepository;
import vn.drwp.workflow.infrastructure.WorkflowRepository.DefinitionRow;
import vn.drwp.workflow.infrastructure.WorkflowRepository.VersionRow;

/**
 * Định nghĩa quy trình và phiên bản (ADR 006: PUBLISHED bất biến; publish V2 chỉ ảnh hưởng instance mới).
 * Quyền CONFIGURE/VIEW trên resource {@code workflow}.
 */
@Service
public class WorkflowDefinitionService {

    public static final String RESOURCE = "workflow";
    private static final Pattern CODE = Pattern.compile("^[A-Z][A-Z0-9_]{1,63}$");

    public record DefinitionView(String code, String name, String description, String formCode, String status,
                                 Integer latestPublishedVersion, Integer draftVersion) {
    }

    public record VersionView(UUID id, String definitionCode, int versionNo, String status, Map<String, Object> spec,
                              String checksum, String createdBy, Instant createdAt, String publishedBy, Instant publishedAt) {
        static VersionView of(VersionRow v) {
            return new VersionView(v.id(), v.definitionCode(), v.versionNo(), v.status(), v.spec(), v.checksum(),
                    v.createdBy(), v.createdAt(), v.publishedBy(), v.publishedAt());
        }
    }

    private final WorkflowRepository repository;
    private final PermissionChecker permissions;
    private final ApplicationEventPublisher events;
    private final Json json;

    WorkflowDefinitionService(WorkflowRepository repository, PermissionChecker permissions, ApplicationEventPublisher events,
                              Json json) {
        this.repository = repository;
        this.permissions = permissions;
        this.events = events;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public List<DefinitionView> list(Actor actor) {
        permissions.require(actor.subject(), RESOURCE, Action.VIEW);
        return repository.definitions().stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public DefinitionView get(Actor actor, String code) {
        permissions.require(actor.subject(), RESOURCE, Action.VIEW);
        return view(load(code));
    }

    @Transactional(readOnly = true)
    public List<VersionView> versions(Actor actor, String code) {
        permissions.require(actor.subject(), RESOURCE, Action.VIEW);
        load(code);
        return repository.versions(code).stream().map(VersionView::of).toList();
    }

    @Transactional(readOnly = true)
    public VersionView version(Actor actor, String code, int versionNo) {
        permissions.require(actor.subject(), RESOURCE, Action.VIEW);
        return VersionView.of(loadVersion(code, versionNo));
    }

    @Transactional
    public VersionView create(Actor actor, String code, String name, String description, String formCode,
                              Map<String, Object> specRaw) {
        permissions.require(actor.subject(), RESOURCE, Action.CONFIGURE);
        if (code == null || !CODE.matcher(code).matches() || formCode == null || !CODE.matcher(formCode).matches()) {
            throw new IllegalArgumentException("code and formCode must match " + CODE.pattern());
        }
        if (repository.definition(code).isPresent()) {
            throw new ConflictException("WORKFLOW_EXISTS", "Workflow already exists: " + code);
        }
        repository.activeDefinitionForForm(formCode).ifPresent(d -> {
            throw new ConflictException("FORM_ALREADY_BOUND", "Form " + formCode + " is already bound to workflow " + d.code());
        });
        WorkflowSpec spec = WorkflowSpec.parse(specRaw);
        UUID defId = Ids.newId();
        UUID verId = Ids.newId();
        repository.insertDefinition(defId, code, name, description, formCode, actor.subject());
        repository.insertVersion(verId, defId, 1, spec.toTree(), actor.subject());
        audit(actor, "WORKFLOW_CREATED", code, Map.of("formCode", formCode));
        return VersionView.of(repository.version(verId).orElseThrow());
    }

    @Transactional
    public DefinitionView update(Actor actor, String code, String name, String description, String status) {
        permissions.require(actor.subject(), RESOURCE, Action.CONFIGURE);
        DefinitionRow d = load(code);
        String target = status == null ? d.status() : status;
        if (!target.equals("ACTIVE") && !target.equals("ARCHIVED")) {
            throw new IllegalArgumentException("status must be ACTIVE or ARCHIVED");
        }
        repository.updateDefinition(code, name == null ? d.name() : name, description, target);
        audit(actor, "WORKFLOW_UPDATED", code, Map.of("status", target));
        return view(load(code));
    }

    @Transactional
    public VersionView newDraft(Actor actor, String code, Map<String, Object> specRaw) {
        permissions.require(actor.subject(), RESOURCE, Action.CONFIGURE);
        DefinitionRow d = load(code);
        if (repository.versions(code).stream().anyMatch(v -> v.status().equals("DRAFT"))) {
            throw new ConflictException("DRAFT_EXISTS", "Workflow " + code + " already has a draft");
        }
        Map<String, Object> base = specRaw != null ? specRaw
                : repository.versions(code).stream().findFirst().map(VersionRow::spec).orElseThrow();
        WorkflowSpec spec = WorkflowSpec.parse(base);
        UUID id = Ids.newId();
        repository.insertVersion(id, d.id(), repository.maxVersionNo(d.id()) + 1, spec.toTree(), actor.subject());
        return VersionView.of(repository.version(id).orElseThrow());
    }

    @Transactional
    public VersionView updateDraft(Actor actor, String code, int versionNo, Map<String, Object> specRaw) {
        permissions.require(actor.subject(), RESOURCE, Action.CONFIGURE);
        VersionRow v = loadVersion(code, versionNo);
        if (!v.status().equals("DRAFT")) {
            throw new InvalidStateException("Only DRAFT versions can be modified");
        }
        WorkflowSpec spec = WorkflowSpec.parse(specRaw);
        if (repository.updateDraft(v.id(), spec.toTree()) != 1) {
            throw new ConflictException("CONCURRENT_UPDATE", "Draft changed concurrently");
        }
        return VersionView.of(repository.version(v.id()).orElseThrow());
    }

    @Transactional
    public VersionView publish(Actor actor, String code, int versionNo) {
        permissions.require(actor.subject(), RESOURCE, Action.CONFIGURE);
        VersionRow v = loadVersion(code, versionNo);
        if (!v.status().equals("DRAFT")) {
            throw new InvalidStateException("Only DRAFT versions can be published");
        }
        WorkflowSpec spec = WorkflowSpec.parse(v.spec());
        String checksum = sha256(json.write(spec.toTree()));
        if (repository.publish(v.id(), checksum, actor.subject()) != 1) {
            throw new ConflictException("CONCURRENT_UPDATE", "Version changed concurrently");
        }
        audit(actor, "WORKFLOW_PUBLISHED", code, Map.of("version", String.valueOf(versionNo), "checksum", checksum));
        return VersionView.of(repository.version(v.id()).orElseThrow());
    }

    @Transactional
    public VersionView deprecate(Actor actor, String code, int versionNo) {
        permissions.require(actor.subject(), RESOURCE, Action.CONFIGURE);
        VersionRow v = loadVersion(code, versionNo);
        if (!v.status().equals("PUBLISHED")) {
            throw new InvalidStateException("Only PUBLISHED versions can be deprecated");
        }
        repository.deprecate(v.id());
        audit(actor, "WORKFLOW_DEPRECATED", code, Map.of("version", String.valueOf(versionNo)));
        return VersionView.of(repository.version(v.id()).orElseThrow());
    }

    private DefinitionView view(DefinitionRow d) {
        List<VersionRow> versions = repository.versions(d.code());
        Integer published = versions.stream().filter(v -> v.status().equals("PUBLISHED")).map(VersionRow::versionNo).findFirst().orElse(null);
        Integer draft = versions.stream().filter(v -> v.status().equals("DRAFT")).map(VersionRow::versionNo).findFirst().orElse(null);
        return new DefinitionView(d.code(), d.name(), d.description(), d.formCode(), d.status(), published, draft);
    }

    private DefinitionRow load(String code) {
        return repository.definition(code).orElseThrow(() -> new NotFoundException("Workflow", code));
    }

    private VersionRow loadVersion(String code, int versionNo) {
        return repository.version(code, versionNo).orElseThrow(() -> new NotFoundException("Workflow version", code + "@" + versionNo));
    }

    private void audit(Actor actor, String action, String code, Map<String, String> detail) {
        events.publishEvent(new BusinessAuditEvent(TenantContext.require().value(), actor.subject(), action, "workflow", code,
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
