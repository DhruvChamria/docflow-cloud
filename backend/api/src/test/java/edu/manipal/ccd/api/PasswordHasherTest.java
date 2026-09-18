package edu.manipal.ccd.api;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PasswordHasherTest {
    @Test void hashesWithRandomSaltAndVerifiesInConstantTimePrimitive() {
        var hasher=new PasswordHasher();String first=hasher.hash("ExamplePassword123"),second=hasher.hash("ExamplePassword123");
        assertNotEquals(first,second);assertTrue(hasher.verify("ExamplePassword123",first));assertFalse(hasher.verify("WrongPassword123",first));
        assertFalse(hasher.verify("ExamplePassword123","malformed"));
    }
}
