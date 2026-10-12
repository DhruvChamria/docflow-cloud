package edu.manipal.ccd.api;

import edu.manipal.ccd.local.AccountStore;
import jakarta.servlet.http.*;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile({"local","cloud"})
@RequestMapping("/api/v1/auth")
class AuthController {
    private static final Pattern EMAIL=Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,63}$",Pattern.CASE_INSENSITIVE);
    private final AccountStore accounts;private final PasswordHasher passwords;private final Identity identity;private final String dummyHash;
    AuthController(AccountStore accounts,PasswordHasher passwords,Identity identity){
        this.accounts=accounts;this.passwords=passwords;this.identity=identity;this.dummyHash=passwords.hash("invalid-password-value");
    }
    record Credentials(String email,String password) {}
    record AuthView(boolean authenticated,String email) {}
    @PostMapping("/register")
    ResponseEntity<AuthView> register(@RequestBody Credentials credentials,HttpServletRequest request,HttpServletResponse response){
        identity.checkMutation(request);String email=validEmail(credentials.email());validPassword(credentials.password());
        var account=accounts.register(email,passwords.hash(credentials.password()))
            .orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"An account with this email already exists"));
        identity.login(account,response);return ResponseEntity.status(HttpStatus.CREATED).body(new AuthView(true,account.email()));
    }
    @PostMapping("/login")
    AuthView login(@RequestBody Credentials credentials,HttpServletRequest request,HttpServletResponse response){
        identity.checkMutation(request);String email=credentials.email()==null?"":credentials.email().trim().toLowerCase(Locale.ROOT);
        var stored=accounts.byEmail(email);String hash=stored.map(AccountStore.StoredAccount::passwordHash).orElse(dummyHash);
        boolean valid=credentials.password()!=null&&credentials.password().length()<=128&&passwords.verify(credentials.password(),hash);
        if(stored.isEmpty()||!valid)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid email or password");
        var account=new AccountStore.Account(stored.get().id(),stored.get().email());identity.login(account,response);return new AuthView(true,account.email());
    }
    @PostMapping("/logout")
    AuthView logout(HttpServletRequest request,HttpServletResponse response){identity.checkMutation(request);identity.logout(request,response);return new AuthView(false,null);}
    @GetMapping("/me")
    AuthView me(HttpServletRequest request){return identity.account(request).map(account->new AuthView(true,account.email())).orElse(new AuthView(false,null));}
    private String validEmail(String value){
        String email=value==null?"":value.trim().toLowerCase(Locale.ROOT);
        if(email.length()>254||!EMAIL.matcher(email).matches())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Enter a valid email address");
        return email;
    }
    private void validPassword(String value){
        if(value==null||value.length()<10||value.length()>128||!value.matches(".*[A-Za-z].*")||!value.matches(".*[0-9].*"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Password must be 10 to 128 characters and include a letter and number");
    }
}
