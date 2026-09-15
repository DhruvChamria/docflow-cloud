package edu.manipal.ccd.worker;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Fixed-argument, bounded Ghostscript adapter. It never invokes a command shell. */
final class GhostscriptOperations {
    private GhostscriptOperations() {}

    static List<String> command(Path input,Path output,String preset) {
        String binary=System.getenv().getOrDefault("GHOSTSCRIPT_BIN","gs");
        String setting=switch(preset) { case "SCREEN" -> "/screen";case "PRINTER" -> "/printer";default -> "/ebook"; };
        return List.of(binary,"-dSAFER","-dBATCH","-dNOPAUSE","-dQUIET","-sDEVICE=pdfwrite","-dCompatibilityLevel=1.7",
            "-dPDFSETTINGS="+setting,"-dDetectDuplicateImages=true","-dCompressFonts=true","-sOutputFile="+output.toAbsolutePath(),input.toAbsolutePath().toString());
    }

    static void compress(Path input,Path output,String preset) throws IOException {
        Path log=output.resolveSibling(output.getFileName()+".ghostscript.log");
        Process process=new ProcessBuilder(command(input,output,preset)).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            if(!process.waitFor(Duration.ofSeconds(60).toMillis(),TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();process.waitFor(5,TimeUnit.SECONDS);throw new IOException("Ghostscript compression timed out");
            }
            if(process.exitValue()!=0||!Files.isRegularFile(output)||Files.size(output)<5) {
                String detail=Files.exists(log)?Files.readString(log).replaceAll("[\\r\\n]+"," "):"";
                if(detail.length()>300)detail=detail.substring(0,300);
                throw new IOException("Ghostscript rejected the PDF"+(detail.isBlank()?"":": "+detail));
            }
            byte[] signature=new byte[5];try(var stream=Files.newInputStream(output)) { if(stream.read(signature)!=5||!Arrays.equals(signature,"%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII))) throw new IOException("Ghostscript produced an invalid result"); }
        } catch(InterruptedException interrupted) { Thread.currentThread().interrupt();process.destroyForcibly();throw new IOException("Ghostscript compression interrupted",interrupted); }
        finally { Files.deleteIfExists(log); }
    }
}
