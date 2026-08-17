package com.webhook.delivery.context;

import com.webhook.delivery.domain.Tenant;
import com.webhook.delivery.exception.TenantMissingException;
import com.webhook.delivery.repository.TenantRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TenantInterceptor implements HandlerInterceptor {

    public static final String TENANT_HEADER = "X-Tenant-Id";
    private final TenantRepository tenantRepository;

    public TenantInterceptor(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();

        // Only enforce for /api/v1/ routes
        if (!uri.startsWith("/api/v1")) {
            return true;
        }

        String tenantId = request.getHeader(TENANT_HEADER);
        if (tenantId == null || tenantId.trim().isEmpty()) {
            throw new TenantMissingException("Header '" + TENANT_HEADER + "' is required for API requests");
        }

        String trimmedTenantId = tenantId.trim();
        TenantContext.setTenantId(trimmedTenantId);

        // Ensure tenant record exists in DB
        if (!tenantRepository.existsById(trimmedTenantId)) {
            tenantRepository.save(new Tenant(trimmedTenantId, "Tenant " + trimmedTenantId));
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        TenantContext.clear();
    }
}
