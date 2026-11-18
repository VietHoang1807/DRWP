package vn.drwp.identity.application;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import vn.drwp.identity.api.PermissionChecker;
import vn.drwp.identity.api.RoleDirectory;
import vn.drwp.identity.domain.EffectivePermission;
import vn.drwp.identity.domain.Grant;
import vn.drwp.identity.infrastructure.IdentityRepository;
import vn.drwp.platform.kernel.error.ForbiddenException;
import vn.drwp.platform.kernel.security.Action;
import vn.drwp.platform.kernel.security.PermissionMask;
import vn.drwp.platform.kernel.security.ResourceCode;
import vn.drwp.platform.tenancy.TenantContext;

/**
 * Cache quyền: khóa gồm tenant + principal + resource + policyVersion (tài liệu 01 mục 7). Đọc policyVersion
 * mỗi lần kiểm tra (một truy vấn PK) nên thu hồi quyền có hiệu lực ngay ở mọi node. Có thể thay Caffeine bằng Redis
 * khi chạy nhiều node mà không đổi khóa.
 */
@Service
class AuthorizationService implements PermissionChecker, RoleDirectory {

    private static final Logger security = LoggerFactory.getLogger("SECURITY");

    record CacheKey(String tenant, String subject, String resource, long policyVersion) {
    }

    private final IdentityRepository repository;
    private final Cache<CacheKey, Long> cache = Caffeine.newBuilder()
            .maximumSize(50_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();

    AuthorizationService(IdentityRepository repository) {
        this.repository = repository;
    }

    @Override
    public long effectiveMask(String subject, String resource) {
        ResourceCode.requireValid(resource);
        String tenant = TenantContext.require().value();
        long policyVersion = repository.policyVersion();
        return cache.get(new CacheKey(tenant, subject, resource, policyVersion), k -> {
            List<String> matching = ResourceCode.matchingGrants(resource);
            List<Grant> grants = repository.grantsOfSubject(subject, matching);
            return EffectivePermission.compute(grants, resource);
        });
    }

    @Override
    public boolean isAllowed(String subject, String resource, Action... actions) {
        long required = PermissionMask.of(actions);
        return PermissionMask.grants(effectiveMask(subject, resource), required);
    }

    @Override
    public void require(String subject, String resource, Action... actions) {
        if (!isAllowed(subject, resource, actions)) {
            security.info("Permission denied subject={} tenant={} resource={} actions={}",
                    subject, TenantContext.current().orElse(null), resource, List.of(actions));
            throw new ForbiddenException("Missing permission " + List.of(actions) + " on " + resource);
        }
    }

    @Override
    public Set<String> rolesOf(String subject) {
        return repository.roleCodesOf(subject);
    }

    @Override
    public List<String> subjectsInRole(String roleCode) {
        return repository.subjectsInRole(roleCode);
    }
}
