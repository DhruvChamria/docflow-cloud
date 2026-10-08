package edu.manipal.ccd.api;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.web.multipart.MultipartFile;

/** Performs bounded structural checks before an Office package reaches LibreOffice. */
final class OfficePackageValidator {
    private static final int MAX_ENTRIES = 1_000;
    private static final long MAX_ENTRY_BYTES = 50L * 1024 * 1024;
    private static final long MAX_EXPANDED_BYTES = 100L * 1024 * 1024;
    private static final long MAX_COMPRESSION_RATIO = 100;

    enum Format {
        DOCX(null, Set.of("[Content_Types].xml", "word/document.xml")),
        PPTX(null, Set.of("[Content_Types].xml", "ppt/presentation.xml")),
        ODT("application/vnd.oasis.opendocument.text", Set.of("content.xml", "META-INF/manifest.xml", "mimetype")),
        ODP("application/vnd.oasis.opendocument.presentation", Set.of("content.xml", "META-INF/manifest.xml", "mimetype")),
        ODS("application/vnd.oasis.opendocument.spreadsheet", Set.of("content.xml", "META-INF/manifest.xml", "mimetype"));

        final String mime;
        final Set<String> required;
        Format(String mime, Set<String> required) { this.mime = mime; this.required = required; }
    }

    private OfficePackageValidator() {}

    static boolean valid(MultipartFile file, Format format) {
        var found = new java.util.HashSet<String>();
        long expanded = 0;
        int entries = 0;
        try (var zip = new ZipInputStream(file.getInputStream())) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null;) {
                if (++entries > MAX_ENTRIES || unsafeName(entry.getName())) return false;
                if (entry.isDirectory()) continue;
                var prefix = entry.getName().equals("mimetype") ? new ByteArrayOutputStream(160) : null;
                long entryBytes = 0;
                byte[] buffer = new byte[8192];
                for (int read; (read = zip.read(buffer)) != -1;) {
                    entryBytes += read;
                    expanded += read;
                    if (entryBytes > MAX_ENTRY_BYTES || expanded > MAX_EXPANDED_BYTES) return false;
                    if (prefix != null && prefix.size() < 150)
                        prefix.write(buffer, 0, Math.min(read, 150 - prefix.size()));
                }
                long compressed = entry.getCompressedSize();
                if (compressed > 0 && entryBytes > compressed * MAX_COMPRESSION_RATIO) return false;
                if (format.required.contains(entry.getName())) found.add(entry.getName());
                if (prefix != null && !prefix.toString(StandardCharsets.US_ASCII).equals(format.mime)) return false;
            }
        } catch (Exception invalid) {
            return false;
        }
        return found.containsAll(format.required);
    }

    private static boolean unsafeName(String name) {
        if (name == null || name.isBlank() || name.startsWith("/") || name.startsWith("\\") || name.contains("\\")) return true;
        for (String segment : name.split("/")) if (segment.equals("..")) return true;
        return name.length() > 512 || name.matches("^[A-Za-z]:.*");
    }
}
