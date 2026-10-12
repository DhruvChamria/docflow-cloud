package edu.manipal.ccd.api;
import jakarta.servlet.http.*;
import java.security.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
@Component
class GuestIdentity {
    private static final SecureRandom RANDOM=new SecureRandom();
    private final Set<String> allowedOrigins;private final boolean secureCookie;private final String sameSite;
    GuestIdentity(Environment environment) {
        allowedOrigins=Arrays.stream(environment.getProperty("APP_ALLOWED_ORIGIN",
                "http://127.0.0.1:5173,http://localhost:5173,http://127.0.0.1:8080,http://localhost:8080").split(","))
            .map(String::trim).filter(value->!value.isEmpty()).collect(java.util.stream.Collectors.toUnmodifiableSet());
        secureCookie=Boolean.parseBoolean(environment.getProperty("APP_COOKIE_SECURE","false"));
        sameSite=environment.getProperty("APP_COOKIE_SAME_SITE","Strict");
        if(!Set.of("Strict","Lax","None").contains(sameSite))throw new IllegalArgumentException("Invalid cookie SameSite policy");
        if(sameSite.equals("None")&&!secureCookie)throw new IllegalArgumentException("SameSite=None requires secure cookies");
    }
    void checkMutation(HttpServletRequest request) {
        if(!"docflow".equals(request.getHeader("X-Requested-With")) ||
            "cross-site".equals(request.getHeader("Sec-Fetch-Site")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Same-origin request required");
        String origin=request.getHeader("Origin");
        if(origin!=null && !allowedOrigins.contains(origin) && !origin.equals(requestOrigin(request)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Origin not allowed");
    }
    String owner(HttpServletRequest request,HttpServletResponse response,boolean create) {
        String token=token(request);
        if(token==null) {
            if(!create) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Job not found");
            byte[] bytes=new byte[32]; RANDOM.nextBytes(bytes); token=HexFormat.of().formatHex(bytes);
            response.addHeader("Set-Cookie",ResponseCookie.from("docflow_guest",token).httpOnly(true)
                .secure(secureCookie).sameSite(sameSite).path("/api").maxAge(86400).build().toString());
        }
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    Optional<String> ownerIfPresent(HttpServletRequest request) {
        String token=token(request);if(token==null)return Optional.empty();
        try{return Optional.of(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8))));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    private String token(HttpServletRequest request) {
        if(request.getCookies()!=null) for(var cookie:request.getCookies())
            if(cookie.getName().equals("docflow_guest")&&cookie.getValue().matches("[a-f0-9]{64}"))return cookie.getValue();
        return null;
    }
    private static String requestOrigin(HttpServletRequest request) {
        String scheme=request.getScheme();int port=request.getServerPort();
        boolean defaultPort=(scheme.equals("http")&&port==80)||(scheme.equals("https")&&port==443);
        return scheme+"://"+request.getServerName()+(defaultPort?"":":"+port);
    }
}
