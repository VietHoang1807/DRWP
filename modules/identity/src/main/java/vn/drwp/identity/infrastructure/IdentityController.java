package vn.drwp.identity.infrastructure;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import vn.drwp.identity.api.PermissionChecker;
import vn.drwp.identity.api.RoleDirectory;
import vn.drwp.identity.application.RoleAdminService;
import vn.drwp.identity.application.RoleAdminService.MemberView;
import vn.drwp.identity.application.RoleAdminService.RoleView;
import vn.drwp.platform.kernel.security.Action;
import vn.drwp.platform.runtime.ConditionalOnRuntimeRole;
import vn.drwp.platform.runtime.RuntimeRole;
import vn.drwp.platform.tenancy.TenantContext;
import vn.drwp.platform.web.security.Actor;
import vn.drwp.platform.web.security.CurrentActor;

@RestController
@RequestMapping("/api/identity")
@ConditionalOnRuntimeRole(RuntimeRole.API)
class IdentityController {

    private final RoleAdminService roles;
    private final PermissionChecker permissions;
    private final RoleDirectory directory;

    IdentityController(RoleAdminService roles, PermissionChecker permissions, RoleDirectory directory) {
        this.roles = roles;
        this.permissions = permissions;
        this.directory = directory;
    }

    record RoleRequest(@NotBlank String code, @NotBlank String name, String description) {
    }

    record RoleUpdate(@NotBlank String name, String description) {
    }

    record GrantRequest(@NotBlank String resource, Set<Action> allow, Set<Action> deny) {
    }

    record MemberRequest(@NotBlank String subject, String displayName, List<String> roles) {
    }

    record RolesRequest(@NotNull List<String> roles) {
    }

    record PermissionView(String resource, long mask, Set<Action> actions) {
    }

    @GetMapping("/actions")
    List<Map<String, Object>> actions() {
        return Arrays.stream(Action.values()).map(a -> Map.<String, Object>of("action", a.name(), "bit", a.bit(), "mask", a.mask())).toList();
    }

    @GetMapping("/me")
    Map<String, Object> me(@RequestParam(required = false) String resource) {
        Actor actor = CurrentActor.require();
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("subject", actor.subject());
        body.put("username", actor.username());
        body.put("tenant", TenantContext.require().value());
        body.put("roles", directory.rolesOf(actor.subject()));
        if (resource != null) {
            long mask = permissions.effectiveMask(actor.subject(), resource);
            body.put("permission", new PermissionView(resource, mask, Action.fromMask(mask)));
        }
        return body;
    }

    @GetMapping("/roles")
    List<RoleView> roles() {
        return roles.roles(CurrentActor.require());
    }

    @GetMapping("/roles/{code}")
    RoleView role(@PathVariable String code) {
        return roles.role(CurrentActor.require(), code);
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    RoleView create(@Valid @RequestBody RoleRequest r) {
        return roles.createRole(CurrentActor.require(), r.code(), r.name(), r.description());
    }

    @PutMapping("/roles/{code}")
    RoleView update(@PathVariable String code, @Valid @RequestBody RoleUpdate r) {
        return roles.updateRole(CurrentActor.require(), code, r.name(), r.description());
    }

    @DeleteMapping("/roles/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String code) {
        roles.deleteRole(CurrentActor.require(), code);
    }

    @PutMapping("/roles/{code}/permissions")
    RoleView permissions(@PathVariable String code, @RequestBody List<@Valid GrantRequest> grants) {
        return roles.setPermissions(CurrentActor.require(), code,
                grants.stream().map(g -> RoleAdminService.grant(g.resource(), g.allow(), g.deny())).toList());
    }

    @GetMapping("/members")
    List<MemberView> members() {
        return roles.members(CurrentActor.require());
    }

    @PostMapping("/members")
    @ResponseStatus(HttpStatus.CREATED)
    MemberView addMember(@Valid @RequestBody MemberRequest r) {
        return roles.addMember(CurrentActor.require(), r.subject(), r.displayName(), r.roles());
    }

    @DeleteMapping("/members/{subject}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeMember(@PathVariable String subject) {
        roles.removeMember(CurrentActor.require(), subject);
    }

    @PutMapping("/members/{subject}/roles")
    MemberView replaceRoles(@PathVariable String subject, @Valid @RequestBody RolesRequest r) {
        return roles.replaceRoles(CurrentActor.require(), subject, r.roles());
    }
}
