package edu.manipal.ccd.worker;
import java.nio.file.*;
import java.util.List;
import org.apache.pdfbox.*;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class MergeTest {
    @TempDir Path directory;
    @Test void preservesPageCountAndOrder() throws Exception {
        var first=directory.resolve("a.pdf");var second=directory.resolve("b.pdf");var output=directory.resolve("out.pdf");
        try(var doc=new PDDocument()) { doc.addPage(new PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.A4));doc.save(first.toFile()); }
        try(var doc=new PDDocument()) { doc.addPage(new PDPage(org.apache.pdfbox.pdmodel.common.PDRectangle.LETTER));doc.save(second.toFile()); }
        Merge.run(List.of(first,second),output);
        try(var doc=Loader.loadPDF(output.toFile())) {
            assertEquals(2,doc.getNumberOfPages());
            assertEquals(org.apache.pdfbox.pdmodel.common.PDRectangle.A4.getWidth(),doc.getPage(0).getMediaBox().getWidth());
            assertEquals(org.apache.pdfbox.pdmodel.common.PDRectangle.LETTER.getWidth(),doc.getPage(1).getMediaBox().getWidth());
        }
    }
    @Test void rejectsMalformedDocument() throws Exception {
        var input=directory.resolve("bad.pdf");Files.writeString(input,"%PDF-not-a-real-document");
        assertThrows(java.io.IOException.class,()->Merge.run(List.of(input,input),directory.resolve("out.pdf")));
    }
}
