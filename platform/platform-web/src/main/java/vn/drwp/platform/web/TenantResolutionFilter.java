package vn.drwp.platform.web;

import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import vn.drwp.platform.kernel.tenant.TenantId;
import vn.drwp.platform.tenancy.TenantContext;
import vn.drwp.platform.tenancy.TenantMembershipVerifier;

/**
 * Xác định tenant cho request (tài liệu 01 mục 4, tài liệu 02 mục 1.3):
 * <ul>
 *   <li>Tenant được CHỌN bởi claim {@code tenant_id} hoặc header {@code X-Tenant-Id};</li>
 *   <li>chỉ được chấp nhận khi principal đã xác thực có membership ACTIVE trong tenant ACTIVE;</li>
 *   <li>thiếu/không hợp lệ: từ chối (fail-closed); tenant context được dọn khi request kết thúc.</li>
 * </ul>
 * Bỏ qua: {@code /api/platform/**} (quản trị nền tảng) và {@code /api/me/**} (danh sách tenant của tôi).
 */
public class TenantResolutionFilter extends OncePerRequestFilter {

    private static final Logger security = LoggerFactory.getLogger("SECURITY");

    private final ObjectProvider<TenantMembershipVerifier> membership;
    private final String tenantHeader;

    public TenantResolutionFilter(ObjectProvider<TenantMembershipVerifier> membership, String tenantHeader) {
        this.membership = membership;
        this.tenantHeader = tenantHeader;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !path.startsWith("/api/") || path.startsWith("/api/platform/") || path.startsWith("/api/me/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            chain.doFilter(request, response); // Spring Security sẽ trả 401
            return;
        }
        String claimTenant = jwtAuth.getToken().getClaimAsString("tenant_id");
        String headerTenant = request.getHeader(tenantHeader);
        if (claimTenant != null && headerTenant != null && !claimTenant.equals(headerTenant)) {
            reject(response, HttpStatus.BAD_REQUEST, "TENANT_CONFLICT", "Tenant header conflicts with token");
            return;
        }
        String selected = Optional.ofNullable(claimTenant).orElse(headerTenant);
        if (selected == null || selected.isBlank()) {
            reject(response, HttpStatus.BAD_REQUEST, "TENANT_REQUIRED", "Select a tenant with header " + tenantHeader);
            return;
        }
        TenantId tenant;
        try {
            tenant = TenantId.of(selected.trim());
        } catch (IllegalArgumentException e) {
            reject(response, HttpStatus.BAD_REQUEST, "TENANT_INVALID", "Invalid tenant id");
            return;
        }
        String subject = jwtAuth.getToken().getSubject();
        if (!membership.getObject().isActiveMember(tenant, subject)) {
            security.warn("Tenant access denied subject={} tenant={} path={}", subject, tenant, request.getRequestURI());
            reject(response, HttpStatus.FORBIDDEN, "TENANT_ACCESS_DENIED", "No active membership in the selected tenant");
            return;
        }
        try {
            TenantContext.callAs(tenant, () -> {
                try {
                    chain.doFilter(request, response);
                } catch (IOException | ServletException e) {
                    throw new FilterChainException(e);
                }
                return null;
            });
        } catch (FilterChainException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            throw (ServletException) e.getCause();
        }
    }

    private static void reject(HttpServletResponse response, HttpStatus status, String code, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase()
                + "\",\"status\":" + status.value() + ",\"code\":\"" + code + "\",\"detail\":\"" + detail + "\"}");
    }

    private static final class FilterChainException extends RuntimeException {
        FilterChainException(Exception cause) {
            super(cause);
        }
    }
}
