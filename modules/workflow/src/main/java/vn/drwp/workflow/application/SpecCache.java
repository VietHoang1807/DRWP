package vn.drwp.workflow.application;

import java.time.Duration;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import vn.drwp.platform.kernel.error.NotFoundException;
import vn.drwp.platform.tenancy.TenantContext;
import vn.drwp.workflow.domain.WorkflowSpec;
import vn.drwp.workflow.infrastructure.WorkflowRepository;

/** Cache spec đã parse theo (tenant, versionId). Phiên bản PUBLISHED bất biến nên không cần invalidation. */
@Component
class SpecCache {

    record Key(String tenant, UUID versionId) {
    }

    private final WorkflowRepository repository;
    private final Cache<Key, WorkflowSpec> cache = Caffeine.newBuilder().maximumSize(2_000).expireAfterAccess(Duration.ofHours(1)).build();

    SpecCache(WorkflowRepository repository) {
        this.repository = repository;
    }

    WorkflowSpec get(UUID versionId) {
        return cache.get(new Key(TenantContext.require().value(), versionId), k -> WorkflowSpec.parse(
                repository.version(versionId).orElseThrow(() -> new NotFoundException("Workflow version", versionId)).spec()));
    }
}
