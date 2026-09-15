package edu.manipal.ccd.core;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PdfOptionsTest {
    @Test void preservesSelectionOrder() {
        assertEquals(List.of(3,1,2),new PdfOptions("pdf.extract","3,1-2",0).select(3));
        assertEquals(List.of(1,2,3),new PdfOptions("pdf.rotate","",90).select(3));
    }
    @Test void rejectsMalformedAndUnboundedSelections() {
        for(String pages:List.of("0","1001","2-1","1,1","1-3,2","1,","-1","1e2"))
            assertThrows(IllegalArgumentException.class,()->new PdfOptions("pdf.extract",pages,0),pages);
        assertThrows(IllegalArgumentException.class,()->new PdfOptions("pdf.rotate","",45));
        assertThrows(IllegalArgumentException.class,()->new PdfOptions("unknown","",0));
        assertThrows(IllegalArgumentException.class,()->new PdfOptions("pdf.merge","1",0));
    }
    @Test void validatesAgainstActualDocument() {
        assertThrows(IllegalArgumentException.class,()->new PdfOptions("pdf.extract","3",0).select(2));
        assertThrows(IllegalArgumentException.class,()->new PdfOptions("pdf.reorder","1",0).select(2));
        assertThrows(IllegalArgumentException.class,()->new PdfOptions("pdf.delete","1-2",0).select(2));
        assertEquals(List.of(2,1),new PdfOptions("pdf.reorder","2,1",0).select(2));
    }
    @Test void validatesEditingParameters() {
        assertEquals(List.of(2),new PdfOptions("pdf.duplicate_pages","2",0,2,1,"A4","","CENTER",1,12,.25).select(3));
        assertEquals(List.of(),new PdfOptions("pdf.insert_blank","",0,3,4,"LETTER","","CENTER",1,12,.25).select(3));
        assertThrows(IllegalArgumentException.class,()->new PdfOptions("pdf.insert_blank","",0,1,5,"A4","","CENTER",1,12,.25).select(3));
        assertThrows(IllegalArgumentException.class,()->new PdfOptions("pdf.page_numbers","",0,1,1,"A4","","CENTER",1,12,.25));
        assertThrows(IllegalArgumentException.class,()->new PdfOptions("pdf.watermark_text","",45,1,1,"A4","", "CENTER",1,48,.25));
        assertThrows(IllegalArgumentException.class,()->new PdfOptions("pdf.watermark_text","",45,1,1,"A4","TEST", "CENTER",1,48,1.5));
    }
    @Test void safelyPacksMetadata() {
        String payload=PdfOptions.metadataPayload("DocFlow Cloud","Sample Author","Cloud documents","pdf, cloud");
        var options=new PdfOptions("pdf.metadata_update","",0,1,1,"A4",payload,"CENTER",1,12,.25);
        assertEquals(List.of("DocFlow Cloud","Sample Author","Cloud documents","pdf, cloud"),options.metadata());
        assertThrows(IllegalArgumentException.class,()->PdfOptions.metadataPayload("","","",""));
        assertThrows(IllegalArgumentException.class,()->new PdfOptions("pdf.metadata_update","",0,1,1,"A4","not-packed","CENTER",1,12,.25));
    }
    @Test void validatesTypedResultOperations() {
        assertEquals(List.of(List.of(1,2),List.of(3)),new PdfOptions("pdf.split","1-2,3",0).groups(3));
        assertThrows(IllegalArgumentException.class,()->new PdfOptions("pdf.render_images","",0,1,1,"A4","","CENTER",1,300,.25));
        assertEquals(List.of(1,2),new PdfOptions("pdf.extract_text","",0).select(2));
        assertDoesNotThrow(()->new PdfOptions("convert.image_to_pdf","",0));
        assertDoesNotThrow(()->new PdfOptions("pdf.metadata_read","",0));
        assertDoesNotThrow(()->new PdfOptions("convert.text_to_pdf","",0,1,1,"LETTER","","CENTER",1,11,.25));
        assertDoesNotThrow(()->new PdfOptions("convert.markdown_to_pdf","",0,1,1,"A4","","CENTER",1,11,.25));
        assertDoesNotThrow(()->new PdfOptions("convert.html_to_pdf","",0,1,1,"LETTER","","CENTER",1,11,.25));
        assertDoesNotThrow(()->new PdfOptions("convert.docx_to_pdf","",0));
        assertDoesNotThrow(()->new PdfOptions("convert.odt_to_pdf","",0));
        assertDoesNotThrow(()->new PdfOptions("convert.ods_to_pdf","",0));
        assertThrows(IllegalArgumentException.class,()->new PdfOptions("convert.text_to_pdf","",0,1,1,"A4","","CENTER",1,30,.25));
        assertDoesNotThrow(()->new PdfOptions("pdf.optimize","",0));
        assertEquals("EBOOK",new PdfOptions("pdf.compress","",0,1,1,"A4","ebook","CENTER",1,12,.25).text());
        assertThrows(IllegalArgumentException.class,()->new PdfOptions("pdf.compress","",0,1,1,"A4","unknown","CENTER",1,12,.25));
        assertDoesNotThrow(()->new PdfOptions("pdf.watermark_image","1",0,1,1,"A4","","CENTER",1,25,.35));
        assertThrows(IllegalArgumentException.class,()->new PdfOptions("pdf.watermark_image","",0,1,1,"A4","","CENTER",1,2,.35));
        assertDoesNotThrow(()->new PdfOptions("pdf.protect","",0));assertDoesNotThrow(()->new PdfOptions("pdf.unlock","",0));
    }
}
