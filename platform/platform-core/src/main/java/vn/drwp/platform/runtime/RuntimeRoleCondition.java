package vn.drwp.platform.runtime;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Mặc định (không cấu hình) bật cả ba vai trò để chạy all-in-one khi phát triển. */
class RuntimeRoleCondition implements Condition {

    static final String PROPERTY = "drwp.runtime.roles";
    static final String DEFAULT = "api,event-worker,scheduled-worker";

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        Map<String, Object> attrs = metadata.getAnnotationAttributes(ConditionalOnRuntimeRole.class.getName());
        if (attrs == null) {
            return true;
        }
        RuntimeRole role = (RuntimeRole) attrs.get("value");
        return enabledRoles(context.getEnvironment().getProperty(PROPERTY, DEFAULT)).contains(role.key());
    }

    static Set<String> enabledRoles(String raw) {
        return Arrays.stream(raw.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
    }
}
