package vn.drwp.identity.application;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.drwp.identity.domain.EffectivePermission;
import vn.drwp.identity.domain.Grant;
import vn.drwp.identity.domain.RoleCode;
import vn.drwp.identity.infrastructure.IdentityRepository;
import vn.drwp.identity.infrastructure.IdentityRepository.RoleRow;
import vn.drwp.platform.kernel.error.ConflictException;
import vn.drwp.platform.kernel.error.ForbiddenException;
import vn.drwp.platform.kernel.error.NotFoundException;
import vn.drwp.platform.kernel.event.BusinessAuditEvent;
import vn.drwp.platform.kernel.security.Action;
import vn.drwp.platform.kernel.security.PermissionMask;
import vn.drwp.platform.support.CorrelationId;
import vn.drwp.platform.tenancy.TenantContext;
import vn.drwp.platform.web.security.Actor;
import vn.drwp.tenant.api.TenantMembershipReader;
import vn.drwp.tenant.api.TenantMembershipWriter;

/** Quản trị vai trò, quyền và thành viên trong tenant hiện tại. Yêu cầu MANAGE trên resource {@code identity}. */
@Service
public class RoleAdminService {

    public static final String RESOURCE = "identity";

    public record RoleView(String code, String name, String description, boolean system, List<GrantView> permissions) {
    }

    public record GrantView(String resource, Set<Action> allow, Set<Action> deny, long allowMask, long denyMask) {
        static GrantView of(Grant g) {
            return new GrantView(g.resource(), Action.fromMask(g.allowMask()), Action.fromMask(g.denyMask()),
                    g.allowMask(), g.denyMask());
        }
    }

    public record MemberView(String subject, String displayName, String status, Set<String> roles) {
    }

    private final IdentityRepository repository;
    private final AuthorizationService authorization;
    private final TenantMembershipReader membershipReader;
    private final TenantMembershipWriter membershipWriter;
    private final ApplicationEventPublisher events;

    RoleAdminService(IdentityRepository repository, AuthorizationService authorization,
                     TenantMembershipReader membershipReader, TenantMembershipWriter membershipWriter,
                     ApplicationEventPublisher events) {
        this.repository = repository;
        this.authorization = authorization;
        this.membershipReader = membershipReader;
        this.membershipWriter = membershipWriter;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<RoleView> roles(Actor actor) {
        authorization.require(actor.subject(), RESOURCE, Action.VIEW);
        return repository.roles().stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public RoleView role(Actor actor, String code) {
        authorization.require(actor.subject(), RESOURCE, Action.VIEW);
        return view(load(code));
    }

    @Transactional
    public RoleView createRole(Actor actor, String code, String name, String description) {
        authorization.require(actor.subject(), RESOURCE, Action.MANAGE);
        RoleCode.requireValid(code);
        if (repository.findRole(code).isPresent()) {
            throw new ConflictException("ROLE_EXISTS", "Role already exists: " + code);
        }
        repository.insertRoleIfAbsent(code, name, description, false);
        audit(actor, "ROLE_CREATED", code, Map.of());
        return view(load(code));
    }

    @Transactional
    public RoleView updateRole(Actor actor, String code, String name, String description) {
        authorization.require(actor.subject(), RESOURCE, Action.MANAGE);
        load(code);
        repository.updateRole(code, name, description);
        audit(actor, "ROLE_UPDATED", code, Map.of());
        return view(load(code));
    }

    @Transactional
    public void deleteRole(Actor actor, String code) {
        authorization.require(actor.subject(), RESOURCE, Action.MANAGE);
        RoleRow role = load(code);
        if (role.system()) {
            throw new ConflictException("SYSTEM_ROLE", "System role cannot be deleted");
        }
        repository.deleteRole(code);
        repository.bumpPolicyVersion();
        audit(actor, "ROLE_DELETED", code, Map.of());
    }

    @Transactional
    public RoleView setPermissions(Actor actor, String code, List<Grant> grants) {
        authorization.require(actor.subject(), RESOURCE, Action.MANAGE);
        RoleRow role = load(code);
        if (role.system()) {
            throw new ConflictException("SYSTEM_ROLE", "Permissions of a system role are managed by the platform");
        }
        for (Grant g : grants) {
            long mine = authorization.effectiveMask(actor.subject(), g.resource());
            if (!EffectivePermission.canDelegate(mine, g)) {
                throw new ForbiddenException("Cannot grant permissions you do not hold on " + g.resource());
            }
        }
        repository.replaceGrants(role.id(), grants);
        repository.bumpPolicyVersion();
        audit(actor, "ROLE_PERMISSIONS_CHANGED", code, Map.of("grants", String.valueOf(grants.size())));
        return view(load(code));
    }

    @Transactional(readOnly = true)
    public List<MemberView> members(Actor actor) {
        authorization.require(actor.subject(), RESOURCE, Action.VIEW);
        return membershipReader.members(TenantContext.require()).stream()
                .map(m -> new MemberView(m.subject(), m.displayName(), m.status(), repository.roleCodesOf(m.subject())))
                .toList();
    }

    @Transactional
    public MemberView addMember(Actor actor, String subject, String displayName, List<String> roles) {
        authorization.require(actor.subject(), RESOURCE, Action.MANAGE);
        membershipWriter.addMember(TenantContext.require(), subject, displayName, actor.subject());
        replaceRolesInternal(actor, subject, roles == null ? List.of() : roles);
        audit(actor, "MEMBER_ADDED", subject, Map.of());
        return new MemberView(subject, displayName, "ACTIVE", repository.roleCodesOf(subject));
    }

    @Transactional
    public void removeMember(Actor actor, String subject) {
        authorization.require(actor.subject(), RESOURCE, Action.MANAGE);
        if (actor.subject().equals(subject)) {
            throw new ConflictException("SELF_REMOVAL", "You cannot remove yourself");
        }
        membershipWriter.suspendMember(TenantContext.require(), subject, actor.subject());
        repository.unassignAll(subject);
        repository.bumpPolicyVersion();
        audit(actor, "MEMBER_SUSPENDED", subject, Map.of());
    }

    @Transactional
    public MemberView replaceRoles(Actor actor, String subject, List<String> roles) {
        authorization.require(actor.subject(), RESOURCE, Action.MANAGE);
        if (!membershipReader.isActiveMember(TenantContext.require(), subject)) {
            throw new NotFoundException("Member", subject);
        }
        replaceRolesInternal(actor, subject, roles);
        audit(actor, "MEMBER_ROLES_CHANGED", subject, Map.of("roles", String.join(",", roles)));
        return new MemberView(subject, null, "ACTIVE", repository.roleCodesOf(subject));
    }

    private void replaceRolesInternal(Actor actor, String subject, List<String> roleCodes) {
        Set<String> target = new LinkedHashSet<>(roleCodes);
        List<RoleRow> resolved = new ArrayList<>();
        for (String code : target) {
            RoleRow role = load(code);
            // chống leo thang: chỉ gán vai trò mà mọi quyền của nó người gán đang nắm giữ
            for (Grant g : repository.grantsOfRole(role.id())) {
                if (!EffectivePermission.canDelegate(authorization.effectiveMask(actor.subject(), g.resource()), g)) {
                    throw new ForbiddenException("Cannot assign role " + code + " with permissions you do not hold");
                }
            }
            resolved.add(role);
        }
        if (actor.subject().equals(subject) && !target.contains(RoleCode.TENANT_ADMIN)
                && repository.roleCodesOf(subject).contains(RoleCode.TENANT_ADMIN)) {
            throw new ConflictException("SELF_DEMOTION", "You cannot remove your own TENANT_ADMIN role");
        }
        repository.unassignAll(subject);
        for (RoleRow role : resolved) {
            repository.assign(subject, role.id(), actor.subject());
        }
        repository.bumpPolicyVersion();
    }

    private RoleRow load(String code) {
        return repository.findRole(code).orElseThrow(() -> new NotFoundException("Role", code));
    }

    private RoleView view(RoleRow r) {
        return new RoleView(r.code(), r.name(), r.description(), r.system(),
                repository.grantsOfRole(r.id()).stream().map(GrantView::of).toList());
    }

    private void audit(Actor actor, String action, String resourceId, Map<String, String> detail) {
        events.publishEvent(new BusinessAuditEvent(TenantContext.require().value(), actor.subject(), action,
                "identity", resourceId, BusinessAuditEvent.Outcome.SUCCESS, detail, CorrelationId.current(), null));
    }

    public static Grant grant(String resource, Set<Action> allow, Set<Action> deny) {
        return new Grant(resource, PermissionMask.of(allow == null ? Set.of() : allow),
                PermissionMask.of(deny == null ? Set.of() : deny));
    }

}
