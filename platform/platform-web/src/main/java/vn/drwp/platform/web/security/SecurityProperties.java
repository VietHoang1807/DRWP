package vn.drwp.platform.web.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Business API tự xác thực JWT (tài liệu 02 mục 4, ADR 013) — kể cả khi đã có gateway phía trước.
 */
@ConfigurationProperties("drwp.security")
public record SecurityProperties(
        /** Issuer Keycloak, ví dụ http://localhost:8180/realms/drwp */
        String issuerUri,
        /** Tùy chọn: JWK set URI; mặc định suy ra từ issuer. */
        String jwkSetUri,
        /** Audience bắt buộc có trong {@code aud}. */
        @DefaultValue("drwp-api") String audience,
        /** Header client dùng để CHỌN tenant (vẫn phải có membership, không phải nguồn tin cậy). */
        @DefaultValue("X-Tenant-Id") String tenantHeader,
        Dev dev) {

    public static final String PLATFORM_ADMIN_ROLE = "platform-admin";

    /** Chế độ token HS256 cho phát triển cục bộ. KHÔNG bật ở production (bị chặn khi profile prod). */
    public record Dev(@DefaultValue("false") boolean enabled, String secret, @DefaultValue("drwp-dev") String issuer) {
    }

    public boolean devEnabled() {
        return dev != null && dev.enabled();
    }
}
