package edu.manipal.ccd.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class GuestIdentityTest {
    @Test void acceptsConfiguredCloudOriginAndCreatesSecureCookie() {
        var environment=new MockEnvironment()
            .withProperty("APP_ALLOWED_ORIGIN","https://docflow.example")
            .withProperty("APP_COOKIE_SECURE","true");
        var identity=new GuestIdentity(environment);
        var request=new MockHttpServletRequest();request.setMethod("POST");
        request.addHeader("X-Requested-With","docflow");request.addHeader("Origin","https://docflow.example");
        var response=new MockHttpServletResponse();
        assertDoesNotThrow(()->identity.checkMutation(request));
        assertNotNull(identity.owner(request,response,true));
        String cookie=response.getHeader("Set-Cookie");
        assertNotNull(cookie);assertTrue(cookie.contains("Secure"));assertTrue(cookie.contains("SameSite=Strict"));
    }

    @Test void rejectsUnconfiguredOrigin() {
        var identity=new GuestIdentity(new MockEnvironment().withProperty("APP_ALLOWED_ORIGIN","https://docflow.example"));
        var request=new MockHttpServletRequest();request.setMethod("POST");
        request.addHeader("X-Requested-With","docflow");request.addHeader("Origin","https://attacker.example");
        assertThrows(ResponseStatusException.class,()->identity.checkMutation(request));
    }

    @Test void acceptsCurrentHttpsOriginBehindCloudProxy() {
        var identity=new GuestIdentity(new MockEnvironment());
        var request=new MockHttpServletRequest();request.setMethod("POST");
        request.setScheme("https");request.setServerName("docflow.example");request.setServerPort(443);
        request.addHeader("X-Requested-With","docflow");request.addHeader("Origin","https://docflow.example");

        assertDoesNotThrow(()->identity.checkMutation(request));
    }
}
