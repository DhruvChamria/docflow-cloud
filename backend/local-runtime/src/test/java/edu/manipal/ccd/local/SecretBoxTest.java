package edu.manipal.ccd.local;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class SecretBoxTest {
    @TempDir Path directory;
    @Test void encryptsAuthenticatesAndClearsInput() throws Exception {
        byte[] key=new byte[32];new java.security.SecureRandom().nextBytes(key);Path file=directory.resolve("key");
        String encoded=Base64.getEncoder().encodeToString(key);Files.writeString(file,encoded);Arrays.fill(key,(byte)0);var box=new SecretBox(file);
        char[] input="TemporaryDocumentPassword9".toCharArray();byte[] encrypted=box.encrypt(input);
        assertFalse(new String(encrypted,java.nio.charset.StandardCharsets.ISO_8859_1).contains("TemporaryDocumentPassword9"));
        assertTrue(new String(input).chars().allMatch(value->value==0));
        char[] restored=box.decrypt(encrypted);assertEquals("TemporaryDocumentPassword9",new String(restored));Arrays.fill(restored,'\0');
        encrypted[encrypted.length-1]^=1;assertThrows(IllegalStateException.class,()->box.decrypt(encrypted));
        assertDoesNotThrow(()->new SecretBox(encoded));
    }
}
