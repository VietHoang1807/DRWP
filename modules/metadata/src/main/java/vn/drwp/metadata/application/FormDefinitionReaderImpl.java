package vn.drwp.metadata.application;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import vn.drwp.metadata.api.FormDefinitionReader;
import vn.drwp.metadata.domain.FieldDefinition;
import vn.drwp.metadata.domain.FormSchema;
import vn.drwp.metadata.domain.FormValidator;
import vn.drwp.metadata.domain.FormVersionStatus;
import vn.drwp.metadata.infrastructure.MetadataRepository;
import vn.drwp.metadata.infrastructure.MetadataRepository.VersionRow;
import vn.drwp.platform.kernel.error.InvalidStateException;
import vn.drwp.platform.kernel.error.NotFoundException;
import vn.drwp.platform.kernel.error.Violation;
import vn.drwp.platform.tenancy.TenantContext;

/**
 * Cài đặt contract. Phiên bản đã phát hành bất biến nên cache an toàn; khóa cache gồm tenant để không rò
 * giữa tenant (tài liệu 02 mục 3 "Hibernate/cache leakage").
 */
@Component
class FormDefinitionReaderImpl implements FormDefinitionReader {

    record Key(String tenant, UUID versionId) {
    }

    private final MetadataRepository repository;
    private final Cache<Key, FormSchema> schemas = Caffeine.newBuilder().maximumSize(5_000).expireAfterAccess(Duration.ofHours(1)).build();

    FormDefinitionReaderImpl(MetadataRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<PublishedForm> latestPublished(String formCode) {
        return repository.latestPublished(formCode).map(FormDefinitionReaderImpl::toPublished);
    }

    @Override
    public PublishedForm version(UUID formVersionId) {
        VersionRow v = repository.version(formVersionId).orElseThrow(() -> new NotFoundException("Form version", formVersionId));
        if (v.status() == FormVersionStatus.DRAFT) {
            throw new InvalidStateException("Form version " + formVersionId + " is not published");
        }
        return toPublished(v);
    }

    @Override
    public List<Violation> validate(UUID formVersionId, Map<String, Object> data, boolean full) {
        return FormValidator.validate(schema(formVersionId), data, full);
    }

    @Override
    public Set<String> filterableFields(String formCode) {
        return repository.latestPublished(formCode)
                .map(v -> schema(v.id()).fields().stream().filter(FieldDefinition::filterable).map(FieldDefinition::key)
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    @Override
    public Set<String> sensitiveFields(UUID formVersionId) {
        return schema(formVersionId).fields().stream().filter(FieldDefinition::sensitive).map(FieldDefinition::key)
                .collect(Collectors.toSet());
    }

    private FormSchema schema(UUID versionId) {
        Key key = new Key(TenantContext.require().value(), versionId);
        return schemas.get(key, k -> {
            VersionRow v = repository.version(versionId).orElseThrow(() -> new NotFoundException("Form version", versionId));
            if (v.status() == FormVersionStatus.DRAFT) {
                throw new InvalidStateException("Form version " + versionId + " is not published");
            }
            return FormSchema.parse(v.schema());
        });
    }

    private static PublishedForm toPublished(VersionRow v) {
        return new PublishedForm(v.id(), v.formCode(), v.formName(), v.versionNo(), v.status().name(), v.schema());
    }
}
