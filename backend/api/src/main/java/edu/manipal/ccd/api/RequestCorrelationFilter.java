package edu.manipal.ccd.api;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class RequestCorrelationFilter extends OncePerRequestFilter {
    static final String ATTRIBUTE="docflow.correlationId";
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String id = UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE,id);
        MDC.put("correlationId", id);
        response.setHeader("X-Correlation-Id", id);
        response.setHeader("Cache-Control", "no-store");
        try { chain.doFilter(request, response); }
        finally { MDC.remove("correlationId"); }
    }
}
