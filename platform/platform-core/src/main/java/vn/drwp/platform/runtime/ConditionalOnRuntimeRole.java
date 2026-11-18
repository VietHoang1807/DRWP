package vn.drwp.platform.runtime;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Conditional;

/** Bean chỉ được đăng ký khi vai trò runtime tương ứng bật trong {@code drwp.runtime.roles}. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Conditional(RuntimeRoleCondition.class)
public @interface ConditionalOnRuntimeRole {
    RuntimeRole value();
}
