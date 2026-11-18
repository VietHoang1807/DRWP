package vn.drwp.file.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import vn.drwp.file.api.DocumentUploaded;
import vn.drwp.file.domain.ContentPolicy;
import vn.drwp.file.infrastructure.FileRepository;
import vn.drwp.file.infrastructure.FileRepository.FileRow;
import vn.drwp.file.infrastructure.ObjectStorage;
import vn.drwp.platform.kernel.error.ForbiddenException;
import vn.drwp.platform.kernel.error.NotFoundException;
import vn.drwp.platform.kernel.error.ValidationFailedException;
import vn.drwp.platform.kernel.event.BusinessAuditEvent;
import vn.drwp.platform.messaging.OutboxWriter;
import vn.drwp.platform.support.CorrelationId;
import vn.drwp.platform.support.Ids;
import vn.drwp.platform.tenancy.TenantContext;
import vn.drwp.platform.web.security.Actor;
import vn.drwp.record.api.RecordAttachmentPolicy;
import vn.drwp.workflow.api.WorkflowParticipation;

/**
 * Tệp đính kèm: kiểm tra nội dung trước khi lưu, khóa lưu trữ có tiền tố tenant, quyền xem theo hồ sơ
 * (chủ hồ sơ / VIEW / người tham gia quy trình), audit tải về (tài liệu 02 mục 4).
 */
@Service
public class FileService {

    public record FileView(UUID id, UUID recordId, String name, String contentType, long sizeBytes, String sha256,
                           String status, String uploadedBy, Instant uploadedAt) {
        static FileView of(FileRow f) {
            return new FileView(f.id(), f.recordId(), f.originalName(), f.contentType(), f.sizeBytes(), f.sha256(), f.status(),
                    f.uploadedBy(), f.uploadedAt());
        }
    }

    public record Download(FileView meta, InputStream content) {
    }

    private final FileRepository repository;
    private final ObjectStorage storage;
    private final ContentPolicy policy;
    private final RecordAttachmentPolicy recordPolicy;
    private final WorkflowParticipation participation;
    private final ApplicationEventPublisher events;
    private final OutboxWriter outbox;
    private final TransactionTemplate separateTx;

    FileService(FileRepository repository, ObjectStorage storage, ContentPolicy policy, RecordAttachmentPolicy recordPolicy,
                WorkflowParticipation participation, ApplicationEventPublisher events, OutboxWriter outbox,
                PlatformTransactionManager tm) {
        this.repository = repository;
        this.storage = storage;
        this.policy = policy;
        this.recordPolicy = recordPolicy;
        this.participation = participation;
        this.events = events;
        this.outbox = outbox;
        this.separateTx = new TransactionTemplate(tm);
        this.separateTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Transactional
    public FileView upload(Actor actor, UUID recordId, String originalName, String contentType, InputStream content) {
        if (recordId != null && !recordPolicy.canAttach(recordId, actor.subject())) {
            throw new NotFoundException("Record", recordId);
        }
        String tenant = TenantContext.require().value();
        UUID id = Ids.newId();
        String name = ContentPolicy.sanitizeName(originalName);
        Path temp = null;
        try {
            temp = Files.createTempFile("drwp-upload-", ".bin");
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            long size;
            try (OutputStream out = new DigestOutputStream(Files.newOutputStream(temp), sha)) {
                size = content.transferTo(out);
            }
            byte[] head = readHead(temp);
            ContentPolicy.Verdict verdict = policy.check(name, contentType, size, head);
            String digest = HexFormat.of().formatHex(sha.digest());
            Instant now = Instant.now();
            if (!verdict.accepted()) {
                // ghi nhận tệp bị từ chối trong transaction riêng để không mất khi request lỗi
                long recordedSize = Math.max(size, 1);
                separateTx.executeWithoutResult(s -> {
                    repository.insert(new FileRow(id, recordId, name, safeType(contentType), recordedSize, digest, null,
                            "REJECTED", verdict.reason(), actor.subject(), now));
                    audit(actor, "FILE_REJECTED", id, Map.of("reason", verdict.reason()), BusinessAuditEvent.Outcome.FAILED);
                });
                throw ValidationFailedException.single("file", verdict.reason(), "File rejected: " + verdict.reason());
            }
            String key = "%s/%d/%02d/%s".formatted(tenant, now.atZone(ZoneOffset.UTC).getYear(),
                    now.atZone(ZoneOffset.UTC).getMonthValue(), id);
            storage.put(key, temp, size, contentType);
            FileRow row = new FileRow(id, recordId, name, safeType(contentType), size, digest, key, "AVAILABLE", verdict.reason(),
                    actor.subject(), now);
            repository.insert(row);
            events.publishEvent(new DocumentUploaded(tenant, id, recordId, row.contentType(), size, actor.subject(), now));
            outbox.append("file", id.toString(), "DocumentUploaded", 1, Map.of("fileId", id.toString(),
                    "recordId", recordId == null ? "" : recordId.toString(), "contentType", row.contentType(), "sizeBytes", size));
            audit(actor, "FILE_UPLOADED", id, Map.of("size", String.valueOf(size)), BusinessAuditEvent.Outcome.SUCCESS);
            return FileView.of(row);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // best effort
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public FileView metadata(Actor actor, UUID id) {
        return FileView.of(loadReadable(actor, id));
    }

    @Transactional(readOnly = true)
    public List<FileView> ofRecord(Actor actor, UUID recordId) {
        if (!canReadRecord(actor.subject(), recordId)) {
            throw new NotFoundException("Record", recordId);
        }
        return repository.byRecord(recordId).stream().map(FileView::of).toList();
    }

    @Transactional
    public Download download(Actor actor, UUID id) {
        FileRow f = loadReadable(actor, id);
        audit(actor, "FILE_DOWNLOADED", id, Map.of(), BusinessAuditEvent.Outcome.SUCCESS);
        return new Download(FileView.of(f), storage.get(f.storageKey()));
    }

    @Transactional
    public void delete(Actor actor, UUID id) {
        FileRow f = loadReadable(actor, id);
        boolean allowed = f.recordId() == null ? f.uploadedBy().equals(actor.subject())
                : recordPolicy.canAttach(f.recordId(), actor.subject());
        if (!allowed) {
            throw new ForbiddenException("Cannot delete this file");
        }
        if (repository.markDeleted(id) == 1) {
            storage.delete(f.storageKey());
            audit(actor, "FILE_DELETED", id, Map.of(), BusinessAuditEvent.Outcome.SUCCESS);
        }
    }

    private FileRow loadReadable(Actor actor, UUID id) {
        FileRow f = repository.find(id).filter(r -> "AVAILABLE".equals(r.status()))
                .orElseThrow(() -> new NotFoundException("File", id));
        boolean ok = f.uploadedBy().equals(actor.subject())
                || (f.recordId() != null && canReadRecord(actor.subject(), f.recordId()));
        if (!ok) {
            throw new NotFoundException("File", id);
        }
        return f;
    }

    private boolean canReadRecord(String subject, UUID recordId) {
        return recordPolicy.canView(recordId, subject) || participation.isParticipant(recordId, subject);
    }

    private static byte[] readHead(Path p) throws IOException {
        try (InputStream in = Files.newInputStream(p)) {
            byte[] buf = in.readNBytes(512);
            return Arrays.copyOf(buf, buf.length);
        }
    }

    private static String safeType(String contentType) {
        return contentType == null ? "application/octet-stream" : contentType.split(";")[0].trim();
    }

    private void audit(Actor actor, String action, UUID fileId, Map<String, String> detail, BusinessAuditEvent.Outcome outcome) {
        events.publishEvent(new BusinessAuditEvent(TenantContext.require().value(), actor.subject(), action, "file",
                fileId.toString(), outcome, detail, CorrelationId.current(), null));
    }
}
