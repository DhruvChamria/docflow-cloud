package edu.manipal.ccd.worker;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GhostscriptOperationsTest {
    @Test void buildsFixedSafeCommandsForEveryPreset() {
        for(String preset:java.util.List.of("SCREEN","EBOOK","PRINTER")) {
            var command=GhostscriptOperations.command(Path.of("source.pdf"),Path.of("result.pdf"),preset);
            assertTrue(command.contains("-dSAFER"));assertTrue(command.contains("-dBATCH"));assertTrue(command.contains("-dNOPAUSE"));
            assertTrue(command.contains("-dPDFSETTINGS=/"+preset.toLowerCase(java.util.Locale.ROOT)));
            assertEquals("source.pdf",Path.of(command.get(command.size()-1)).getFileName().toString());
            assertTrue(command.stream().anyMatch(value->value.startsWith("-sOutputFile=")&&value.endsWith("result.pdf")));
        }
    }
}
