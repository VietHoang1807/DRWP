package vn.drwp.platform.web.security;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * CHỈ cho phát triển cục bộ ({@code drwp.security.dev.enabled=true}): phát token HS256 có cùng dạng claim
 * với Keycloak để thử nhanh luồng mà không cần dựng Keycloak.
 */
@RestController
@ConditionalOnProperty(prefix = "drwp.security.dev", name = "enabled", havingValue = "true")
class DevTokenController {

    private final NimbusJwtEncoder encoder;
    private final SecurityProperties props;

    DevTokenController(SecurityProperties props) {
        this.props = props;
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(SecurityConfiguration.devKey(props)));
    }

    record DevTokenRequest(@NotBlank @Pattern(regexp = "^[A-Za-z0-9._@-]{1,64}$") String subject,
                           String username, boolean platformAdmin, String tenant) {
    }

    @PostMapping("/dev/token")
    Map<String, Object> token(@Valid @RequestBody DevTokenRequest request) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(props.dev().issuer())
                .subject(request.subject())
                .audience(List.of(props.audience()))
                .issuedAt(now)
                .expiresAt(now.plus(Duration.ofHours(8)))
                .claim("preferred_username", request.username() != null ? request.username() : request.subject())
                .claim("realm_access", Map.of("roles", request.platformAdmin()
                        ? List.of(SecurityProperties.PLATFORM_ADMIN_ROLE) : List.of()));
        if (request.tenant() != null && !request.tenant().isBlank()) {
            claims.claim("tenant_id", request.tenant());
        }
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
        return Map.of("access_token", token, "token_type", "Bearer", "expires_in", 8 * 3600);
    }
}
