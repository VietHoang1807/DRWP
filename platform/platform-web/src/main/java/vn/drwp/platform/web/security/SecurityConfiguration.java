package vn.drwp.platform.web.security;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import jakarta.servlet.DispatcherType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import vn.drwp.platform.tenancy.TenantMembershipVerifier;
import vn.drwp.platform.web.CorrelationIdFilter;
import vn.drwp.platform.web.TenantResolutionFilter;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecurityProperties.class)
@EnableMethodSecurity
public class SecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfiguration.class);

    @Bean
    SecurityFilterChain apiSecurity(HttpSecurity http, SecurityProperties props,
                                    org.springframework.beans.factory.ObjectProvider<TenantMembershipVerifier> membership) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API stateless dùng bearer token, không cookie
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll(); // trang lỗi giữ nguyên mã 4xx/5xx gốc
                    auth.requestMatchers("/actuator/health/**", "/actuator/info").permitAll();
                    if (props.devEnabled()) {
                        auth.requestMatchers(HttpMethod.POST, "/dev/token").permitAll();
                    }
                    auth.requestMatchers("/api/platform/**").hasRole("PLATFORM_ADMIN");
                    auth.requestMatchers("/api/**").authenticated();
                    auth.requestMatchers("/actuator/**").hasRole("PLATFORM_ADMIN");
                    auth.anyRequest().denyAll();
                })
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .addFilterBefore(new CorrelationIdFilter(), BearerTokenAuthenticationFilter.class)
                .addFilterAfter(new TenantResolutionFilter(membership, props.tenantHeader()), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(SecurityProperties props, Environment env) {
        if (props.devEnabled()) {
            if (Arrays.asList(env.getActiveProfiles()).contains("prod")) {
                throw new IllegalStateException("drwp.security.dev.enabled must not be used with profile 'prod'");
            }
            log.warn("DEV JWT mode enabled (HS256, issuer {}). Never use outside local development.", props.dev().issuer());
            NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(devKey(props)).macAlgorithm(MacAlgorithm.HS256).build();
            decoder.setJwtValidator(validator(props.dev().issuer(), props.audience()));
            return decoder;
        }
        if (props.issuerUri() == null || props.issuerUri().isBlank()) {
            throw new IllegalStateException("drwp.security.issuer-uri is required when dev mode is disabled");
        }
        NimbusJwtDecoder decoder = props.jwkSetUri() != null && !props.jwkSetUri().isBlank()
                ? NimbusJwtDecoder.withJwkSetUri(props.jwkSetUri()).build()
                : NimbusJwtDecoder.withIssuerLocation(props.issuerUri()).build();
        decoder.setJwtValidator(validator(props.issuerUri(), props.audience()));
        return decoder;
    }

    static SecretKey devKey(SecurityProperties props) {
        String secret = props.dev().secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("drwp.security.dev.secret must be at least 32 bytes");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static OAuth2TokenValidator<Jwt> validator(String issuer, String audience) {
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>("aud",
                aud -> aud != null && aud.contains(audience));
        return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audienceValidator);
    }

    /** Quyền trong token chỉ dùng cho vai trò platform; quyền nghiệp vụ tra từ DB theo tenant (tài liệu 01 mục 7). */
    static Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            if (CurrentActor.realmRoles(jwt).contains(SecurityProperties.PLATFORM_ADMIN_ROLE)) {
                authorities.add(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"));
            }
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
        };
    }
}
