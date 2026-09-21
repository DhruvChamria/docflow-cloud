package edu.manipal.ccd.office;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Shell-free LibreOffice conversion with a unique profile and bounded runtime. */
final class OfficeOperations {
    private OfficeOperations() {}
    static List<String> command(Path input,Path outputDirectory,Path profile) {
        String binary=System.getenv().getOrDefault("LIBREOFFICE_BIN","soffice");
        return List.of(binary,"-env:UserInstallation="+profile.toUri(),"--headless","--nologo","--nodefault","--nolockcheck","--nofirststartwizard",
            "--convert-to","pdf","--outdir",outputDirectory.toAbsolutePath().toString(),input.toAbsolutePath().toString());
    }
    static Path convert(Path input,Path outputDirectory,Path profile) throws IOException {
        Files.createDirectories(outputDirectory);Files.createDirectories(profile);Path log=outputDirectory.resolve("libreoffice.log");
        Process process=new ProcessBuilder(command(input,outputDirectory,profile)).directory(outputDirectory.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            if(!process.waitFor(Duration.ofSeconds(60).toMillis(),TimeUnit.MILLISECONDS)) {
                process.descendants().forEach(ProcessHandle::destroyForcibly);process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);throw new IOException("Office conversion timed out");
            }
            Path result=outputDirectory.resolve(stripExtension(input.getFileName().toString())+".pdf");
            if(process.exitValue()!=0||!Files.isRegularFile(result)||Files.size(result)<5)throw new IOException("LibreOffice rejected the document"+diagnostic(log));
            byte[] signature=new byte[5];try(var stream=Files.newInputStream(result)) { if(stream.read(signature)!=5||!Arrays.equals(signature,"%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))throw new IOException("LibreOffice produced an invalid result"); }
            if(Files.size(result)>100L*1024*1024)throw new IOException("Office output limit exceeded");return result;
        } catch(InterruptedException interrupted) { Thread.currentThread().interrupt();process.destroyForcibly();throw new IOException("Office conversion interrupted",interrupted); }
        finally { Files.deleteIfExists(log); }
    }
    private static String stripExtension(String name) { int dot=name.lastIndexOf('.');return dot>0?name.substring(0,dot):name; }
    private static String diagnostic(Path log) {
        try { String value=Files.readString(log).replaceAll("[\\r\\n]+"," ");if(value.length()>300)value=value.substring(0,300);return value.isBlank()?"":": "+value; }
        catch(IOException ignored) { return ""; }
    }
}
