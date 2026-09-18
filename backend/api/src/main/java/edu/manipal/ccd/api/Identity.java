package edu.manipal.ccd.api;

import edu.manipal.ccd.local.AccountStore;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
class Identity {
    static final String SESSION="docflow_session";
    private static final SecureRandom RANDOM=new SecureRandom();
    private final GuestIdentity guests;private final AccountStore accounts;
    Identity(GuestIdentity guests,AccountStore accounts){this.guests=guests;this.accounts=accounts;}
    void checkMutation(HttpServletRequest request){guests.checkMutation(request);}
    Optional<AccountStore.Account> account(HttpServletRequest request){
        String token=cookie(request,SESSION);return token==null?Optional.empty():accounts.session(digest(token));
    }
    Optional<String> ownerIfPresent(HttpServletRequest request){
        var account=account(request);if(account.isPresent()) return Optional.of("account:"+account.get().id());
        return guests.ownerIfPresent(request);
    }
    String owner(HttpServletRequest request,HttpServletResponse response,boolean createGuest){
        return account(request).map(value->"account:"+value.id()).orElseGet(()->guests.owner(request,response,createGuest));
    }
    void login(AccountStore.Account account,HttpServletResponse response){
        byte[] bytes=new byte[32];RANDOM.nextBytes(bytes);String token=HexFormat.of().formatHex(bytes);Instant expires=Instant.now().plus(Duration.ofDays(7));
        accounts.createSession(account.id(),digest(token),expires);response.addHeader("Set-Cookie",ResponseCookie.from(SESSION,token).httpOnly(true)
            .secure(false).sameSite("Strict").path("/api").maxAge(Duration.ofDays(7)).build().toString());
    }
    void logout(HttpServletRequest request,HttpServletResponse response){
        String token=cookie(request,SESSION);if(token!=null) accounts.invalidate(digest(token));
        response.addHeader("Set-Cookie",ResponseCookie.from(SESSION,"").httpOnly(true).secure(false).sameSite("Strict").path("/api").maxAge(Duration.ZERO).build().toString());
    }
    private static String cookie(HttpServletRequest request,String name){
        if(request.getCookies()!=null) for(var cookie:request.getCookies()) if(cookie.getName().equals(name)&&cookie.getValue().matches("[a-f0-9]{64}")) return cookie.getValue();
        return null;
    }
    static String digest(String value){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
}
