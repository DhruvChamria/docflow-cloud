package edu.manipal.ccd.api;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;

class RequestRateLimitFilterTest {
    @Test void limitsMutationsButNotReads() throws Exception {
        var filter=new RequestRateLimitFilter(2,1);
        var calls=new AtomicInteger();
        var chain=(jakarta.servlet.FilterChain)(request,response)->calls.incrementAndGet();

        assertEquals(200,run(filter,"POST","/api/v1/jobs",chain).getStatus());
        assertEquals(200,run(filter,"POST","/api/v1/jobs/123/cancel",chain).getStatus());
        var blocked=run(filter,"DELETE","/api/v1/jobs/123",chain);
        assertEquals(429,blocked.getStatus());
        assertEquals("60",blocked.getHeader("Retry-After"));
        assertEquals(200,run(filter,"GET","/api/v1/jobs",chain).getStatus());
        assertEquals(3,calls.get());
    }

    @Test void usesASeparateAuthenticationBudget() throws Exception {
        var filter=new RequestRateLimitFilter(10,1);
        var chain=(jakarta.servlet.FilterChain)(request,response)->{};
        assertEquals(200,run(filter,"POST","/api/v1/auth/login",chain).getStatus());
        assertEquals(429,run(filter,"POST","/api/v1/auth/register",chain).getStatus());
    }

    private static MockHttpServletResponse run(RequestRateLimitFilter filter,String method,String path,
                                                jakarta.servlet.FilterChain chain) throws Exception {
        var request=new MockHttpServletRequest(method,path);request.setRemoteAddr("192.0.2.1");
        var response=new MockHttpServletResponse();filter.doFilter(request,response,chain);return response;
    }
}
