package edu.manipal.ccd.api;

import java.security.*;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.stereotype.Component;

@Component
class PasswordHasher {
    private static final int ITERATIONS=210_000,BYTES=32;
    private static final SecureRandom RANDOM=new SecureRandom();
    String hash(String password) {
        byte[] salt=new byte[16];RANDOM.nextBytes(salt);
        return "pbkdf2-sha256$"+ITERATIONS+"$"+Base64.getEncoder().encodeToString(salt)+"$"+
            Base64.getEncoder().encodeToString(derive(password.toCharArray(),salt,ITERATIONS));
    }
    boolean verify(String password,String encoded) {
        try {
            String[] parts=encoded.split("\\$");if(parts.length!=4||!parts[0].equals("pbkdf2-sha256")) return false;
            int iterations=Integer.parseInt(parts[1]);if(iterations<100_000||iterations>1_000_000) return false;
            byte[] salt=Base64.getDecoder().decode(parts[2]),expected=Base64.getDecoder().decode(parts[3]);
            return MessageDigest.isEqual(expected,derive(password.toCharArray(),salt,iterations));
        } catch(RuntimeException invalid) { return false; }
    }
    private byte[] derive(char[] password,byte[] salt,int iterations) {
        var spec=new PBEKeySpec(password,salt,iterations,BYTES*8);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        catch(GeneralSecurityException unavailable) { throw new IllegalStateException(unavailable); }
        finally { spec.clearPassword();Arrays.fill(password,'\0'); }
    }
}
