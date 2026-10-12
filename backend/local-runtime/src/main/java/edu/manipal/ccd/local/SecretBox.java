package edu.manipal.ccd.local;

import java.nio.file.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** AES-GCM envelope for short-lived document secrets. Ciphertext alone is stored in PostgreSQL. */
public final class SecretBox {
    private static final byte VERSION=1;private static final int IV_BYTES=12;
    private final SecretKeySpec key;private final SecureRandom random=new SecureRandom();
    public SecretBox(Path keyFile) {
        this(readKey(keyFile));
    }
    public SecretBox(String encodedKey) {
        try {
            byte[] decoded=java.util.Base64.getMimeDecoder().decode(encodedKey.trim());
            if(decoded.length!=32)throw new IllegalArgumentException("Document secret key must contain 32 bytes");
            key=new SecretKeySpec(decoded,"AES");
            Arrays.fill(decoded,(byte)0);
        } catch(Exception invalid) { throw new IllegalStateException("Cannot load document secret key",invalid); }
    }
    private static String readKey(Path keyFile) {
        try { return Files.readString(keyFile); }
        catch(Exception invalid) { throw new IllegalStateException("Cannot load document secret key",invalid); }
    }
    public byte[] encrypt(char[] value) {
        ByteBuffer encoded=java.nio.charset.StandardCharsets.UTF_8.encode(CharBuffer.wrap(value));
        byte[] plain=new byte[encoded.remaining()];encoded.get(plain);byte[] iv=new byte[IV_BYTES];random.nextBytes(iv);
        try {
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,iv));
            byte[] encrypted=cipher.doFinal(plain),result=new byte[1+iv.length+encrypted.length];result[0]=VERSION;
            System.arraycopy(iv,0,result,1,iv.length);System.arraycopy(encrypted,0,result,1+iv.length,encrypted.length);return result;
        } catch(Exception failure) { throw new IllegalStateException("Document secret encryption failed",failure); }
        finally { Arrays.fill(plain,(byte)0);Arrays.fill(value,'\0'); }
    }
    public char[] decrypt(byte[] envelope) {
        if(envelope==null||envelope.length<1+IV_BYTES+16||envelope[0]!=VERSION)throw new IllegalArgumentException("Invalid encrypted document secret");
        byte[] iv=Arrays.copyOfRange(envelope,1,1+IV_BYTES),encrypted=Arrays.copyOfRange(envelope,1+IV_BYTES,envelope.length),plain=null;
        try {
            var cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,iv));plain=cipher.doFinal(encrypted);
            CharBuffer decoded=java.nio.charset.StandardCharsets.UTF_8.decode(ByteBuffer.wrap(plain));
            char[] result=new char[decoded.remaining()];decoded.get(result);return result;
        } catch(Exception failure) { throw new IllegalStateException("Document secret decryption failed",failure); }
        finally { if(plain!=null)Arrays.fill(plain,(byte)0);Arrays.fill(encrypted,(byte)0); }
    }
}
