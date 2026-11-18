package vn.drwp.platform.web.security;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import vn.drwp.platform.kernel.error.ForbiddenException;

/** Đọc actor từ SecurityContext (JWT đã được xác thực). */
public final class CurrentActor {

    private CurrentActor() {
    }

    public static Optional<Actor> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            return Optional.of(fromJwt(token.getToken()));
        }
        return Optional.empty();
    }

    public static Actor require() {
        return current().orElseThrow(() -> new ForbiddenException("Authenticated user required"));
    }

    public static Actor fromJwt(Jwt jwt) {
        String username = jwt.getClaimAsString("preferred_username");
        return new Actor(jwt.getSubject(), username != null ? username : jwt.getSubject(), realmRoles(jwt), false);
    }

    static Set<String> realmRoles(Jwt jwt) {
        Object realmAccess = jwt.getClaims().get("realm_access");
        Set<String> roles = new HashSet<>();
        if (realmAccess instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> list) {
            list.forEach(r -> roles.add(String.valueOf(r)));
        }
        return roles;
    }
}
