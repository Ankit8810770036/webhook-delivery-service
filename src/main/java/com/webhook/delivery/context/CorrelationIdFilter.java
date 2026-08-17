package com.webhook.delivery.context;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements Filter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";
    public static final String TENANT_ID_MDC_KEY = "tenantId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
            String correlationId = httpRequest.getHeader(CORRELATION_ID_HEADER);
            if (correlationId == null || correlationId.isBlank()) {
                correlationId = UUID.randomUUID().toString();
            }

            MDC.put(TRACE_ID_MDC_KEY, correlationId);
            httpResponse.setHeader(CORRELATION_ID_HEADER, correlationId);

            String tenantId = httpRequest.getHeader("X-Tenant-Id");
            if (tenantId != null && !tenantId.isBlank()) {
                MDC.put(TENANT_ID_MDC_KEY, tenantId);
            }

            try {
                chain.doFilter(request, response);
            } finally {
                MDC.remove(TRACE_ID_MDC_KEY);
                MDC.remove(TENANT_ID_MDC_KEY);
            }
        } else {
            chain.doFilter(request, response);
        }
    }
}
