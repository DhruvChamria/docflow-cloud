package edu.manipal.ccd.api;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class OfficePackageValidatorTest {
    @Test void acceptsSupportedPackageStructures() throws Exception {
        assertTrue(valid(Map.of("[Content_Types].xml", "types", "word/document.xml", "word"), OfficePackageValidator.Format.DOCX));
        assertTrue(valid(Map.of("[Content_Types].xml", "types", "ppt/presentation.xml", "slides"), OfficePackageValidator.Format.PPTX));
        assertTrue(valid(openDocument("application/vnd.oasis.opendocument.text"), OfficePackageValidator.Format.ODT));
        assertTrue(valid(openDocument("application/vnd.oasis.opendocument.presentation"), OfficePackageValidator.Format.ODP));
        assertTrue(valid(openDocument("application/vnd.oasis.opendocument.spreadsheet"), OfficePackageValidator.Format.ODS));
    }

    @Test void rejectsMissingWrongAndUnsafePackages() throws Exception {
        assertFalse(valid(Map.of("[Content_Types].xml", "types"), OfficePackageValidator.Format.DOCX));
        assertFalse(valid(openDocument("application/vnd.oasis.opendocument.text"), OfficePackageValidator.Format.ODP));
        assertFalse(valid(Map.of("[Content_Types].xml", "types", "word/document.xml", "word", "../escape", "bad"), OfficePackageValidator.Format.DOCX));
    }

    @Test void rejectsExcessiveCompressionRatio() throws Exception {
        var entries = new LinkedHashMap<String, byte[]>();
        entries.put("[Content_Types].xml", "types".getBytes(StandardCharsets.UTF_8));
        entries.put("word/document.xml", new byte[2 * 1024 * 1024]);
        assertFalse(OfficePackageValidator.valid(file(entries), OfficePackageValidator.Format.DOCX));
    }

    private static Map<String,String> openDocument(String mime) {
        return Map.of("mimetype", mime, "content.xml", "content", "META-INF/manifest.xml", "manifest");
    }
    private static boolean valid(Map<String,String> entries, OfficePackageValidator.Format format) throws Exception {
        var bytes = new LinkedHashMap<String,byte[]>();
        entries.forEach((name,value) -> bytes.put(name,value.getBytes(StandardCharsets.UTF_8)));
        return OfficePackageValidator.valid(file(bytes), format);
    }
    private static MockMultipartFile file(Map<String,byte[]> entries) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return new MockMultipartFile("file", "sample.zip", "application/zip", output.toByteArray());
    }
}
