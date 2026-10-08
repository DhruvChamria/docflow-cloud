package edu.manipal.ccd.core;

import java.util.*;

/** Validated, bounded PDF-operation parameters shared by API and worker. */
public record PdfOptions(String operation,String pages,int angle,int copies,int insertAt,String pageSize,
                         String text,String position,int startNumber,int fontSize,double opacity) {
    private static final Set<String> OPERATIONS=Set.of("pdf.merge","pdf.split","pdf.extract","pdf.rotate","pdf.reorder","pdf.delete",
        "pdf.duplicate_pages","pdf.insert_blank","pdf.page_numbers","pdf.watermark_text",
        "pdf.render_images","pdf.extract_text","convert.image_to_pdf","convert.text_to_pdf","convert.markdown_to_pdf","convert.html_to_pdf","convert.docx_to_pdf","convert.pptx_to_pdf","convert.odt_to_pdf","convert.odp_to_pdf","convert.ods_to_pdf",
        "pdf.watermark_image","pdf.metadata_read","pdf.metadata_update","pdf.metadata_remove","pdf.optimize","pdf.compress","pdf.protect","pdf.unlock");
    private static final Set<String> POSITIONS=Set.of("TOP_LEFT","TOP_RIGHT","BOTTOM_LEFT","BOTTOM_CENTER","BOTTOM_RIGHT","CENTER");

    public PdfOptions(String operation,String pages,int angle) {
        this(operation,pages,angle,1,1,"A4","","CENTER",1,12,0.25);
    }
    public PdfOptions {
        operation=operation==null?"":operation.trim();pages=pages==null?"":pages.trim();
        pageSize=pageSize==null?"A4":pageSize.trim().toUpperCase(Locale.ROOT);
        text=text==null?"":text.trim();position=position==null?"CENTER":position.trim().toUpperCase(Locale.ROOT);
        if(!OPERATIONS.contains(operation)) throw new IllegalArgumentException("Unsupported operation");
        if(!pages.isEmpty()) parse(pages,1000);
        switch(operation) {
            case "pdf.merge" -> { if(!pages.isEmpty()||angle!=0) throw new IllegalArgumentException("Merge does not accept page options"); }
            case "pdf.split","pdf.extract","pdf.reorder","pdf.delete" -> { if(pages.isEmpty()) throw new IllegalArgumentException("Enter page numbers, such as 1,3-5"); }
            case "pdf.rotate" -> { if(angle!=90&&angle!=180&&angle!=270) throw new IllegalArgumentException("Rotation must be 90, 180 or 270 degrees clockwise"); }
            case "pdf.duplicate_pages" -> {
                if(pages.isEmpty()) throw new IllegalArgumentException("Choose pages to duplicate");
                if(copies<1||copies>10) throw new IllegalArgumentException("Copies must be between 1 and 10");
            }
            case "pdf.insert_blank" -> {
                if(insertAt<1||insertAt>1001) throw new IllegalArgumentException("Insertion position must be between 1 and 1001");
                if(copies<1||copies>20) throw new IllegalArgumentException("Blank page count must be between 1 and 20");
                if(!Set.of("A4","LETTER").contains(pageSize)) throw new IllegalArgumentException("Page size must be A4 or LETTER");
            }
            case "pdf.page_numbers" -> {
                if(!POSITIONS.contains(position)||position.equals("CENTER")) throw new IllegalArgumentException("Unsupported page-number position");
                if(startNumber < -1_000_000 || startNumber > 1_000_000) throw new IllegalArgumentException("Start number is outside the supported range");
                if(fontSize<6||fontSize>72) throw new IllegalArgumentException("Font size must be between 6 and 72");
            }
            case "pdf.watermark_text" -> {
                if(text.isEmpty()||text.length()>100) throw new IllegalArgumentException("Watermark text must contain 1 to 100 characters");
                if(angle < -180 || angle > 180) throw new IllegalArgumentException("Watermark angle must be between -180 and 180");
                if(fontSize<8||fontSize>144) throw new IllegalArgumentException("Font size must be between 8 and 144");
                if(!Double.isFinite(opacity)||opacity<0.05||opacity>1) throw new IllegalArgumentException("Opacity must be between 0.05 and 1");
            }
            case "pdf.render_images" -> { if(fontSize<72||fontSize>200) throw new IllegalArgumentException("Rendering DPI must be between 72 and 200"); }
            case "pdf.extract_text" -> { }
            case "convert.image_to_pdf" -> {
                if(!pages.isEmpty()||angle!=0) throw new IllegalArgumentException("Image conversion does not accept PDF page options");
                if(!Set.of("A4","LETTER").contains(pageSize)) throw new IllegalArgumentException("Page size must be A4 or LETTER");
            }
            case "convert.text_to_pdf","convert.markdown_to_pdf","convert.html_to_pdf" -> {
                if(!pages.isEmpty()||angle!=0) throw new IllegalArgumentException("Text conversion does not accept PDF page options");
                if(!Set.of("A4","LETTER").contains(pageSize)) throw new IllegalArgumentException("Page size must be A4 or LETTER");
                if(fontSize<8||fontSize>24) throw new IllegalArgumentException("Font size must be between 8 and 24");
            }
            case "convert.docx_to_pdf","convert.pptx_to_pdf","convert.odt_to_pdf","convert.odp_to_pdf","convert.ods_to_pdf" -> { if(!pages.isEmpty()||angle!=0||!text.isEmpty()) throw new IllegalArgumentException("Office conversion does not accept PDF options"); }
            case "pdf.watermark_image" -> {
                if(!POSITIONS.contains(position)) throw new IllegalArgumentException("Unsupported image-watermark position");
                if(fontSize<5||fontSize>90) throw new IllegalArgumentException("Image width must be between 5 and 90 percent");
                if(!Double.isFinite(opacity)||opacity<0.05||opacity>1) throw new IllegalArgumentException("Opacity must be between 0.05 and 1");
            }
            case "pdf.metadata_read" -> { if(!pages.isEmpty()||angle!=0||!text.isEmpty()) throw new IllegalArgumentException("Metadata reading does not accept values"); }
            case "pdf.metadata_update" -> { if(!pages.isEmpty()||angle!=0) throw new IllegalArgumentException("Metadata update does not accept page options"); decodeMetadata(text); }
            case "pdf.metadata_remove" -> { if(!pages.isEmpty()||angle!=0||!text.isEmpty()) throw new IllegalArgumentException("Metadata removal does not accept values"); }
            case "pdf.optimize" -> { if(!pages.isEmpty()||angle!=0||!text.isEmpty()) throw new IllegalArgumentException("Optimization does not accept page options"); }
            case "pdf.compress" -> {
                if(!pages.isEmpty()||angle!=0) throw new IllegalArgumentException("Compression does not accept page options");
                if(!Set.of("SCREEN","EBOOK","PRINTER").contains(text.toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("Compression quality must be SCREEN, EBOOK or PRINTER");
                text=text.toUpperCase(Locale.ROOT);
            }
            case "pdf.protect","pdf.unlock" -> { if(!pages.isEmpty()||angle!=0||!text.isEmpty()) throw new IllegalArgumentException("Password operations do not accept page options"); }
            default -> { }
        }
    }

    /** Packs optional metadata into the already-persisted bounded text field without delimiter ambiguity. */
    public static String metadataPayload(String title,String author,String subject,String keywords) {
        var values=List.of(clean(title,250),clean(author,250),clean(subject,500),clean(keywords,500));
        if(values.stream().allMatch(String::isEmpty)) throw new IllegalArgumentException("Enter at least one metadata value");
        var encoder=Base64.getUrlEncoder().withoutPadding();
        return values.stream().map(value->encoder.encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))).collect(java.util.stream.Collectors.joining(":"));
    }
    public List<String> metadata() { return decodeMetadata(text); }
    private static List<String> decodeMetadata(String payload) {
        try {
            String[] values=payload.split(":",-1);if(values.length!=4) throw new IllegalArgumentException("Invalid metadata values");
            var decoder=Base64.getUrlDecoder();var result=new ArrayList<String>();
            for(int index=0;index<values.length;index++) result.add(clean(new String(decoder.decode(values[index]),java.nio.charset.StandardCharsets.UTF_8),index<2?250:500));
            if(result.stream().allMatch(String::isEmpty)) throw new IllegalArgumentException("Enter at least one metadata value");
            return List.copyOf(result);
        } catch(IllegalArgumentException invalid) { throw new IllegalArgumentException("Invalid metadata values"); }
    }
    private static String clean(String value,int limit) {
        value=value==null?"":value.trim();if(value.length()>limit) throw new IllegalArgumentException("Metadata value is too long");return value;
    }

    public List<Integer> select(int count) {
        if(count<1||count>1000) throw new IllegalArgumentException("PDF must contain 1 to 1000 pages");
        if(operation.equals("pdf.insert_blank")) {
            if(insertAt>count+1) throw new IllegalArgumentException("Insertion position is outside the document");
            return List.of();
        }
        List<Integer> selected=pages.isEmpty()?java.util.stream.IntStream.rangeClosed(1,count).boxed().toList():parse(pages,count);
        if(operation.equals("pdf.render_images")&&selected.size()>50) throw new IllegalArgumentException("Render at most 50 pages per job");
        if(operation.equals("pdf.reorder")&&selected.size()!=count) throw new IllegalArgumentException("Reorder must list every page exactly once");
        if(operation.equals("pdf.delete")&&selected.size()==count) throw new IllegalArgumentException("At least one page must remain");
        return selected;
    }

    public List<List<Integer>> groups(int count) {
        if(!operation.equals("pdf.split")) throw new IllegalStateException("Page groups are only available for split jobs");
        select(count);var groups=new ArrayList<List<Integer>>();
        for(String token:pages.split(",")) groups.add(parse(token.trim(),count));
        if(groups.size()>50) throw new IllegalArgumentException("Split into at most 50 files per job");
        return List.copyOf(groups);
    }

    private static List<Integer> parse(String text,int count) {
        if(text.length()>5000) throw new IllegalArgumentException("Page selection is too long");
        var result=new LinkedHashSet<Integer>();
        for(String token:text.split(",",-1)) {
            if(!token.trim().matches("[0-9]{1,4}(\\s*-\\s*[0-9]{1,4})?")) throw new IllegalArgumentException("Use page numbers and ascending ranges, such as 1,3-5");
            String[] ends=token.trim().split("\\s*-\\s*");int first=Integer.parseInt(ends[0]);int last=ends.length==1?first:Integer.parseInt(ends[1]);
            if(first<1||last<first||last>count) throw new IllegalArgumentException("Page selection is outside the document or range is reversed");
            for(int page=first;page<=last;page++) if(!result.add(page)) throw new IllegalArgumentException("Each page may appear only once");
        }
        return List.copyOf(result);
    }
}
