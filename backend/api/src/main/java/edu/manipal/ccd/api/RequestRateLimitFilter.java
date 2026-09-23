package edu.manipal.ccd.api;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class RequestRateLimitFilter extends OncePerRequestFilter {
    private static final int MAX_CLIENTS = 10_000;
    private final int jobLimit;
    private final int authLimit;
    private final ConcurrentHashMap<String,Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong lastCleanupMinute = new AtomicLong(-1);

    RequestRateLimitFilter(
        @Value("${app.rate-limit.job-mutations-per-minute:120}") int jobLimit,
        @Value("${app.rate-limit.auth-attempts-per-minute:30}") int authLimit) {
        this.jobLimit = positive(jobLimit);
        this.authLimit = positive(authLimit);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
        throws ServletException,IOException {
        String category=category(request);
        if(category==null) { chain.doFilter(request,response); return; }
        long minute=Instant.now().getEpochSecond()/60;
        cleanup(minute);
        String key=category+":"+request.getRemoteAddr();
        int limit=category.equals("auth")?authLimit:jobLimit;
        AtomicBoolean admitted=new AtomicBoolean(true);
        windows.compute(key,(ignored,current)->{
            if(current==null&&windows.size()>=MAX_CLIENTS) { admitted.set(false); return null; }
            Window next=current==null||current.minute()!=minute?new Window(minute,new AtomicInteger()):current;
            if(next.count().incrementAndGet()>limit) admitted.set(false);
            return next;
        });
        if(!admitted.get()) {
            response.setStatus(429);
            response.setHeader("Retry-After","60");
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Request limit exceeded; retry shortly.\"}");
            return;
        }
        chain.doFilter(request,response);
    }

    private void cleanup(long minute) {
        long previous=lastCleanupMinute.get();
        if(previous==minute||!lastCleanupMinute.compareAndSet(previous,minute))return;
        windows.entrySet().removeIf(entry->entry.getValue().minute()<minute-1);
    }
    private static String category(HttpServletRequest request) {
        String method=request.getMethod();String path=request.getRequestURI();
        if(method.equals("POST")&&path.startsWith("/api/v1/auth/"))return "auth";
        if((method.equals("POST")||method.equals("DELETE"))&&path.startsWith("/api/v1/jobs"))return "jobs";
        return null;
    }
    private static int positive(int value) { if(value<1)throw new IllegalArgumentException("Rate limits must be positive");return value; }
    private record Window(long minute,AtomicInteger count) {}
}
