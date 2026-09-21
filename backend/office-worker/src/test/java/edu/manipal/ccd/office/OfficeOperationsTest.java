package edu.manipal.ccd.office;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OfficeOperationsTest {
    @Test void buildsShellFreeHeadlessCommandWithIsolatedProfile() {
        var command=OfficeOperations.command(Path.of("source.docx"),Path.of("out"),Path.of("profile"));
        assertTrue(command.contains("--headless"));assertTrue(command.contains("--nolockcheck"));assertTrue(command.contains("--convert-to"));
        assertTrue(command.stream().anyMatch(value->value.startsWith("-env:UserInstallation=file:")));
        assertEquals("source.docx",Path.of(command.get(command.size()-1)).getFileName().toString());
        assertFalse(command.contains("sh"));assertFalse(command.contains("cmd"));
    }
}
