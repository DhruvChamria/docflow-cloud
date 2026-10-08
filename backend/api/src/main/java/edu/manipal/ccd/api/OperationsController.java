package edu.manipal.ccd.api;

import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1")
class OperationsController {
    private final org.springframework.core.env.Environment environment;
    OperationsController(org.springframework.core.env.Environment environment) { this.environment=environment; }
    record ParameterInfo(String name,String label,String type,boolean required,String help,List<String> options,String defaultValue) {}
    record OperationInfo(String code, String label, boolean available, List<String> inputTypes, int minFiles, int maxFiles,
                         String description,List<ParameterInfo> parameters) {}
    private ParameterInfo pages(boolean required,String help) { return new ParameterInfo("pages","Page numbers","text",required,help,List.of(),""); }
    private ParameterInfo angle() { return new ParameterInfo("angle","Clockwise rotation","select",true,"Rotation applied to selected pages.",List.of("90","180","270"),"90"); }
    private ParameterInfo text(String name,String label,boolean required,String help,String value) { return new ParameterInfo(name,label,"text",required,help,List.of(),value); }
    private ParameterInfo select(String name,String label,String help,String... values) { return new ParameterInfo(name,label,"select",true,help,List.of(values),values[0]); }
    private boolean environment.matchesProfiles("local") { return environment.matchesProfiles("local","cloud"); }

    @GetMapping("/operations")
    List<OperationInfo> operations() {
        return List.of(
            new OperationInfo("pdf.merge", "Merge PDFs", environment.matchesProfiles("local"), List.of("application/pdf"),2,10,
                "Combine PDFs in the displayed order.",List.of()),
            new OperationInfo("pdf.split", "Split PDF by ranges", environment.matchesProfiles("local"), List.of("application/pdf"),1,1,
                "Create a ZIP with one PDF per comma-separated page or range.",List.of(pages(true,"Example: 1-3,4-6 creates two PDFs."))),
            new OperationInfo("pdf.extract", "Extract PDF pages", environment.matchesProfiles("local"), List.of("application/pdf"),1,1,
                "Create a PDF from selected pages.",List.of(pages(true,"Use 1-based pages and ranges, such as 1,3-5."))),
            new OperationInfo("pdf.rotate", "Rotate PDF pages", environment.matchesProfiles("local"), List.of("application/pdf"),1,1,
                "Rotate selected pages or the whole document.",List.of(pages(false,"Leave blank to rotate every page."),angle())),
            new OperationInfo("pdf.reorder", "Reorder PDF pages", environment.matchesProfiles("local"), List.of("application/pdf"),1,1,
                "List every page exactly once in the desired order.",List.of(pages(true,"Example: 3,1,2."))),
            new OperationInfo("pdf.delete", "Delete PDF pages", environment.matchesProfiles("local"), List.of("application/pdf"),1,1,
                "Remove selected pages while leaving at least one.",List.of(pages(true,"Pages to remove, such as 2,4-6."))),
            new OperationInfo("pdf.duplicate_pages","Duplicate PDF pages",environment.matchesProfiles("local"),List.of("application/pdf"),1,1,
                "Copy selected pages immediately after each original.",List.of(pages(true,"Pages to duplicate."),text("copies","Extra copies",true,"Enter 1 to 10.","1"))),
            new OperationInfo("pdf.insert_blank","Insert blank pages",environment.matchesProfiles("local"),List.of("application/pdf"),1,1,
                "Insert blank A4 or Letter pages at a 1-based position.",List.of(text("insertAt","Insert before page",true,"Use page count + 1 to append.","1"),text("copies","Blank page count",true,"Enter 1 to 20.","1"),select("pageSize","Page size","Size of each new blank page.","A4","LETTER"))),
            new OperationInfo("pdf.page_numbers","Add page numbers",environment.matchesProfiles("local"),List.of("application/pdf"),1,1,
                "Add sequential page numbers to selected pages or all pages.",List.of(pages(false,"Leave blank for every page."),text("startNumber","Start number",true,"First displayed number.","1"),text("fontSize","Font size",true,"Enter 6 to 72.","12"),select("position","Position","Placement inside the page margin.","BOTTOM_CENTER","BOTTOM_LEFT","BOTTOM_RIGHT","TOP_LEFT","TOP_RIGHT"))),
            new OperationInfo("pdf.watermark_text","Add text watermark",environment.matchesProfiles("local"),List.of("application/pdf"),1,1,
                "Overlay a centered text watermark on selected pages or all pages.",List.of(pages(false,"Leave blank for every page."),text("text","Watermark text",true,"Maximum 100 characters.","CONFIDENTIAL"),text("fontSize","Font size",true,"Enter 8 to 144.","48"),text("angle","Angle",true,"Enter -180 to 180 degrees.","45"),text("opacity","Opacity",true,"Enter 0.05 to 1.","0.25"))),
            new OperationInfo("pdf.watermark_image","Add image watermark",environment.matchesProfiles("local"),List.of("application/pdf","image/png","image/jpeg"),2,2,
                "Upload the PDF first and a JPG/PNG watermark second; transparency and aspect ratio are preserved.",List.of(pages(false,"Leave blank for every page."),text("scalePercent","Image width (%)",true,"Enter 5 to 90.","25"),text("opacity","Opacity",true,"Enter 0.05 to 1.","0.35"),select("position","Position","Watermark placement.","CENTER","TOP_LEFT","TOP_RIGHT","BOTTOM_LEFT","BOTTOM_RIGHT"))),
            new OperationInfo("convert.image_to_pdf","Images to PDF",environment.matchesProfiles("local"),List.of("image/jpeg","image/png"),1,20,
                "Fit JPG or PNG images onto PDF pages without changing aspect ratio.",List.of(select("pageSize","Page size","Output paper size; landscape images use landscape orientation.","A4","LETTER"))),
            new OperationInfo("convert.text_to_pdf","Text to PDF",environment.matchesProfiles("local"),List.of("text/plain"),1,1,
                "Convert a UTF-8 plain-text file using the built-in Latin PDF font.",List.of(select("pageSize","Page size","Output paper size.","A4","LETTER"),text("fontSize","Font size",true,"Enter 8 to 24.","11"))),
            new OperationInfo("convert.markdown_to_pdf","Markdown to PDF",environment.matchesProfiles("local"),List.of("text/markdown"),1,1,
                "Render safe CommonMark text with headings, lists, quotes, links and code; embedded HTML and remote resources are removed.",List.of(select("pageSize","Page size","Output paper size.","A4","LETTER"),text("fontSize","Base font size",true,"Enter 8 to 24.","11"))),
            new OperationInfo("convert.html_to_pdf","Restricted HTML to PDF",environment.matchesProfiles("local"),List.of("text/html"),1,1,
                "Render a sanitized HTML subset with fixed local styling; scripts, forms, images, custom styles and remote resources are removed.",List.of(select("pageSize","Page size","Output paper size.","A4","LETTER"),text("fontSize","Base font size",true,"Enter 8 to 24.","11"))),
            new OperationInfo("pdf.render_images","Render PDF pages",environment.matchesProfiles("local"),List.of("application/pdf"),1,1,
                "Render up to 50 selected pages as PNG files in a ZIP.",List.of(pages(false,"Leave blank for every page."),text("dpi","Resolution (DPI)",true,"Enter 72 to 200.","144"))),
            new OperationInfo("pdf.extract_text","Extract PDF text",environment.matchesProfiles("local"),List.of("application/pdf"),1,1,
                "Create a UTF-8 text file from selected text-based PDF pages.",List.of(pages(false,"Leave blank for every page."))),
            new OperationInfo("pdf.metadata_read","Read PDF metadata",environment.matchesProfiles("local"),List.of("application/pdf"),1,1,
                "Download normalized standard PDF metadata as JSON.",List.of()),
            new OperationInfo("pdf.metadata_update","Update PDF metadata",environment.matchesProfiles("local"),List.of("application/pdf"),1,1,
                "Set standard document properties; at least one value is required.",List.of(text("title","Title",false,"Maximum 250 characters.",""),text("author","Author",false,"Maximum 250 characters.",""),text("subject","Subject",false,"Maximum 500 characters.",""),text("keywords","Keywords",false,"Maximum 500 characters.",""))),
            new OperationInfo("pdf.metadata_remove","Remove PDF metadata",environment.matchesProfiles("local"),List.of("application/pdf"),1,1,
                "Clear the standard PDF document-information fields.",List.of()),
            new OperationInfo("pdf.optimize","Optimize PDF structure",environment.matchesProfiles("local"),List.of("application/pdf"),1,1,
                "Rewrite the PDF with lossless object-stream compression; file-size improvement varies.",List.of()),
            new OperationInfo("pdf.compress","Compress PDF",environment.matchesProfiles("local"),List.of("application/pdf"),1,1,
                "Use isolated Ghostscript lossy compression; visual quality and size reduction depend on the source PDF.",List.of(select("quality","Quality preset","SCREEN is smallest/lowest quality, EBOOK is balanced, PRINTER retains more detail.","EBOOK","SCREEN","PRINTER"))),
            new OperationInfo("pdf.protect","Password-protect PDF",environment.matchesProfiles("local"),List.of("application/pdf"),1,1,
                "Encrypt a PDF with a temporary password that is never stored in plaintext.",List.of(new ParameterInfo("documentPassword","Document password","password",true,"Use 8 to 128 characters; retain it because it cannot be recovered.",List.of(),""))),
            new OperationInfo("pdf.unlock","Unlock PDF",environment.matchesProfiles("local"),List.of("application/pdf"),1,1,
                "Remove encryption using the correct existing PDF password.",List.of(new ParameterInfo("documentPassword","Current password","password",true,"The temporary password is encrypted and erased after processing.",List.of(),""))),
            new OperationInfo("convert.docx_to_pdf", "Word to PDF", environment.matchesProfiles("local"),
                List.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document"),1,1,"Convert a DOCX file using the isolated, resource-limited LibreOffice worker.",List.of()),
            new OperationInfo("convert.pptx_to_pdf", "PowerPoint to PDF", environment.matchesProfiles("local"),
                List.of("application/vnd.openxmlformats-officedocument.presentationml.presentation"),1,1,"Convert a PPTX file using the isolated, resource-limited LibreOffice worker.",List.of()),
            new OperationInfo("convert.odt_to_pdf", "ODT to PDF", environment.matchesProfiles("local"),
                List.of("application/vnd.oasis.opendocument.text"),1,1,"Convert an OpenDocument Text file using the isolated, resource-limited LibreOffice worker.",List.of()),
            new OperationInfo("convert.odp_to_pdf", "ODP to PDF", environment.matchesProfiles("local"),
                List.of("application/vnd.oasis.opendocument.presentation"),1,1,"Convert an OpenDocument Presentation using the isolated, resource-limited LibreOffice worker.",List.of()),
            new OperationInfo("convert.ods_to_pdf", "ODS to PDF", environment.matchesProfiles("local"),
                List.of("application/vnd.oasis.opendocument.spreadsheet"),1,1,"Convert an OpenDocument Spreadsheet using the isolated, resource-limited LibreOffice worker.",List.of()));
    }

}
