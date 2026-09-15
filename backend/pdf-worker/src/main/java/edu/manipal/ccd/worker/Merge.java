package edu.manipal.ccd.worker;
import java.nio.file.*;
import java.util.List;
import java.io.IOException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.apache.pdfbox.io.IOUtils;

public class Merge {
    public static void run(List<Path> sources, Path output) throws IOException {
        int pages=0;
        var merger=new PDFMergerUtility();
        for (var source:sources) {
            try (var pdf=Loader.loadPDF(source.toFile())) {
                if (pdf.isEncrypted()) throw new IOException("Encrypted PDFs are unsupported");
                pages+=pdf.getNumberOfPages();
                if (pages>1000) throw new IOException("Page limit exceeded");
            }
            merger.addSource(source.toFile());
        }
        merger.setDestinationFileName(output.toString());
        merger.mergeDocuments(IOUtils.createTempFileOnlyStreamCache());
        if (Files.size(output)>100L*1024*1024) throw new IOException("Output limit exceeded");
    }
}

