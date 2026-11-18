package vn.drwp.platform.web.security;

import java.util.Objects;
import java.util.Set;

/** Người/thực thể thực hiện hành động. {@code subject} là {@code sub} của JWT (ổn định, không phải username). */
public record Actor(String subject, String username, Set<String> realmRoles, boolean system) {

    public Actor {
        Objects.requireNonNull(subject, "subject");
        realmRoles = realmRoles == null ? Set.of() : Set.copyOf(realmRoles);
    }

    public static Actor system(String purpose) {
        return new Actor("system:" + purpose, "system", Set.of(), true);
    }

    public boolean isPlatformAdmin() {
        return realmRoles.contains(SecurityProperties.PLATFORM_ADMIN_ROLE);
    }
}
