package edu.manipal.ccd.worker;
import edu.manipal.ccd.core.PdfOptions;
import java.nio.file.*;
import java.util.Arrays;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class PdfOperationsTest {
    @TempDir Path directory;
    Path fixture() throws Exception {
        Path file=directory.resolve("input-"+java.util.UUID.randomUUID()+".pdf");
        try(var pdf=new PDDocument()) {
            pdf.addPage(new PDPage(PDRectangle.A4));pdf.addPage(new PDPage(PDRectangle.LETTER));
            pdf.getPage(0).setRotation(270);
            pdf.getDocumentInformation().setTitle("Fixture title");
            try(var content=new PDPageContentStream(pdf,pdf.getPage(0))) {
                content.beginText();content.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);
                content.newLineAtOffset(40,700);content.showText("Fixture text");content.endText();
            }
            pdf.save(file.toFile());
        }
        return file;
    }
    @Test void extractsAndReordersPages() throws Exception {
        for(String operation:List.of("pdf.extract","pdf.reorder")) {
            Path output=directory.resolve(operation+".pdf");
            PdfOperations.run(new PdfOptions(operation,"2,1",0),List.of(fixture()),output);
            try(var pdf=Loader.loadPDF(output.toFile())) {
                assertEquals(2,pdf.getNumberOfPages());
                assertEquals(PDRectangle.LETTER.getWidth(),pdf.getPage(0).getMediaBox().getWidth());
                assertEquals(PDRectangle.A4.getWidth(),pdf.getPage(1).getMediaBox().getWidth());
                assertEquals(270,pdf.getPage(1).getRotation());
            }
        }
    }
    @Test void deletesSelectedPages() throws Exception {
        Path output=directory.resolve("deleted.pdf");
        PdfOperations.run(new PdfOptions("pdf.delete","1",0),List.of(fixture()),output);
        try(var pdf=Loader.loadPDF(output.toFile())) {
            assertEquals(1,pdf.getNumberOfPages());
            assertEquals(PDRectangle.LETTER.getWidth(),pdf.getPage(0).getMediaBox().getWidth());
        }
    }
    @Test void rotationIsAdditiveAndSelective() throws Exception {
        Path output=directory.resolve("rotated.pdf");
        PdfOperations.run(new PdfOptions("pdf.rotate","1",90),List.of(fixture()),output);
        try(var pdf=Loader.loadPDF(output.toFile())) {
            assertEquals(2,pdf.getNumberOfPages());assertEquals(0,pdf.getPage(0).getRotation());assertEquals(0,pdf.getPage(1).getRotation());
        }
    }
    @Test void duplicatesAndInsertsBlankPages() throws Exception {
        Path duplicated=directory.resolve("duplicated.pdf");
        PdfOperations.run(new PdfOptions("pdf.duplicate_pages","1",0,2,1,"A4","","CENTER",1,12,.25),List.of(fixture()),duplicated);
        try(var pdf=Loader.loadPDF(duplicated.toFile())) {
            assertEquals(4,pdf.getNumberOfPages());
            assertEquals(PDRectangle.A4.getWidth(),pdf.getPage(0).getMediaBox().getWidth());
            assertEquals(PDRectangle.A4.getWidth(),pdf.getPage(2).getMediaBox().getWidth());
            assertEquals(PDRectangle.LETTER.getWidth(),pdf.getPage(3).getMediaBox().getWidth());
        }
        Path blank=directory.resolve("blank.pdf");
        PdfOperations.run(new PdfOptions("pdf.insert_blank","",0,2,2,"LETTER","","CENTER",1,12,.25),List.of(fixture()),blank);
        try(var pdf=Loader.loadPDF(blank.toFile())) {
            assertEquals(4,pdf.getNumberOfPages());
            assertEquals(PDRectangle.A4.getWidth(),pdf.getPage(0).getMediaBox().getWidth());
            assertEquals(PDRectangle.LETTER.getWidth(),pdf.getPage(1).getMediaBox().getWidth());
            assertEquals(PDRectangle.LETTER.getWidth(),pdf.getPage(2).getMediaBox().getWidth());
        }
    }
    @Test void addsPageNumbersAndWatermarks() throws Exception {
        Path numbered=directory.resolve("numbered.pdf");
        PdfOperations.run(new PdfOptions("pdf.page_numbers","",0,1,1,"A4","","BOTTOM_CENTER",5,12,.25),List.of(fixture()),numbered);
        try(var pdf=Loader.loadPDF(numbered.toFile())) {
            var text=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);assertTrue(text.contains("5"));assertTrue(text.contains("6"));
        }
        Path marked=directory.resolve("marked.pdf");
        PdfOperations.run(new PdfOptions("pdf.watermark_text","1",45,1,1,"A4","SAMPLE","CENTER",1,48,.3),List.of(fixture()),marked);
        try(var pdf=Loader.loadPDF(marked.toFile())) {
            var stripper=new org.apache.pdfbox.text.PDFTextStripper();
            stripper.setStartPage(1);stripper.setEndPage(1);assertTrue(stripper.getText(pdf).contains("SAMPLE"));
            stripper.setStartPage(2);stripper.setEndPage(2);assertFalse(stripper.getText(pdf).contains("SAMPLE"));
        }
    }
    @Test void updatesAndRemovesMetadata() throws Exception {
        Path updated=directory.resolve("metadata.pdf");
        String payload=PdfOptions.metadataPayload("DocFlow Cloud","Sample Author","Document processing","cloud,pdf");
        PdfOperations.run(new PdfOptions("pdf.metadata_update","",0,1,1,"A4",payload,"CENTER",1,12,.25),List.of(fixture()),updated);
        try(var pdf=Loader.loadPDF(updated.toFile())) {
            assertEquals("DocFlow Cloud",pdf.getDocumentInformation().getTitle());
            assertEquals("Sample Author",pdf.getDocumentInformation().getAuthor());
            assertEquals("Document processing",pdf.getDocumentInformation().getSubject());
            assertEquals("cloud,pdf",pdf.getDocumentInformation().getKeywords());
        }
        Path removed=directory.resolve("removed-metadata.pdf");
        PdfOperations.run(new PdfOptions("pdf.metadata_remove","",0),List.of(updated),removed);
        try(var pdf=Loader.loadPDF(removed.toFile())) {
            assertNull(pdf.getDocumentInformation().getTitle());assertNull(pdf.getDocumentInformation().getAuthor());
            assertNull(pdf.getDocumentInformation().getSubject());assertNull(pdf.getDocumentInformation().getKeywords());
        }
    }
    @Test void createsTypedZipTextJsonAndImageResults() throws Exception {
        Path input=fixture(),split=directory.resolve("split.zip");
        PdfOperations.run(new PdfOptions("pdf.split","1,2",0),List.of(input),split);
        try(var zip=new java.util.zip.ZipFile(split.toFile())) {
            assertEquals(2,zip.size());assertNotNull(zip.getEntry("part-01.pdf"));assertNotNull(zip.getEntry("part-02.pdf"));
        }
        Path rendered=directory.resolve("pages.zip");
        PdfOperations.run(new PdfOptions("pdf.render_images","1",0,1,1,"A4","","CENTER",1,96,.25),List.of(input),rendered);
        try(var zip=new java.util.zip.ZipFile(rendered.toFile())) { assertEquals(1,zip.size());assertNotNull(zip.getEntry("page-0001.png")); }
        Path text=directory.resolve("result.txt");PdfOperations.run(new PdfOptions("pdf.extract_text","1",0),List.of(input),text);
        assertTrue(Files.readString(text).replaceAll("\\s+","").contains("Fixturetext"));
        Path json=directory.resolve("metadata.json");PdfOperations.run(new PdfOptions("pdf.metadata_read","",0),List.of(input),json);
        assertTrue(Files.readString(json).contains("Fixture title"));
        var portrait=new java.awt.image.BufferedImage(40,80,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var landscape=new java.awt.image.BufferedImage(80,40,java.awt.image.BufferedImage.TYPE_INT_RGB);
        Path first=directory.resolve("first.png"),second=directory.resolve("second.jpg");
        javax.imageio.ImageIO.write(portrait,"PNG",first.toFile());javax.imageio.ImageIO.write(landscape,"JPEG",second.toFile());
        Path imagePdf=directory.resolve("images.pdf");PdfOperations.run(new PdfOptions("convert.image_to_pdf","",0),List.of(first,second),imagePdf);
        try(var pdf=Loader.loadPDF(imagePdf.toFile())) {
            assertEquals(2,pdf.getNumberOfPages());assertTrue(pdf.getPage(0).getMediaBox().getHeight()>pdf.getPage(0).getMediaBox().getWidth());
            assertTrue(pdf.getPage(1).getMediaBox().getWidth()>pdf.getPage(1).getMediaBox().getHeight());
        }
    }
    @Test void addsImageWatermarkAndOptimizes() throws Exception {
        var watermark=new java.awt.image.BufferedImage(60,30,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var graphics=watermark.createGraphics();graphics.setColor(new java.awt.Color(255,0,0,180));graphics.fillRect(0,0,60,30);graphics.dispose();
        Path image=directory.resolve("watermark.png");javax.imageio.ImageIO.write(watermark,"PNG",image.toFile());
        Path marked=directory.resolve("image-watermarked.pdf");
        PdfOperations.run(new PdfOptions("pdf.watermark_image","1",0,1,1,"A4","","BOTTOM_RIGHT",1,25,.4),List.of(fixture(),image),marked);
        try(var pdf=Loader.loadPDF(marked.toFile())) {
            assertEquals(2,pdf.getNumberOfPages());assertTrue(pdf.getPage(0).getResources().getXObjectNames().iterator().hasNext());
            assertNull(pdf.getPage(1).getResources());
        }
        Path optimized=directory.resolve("optimized.pdf");PdfOperations.run(new PdfOptions("pdf.optimize","",0),List.of(marked),optimized);
        try(var pdf=Loader.loadPDF(optimized.toFile())) { assertEquals(2,pdf.getNumberOfPages());assertEquals("Fixture title",pdf.getDocumentInformation().getTitle()); }
    }
    @Test void convertsPaginatedTextToPdf() throws Exception {
        Path input=directory.resolve("notes.txt");Files.writeString(input,"DocFlow document platform\n\n"+"A long line ".repeat(80),java.nio.charset.StandardCharsets.UTF_8);
        Path output=directory.resolve("notes.pdf");
        PdfOperations.run(new PdfOptions("convert.text_to_pdf","",0,1,1,"A4","","CENTER",1,11,.25),List.of(input),output);
        try(var pdf=Loader.loadPDF(output.toFile())) {
            assertTrue(pdf.getNumberOfPages()>=1);String text=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);
            assertTrue(text.contains("DocFlow document platform"));assertTrue(text.contains("A long line"));
        }
    }
    @Test void convertsMarkdownWithoutLoadingRemoteResources() throws Exception {
        Path input=directory.resolve("notes.md");Files.writeString(input,"# DocFlow Cloud\n\n- asynchronous jobs\n- safe conversion\n\n![remote](https://invalid.example/image.png)\n<script>alert('x')</script>");
        Path output=directory.resolve("markdown.pdf");
        PdfOperations.run(new PdfOptions("convert.markdown_to_pdf","",0,1,1,"A4","","CENTER",1,11,.25),List.of(input),output);
        try(var pdf=Loader.loadPDF(output.toFile())) { String text=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);assertTrue(text.contains("DocFlow Cloud"));assertTrue(text.contains("asynchronous jobs"));assertFalse(text.contains("alert")); }
    }
    @Test void sanitizesAndConvertsRestrictedHtml() throws Exception {
        Path input=directory.resolve("document.html");Files.writeString(input,"<h1>Safe HTML</h1><p>Rendered locally.</p><img src='https://invalid.example/a.png'><script>danger()</script><form><input value='secret'></form>");
        Path output=directory.resolve("html.pdf");
        PdfOperations.run(new PdfOptions("convert.html_to_pdf","",0,1,1,"LETTER","","CENTER",1,11,.25),List.of(input),output);
        try(var pdf=Loader.loadPDF(output.toFile())) { String text=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);assertTrue(text.contains("Safe HTML"));assertTrue(text.contains("Rendered locally"));assertFalse(text.contains("danger"));assertFalse(text.contains("secret")); }
    }
    @Test void protectsAndUnlocksWithAesPassword() throws Exception {
        char[] password="DocumentSecret123".toCharArray();Path protectedFile=directory.resolve("protected.pdf");
        PdfOperations.run(new PdfOptions("pdf.protect","",0),List.of(fixture()),protectedFile,password);
        assertThrows(org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException.class,()->Loader.loadPDF(protectedFile.toFile()));
        try(var pdf=Loader.loadPDF(protectedFile.toFile(),new String(password))) { assertTrue(pdf.isEncrypted());assertEquals(2,pdf.getNumberOfPages()); }
        Path unlocked=directory.resolve("unlocked.pdf");PdfOperations.run(new PdfOptions("pdf.unlock","",0),List.of(protectedFile),unlocked,password);
        try(var pdf=Loader.loadPDF(unlocked.toFile())) { assertFalse(pdf.isEncrypted());assertEquals(2,pdf.getNumberOfPages()); }
        Arrays.fill(password,'\0');
    }
}
