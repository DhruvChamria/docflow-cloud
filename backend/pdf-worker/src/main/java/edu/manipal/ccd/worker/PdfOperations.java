package edu.manipal.ccd.worker;

import edu.manipal.ccd.core.PdfOptions;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.util.Matrix;

public final class PdfOperations {
    private PdfOperations() {}
    public static void run(PdfOptions options, List<Path> inputs, Path output) throws IOException {
        run(options,inputs,output,null);
    }
    public static void run(PdfOptions options,List<Path> inputs,Path output,char[] documentPassword) throws IOException {
        if(options.operation().equals("convert.image_to_pdf")) { imagesToPdf(options,inputs,output);checkSize(output);return; }
        if(options.operation().equals("convert.text_to_pdf")) { textToPdf(options,inputs,output);checkSize(output);return; }
        if(options.operation().equals("convert.markdown_to_pdf")||options.operation().equals("convert.html_to_pdf")) { markupToPdf(options,inputs,output);checkSize(output);return; }
        if(options.operation().equals("pdf.compress")) {
            if(inputs.size()!=1)throw new IllegalArgumentException("Exactly one PDF required");
            GhostscriptOperations.compress(inputs.get(0),output,options.text());checkSize(output);return;
        }
        if(options.operation().equals("pdf.watermark_image")) { watermarkImage(options,inputs,output);checkSize(output);return; }
        if (options.operation().equals("pdf.merge")) { Merge.run(inputs, output); return; }
        if (inputs.size() != 1) throw new IllegalArgumentException("Exactly one PDF required");
        String suppliedPassword=documentPassword==null?null:new String(documentPassword);
        try (var source = options.operation().equals("pdf.unlock")?Loader.loadPDF(inputs.get(0).toFile(),suppliedPassword):Loader.loadPDF(inputs.get(0).toFile())) {
            if (source.isEncrypted()&&!options.operation().equals("pdf.unlock")) throw new IOException("Encrypted PDFs are unsupported for this operation");
            var pages = options.select(source.getNumberOfPages());
            if (options.operation().equals("pdf.rotate")) {
                for (int page : pages) {
                    var item = source.getPage(page - 1);
                    item.setRotation(Math.floorMod(item.getRotation() + options.angle(), 360));
                }
                source.save(output.toFile());
            } else if (options.operation().equals("pdf.delete")) {
                for (int page : pages.stream().sorted(Comparator.reverseOrder()).toList()) source.removePage(page - 1);
                source.save(output.toFile());
            } else if (options.operation().equals("pdf.page_numbers")) {
                addPageNumbers(source,pages,options);source.save(output.toFile());
            } else if (options.operation().equals("pdf.watermark_text")) {
                addWatermark(source,pages,options);source.save(output.toFile());
            } else if (options.operation().equals("pdf.metadata_update")) {
                var values=options.metadata();var info=source.getDocumentInformation();
                if(!values.get(0).isEmpty()) info.setTitle(values.get(0));
                if(!values.get(1).isEmpty()) info.setAuthor(values.get(1));
                if(!values.get(2).isEmpty()) info.setSubject(values.get(2));
                if(!values.get(3).isEmpty()) info.setKeywords(values.get(3));
                source.save(output.toFile());
            } else if (options.operation().equals("pdf.metadata_remove")) {
                source.setDocumentInformation(new org.apache.pdfbox.pdmodel.PDDocumentInformation());source.save(output.toFile());
            } else if (options.operation().equals("pdf.metadata_read")) {
                writeMetadata(source,output);
            } else if (options.operation().equals("pdf.split")) {
                split(source,options,output);
            } else if (options.operation().equals("pdf.render_images")) {
                render(source,pages,options.fontSize(),output);
            } else if (options.operation().equals("pdf.extract_text")) {
                extractText(source,pages,output);
            } else if (options.operation().equals("pdf.optimize")) {
                source.save(output.toFile(),org.apache.pdfbox.pdfwriter.compress.CompressParameters.DEFAULT_COMPRESSION);
            } else if (options.operation().equals("pdf.protect")) {
                if(documentPassword==null)throw new IOException("Document password unavailable");
                var permission=new org.apache.pdfbox.pdmodel.encryption.AccessPermission();
                var ownerBytes=new byte[32];new java.security.SecureRandom().nextBytes(ownerBytes);
                var policy=new org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy(java.util.HexFormat.of().formatHex(ownerBytes),suppliedPassword,permission);
                java.util.Arrays.fill(ownerBytes,(byte)0);policy.setEncryptionKeyLength(256);policy.setPreferAES(true);source.protect(policy);source.save(output.toFile());
            } else if (options.operation().equals("pdf.unlock")) {
                if(documentPassword==null)throw new IOException("Document password unavailable");
                source.setAllSecurityToBeRemoved(true);source.save(output.toFile());
            } else {
                try (var result = new PDDocument()) {
                    if(options.operation().equals("pdf.insert_blank")) {
                        for(int page=1;page<=source.getNumberOfPages()+1;page++) {
                            if(page==options.insertAt()) for(int copy=0;copy<options.copies();copy++)
                                result.addPage(new PDPage(options.pageSize().equals("LETTER")?PDRectangle.LETTER:PDRectangle.A4));
                            if(page<=source.getNumberOfPages()) importPage(result,source.getPage(page-1));
                        }
                    } else if(options.operation().equals("pdf.duplicate_pages")) {
                        var selected=new HashSet<>(pages);
                        for(int page=1;page<=source.getNumberOfPages();page++) {
                            importPage(result,source.getPage(page-1));
                            if(selected.contains(page)) for(int copy=0;copy<options.copies();copy++) importPage(result,source.getPage(page-1));
                        }
                    } else {
                        for(int page:pages) importPage(result,source.getPage(page-1));
                    }
                    result.save(output.toFile());
                }
            }
        }
        checkSize(output);
    }

    private static void checkSize(Path output) throws IOException {
        if(Files.size(output)>100L*1024*1024) { Files.deleteIfExists(output);throw new IOException("Output limit exceeded"); }
    }

    private static void imagesToPdf(PdfOptions options,List<Path> inputs,Path output) throws IOException {
        try(var document=new PDDocument()) {
            for(Path input:inputs) {
                var image=ImageIO.read(input.toFile());
                if(image==null||image.getWidth()<1||image.getHeight()<1||(long)image.getWidth()*image.getHeight()>40_000_000L)
                    throw new IOException("Invalid or oversized image");
                PDRectangle base=options.pageSize().equals("LETTER")?PDRectangle.LETTER:PDRectangle.A4;
                PDRectangle box=image.getWidth()>image.getHeight()?new PDRectangle(base.getHeight(),base.getWidth()):base;
                var page=new PDPage(box);document.addPage(page);
                var pdfImage=LosslessFactory.createFromImage(document,image);
                float margin=24,scale=Math.min((box.getWidth()-2*margin)/image.getWidth(),(box.getHeight()-2*margin)/image.getHeight());
                float width=image.getWidth()*scale,height=image.getHeight()*scale;
                try(var content=new PDPageContentStream(document,page)) {
                    content.drawImage(pdfImage,(box.getWidth()-width)/2,(box.getHeight()-height)/2,width,height);
                }
                image.flush();
            }
            document.save(output.toFile());
        }
    }

    private static void textToPdf(PdfOptions options,List<Path> inputs,Path output) throws IOException {
        if(inputs.size()!=1||Files.size(inputs.get(0))>1024L*1024) throw new IOException("One text file up to 1 MiB is required");
        String text;
        try {
            var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
            text=decoder.decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(inputs.get(0)))).toString().replace("\r\n","\n").replace('\r','\n').replace("\t","    ");
        } catch(java.nio.charset.CharacterCodingException invalid) { throw new IOException("Invalid UTF-8 text",invalid); }
        try(var document=new PDDocument()) {
            var font=new PDType1Font(Standard14Fonts.FontName.HELVETICA);PDRectangle box=options.pageSize().equals("LETTER")?PDRectangle.LETTER:PDRectangle.A4;
            float margin=50,lineHeight=options.fontSize()*1.4f,maxWidth=box.getWidth()-2*margin,y=-1;int pageCount=0;
            PDPageContentStream content=null;
            try {
                for(String paragraph:text.split("\n",-1)) for(String line:wrap(paragraph,font,options.fontSize(),maxWidth)) {
                    if(content==null||y<margin) {
                        if(content!=null)content.close();if(++pageCount>100)throw new IOException("Text output exceeds 100 pages");
                        var page=new PDPage(box);document.addPage(page);content=new PDPageContentStream(document,page);y=box.getHeight()-margin-options.fontSize();
                    }
                    content.beginText();content.setFont(font,options.fontSize());content.newLineAtOffset(margin,y);content.showText(line);content.endText();y-=lineHeight;
                }
            } catch(IllegalArgumentException unsupported) { throw new IOException("Text contains characters unavailable in the built-in PDF font",unsupported); }
            finally { if(content!=null)content.close(); }
            if(document.getNumberOfPages()==0)document.addPage(new PDPage(box));document.save(output.toFile());
        }
    }

    private static void markupToPdf(PdfOptions options,List<Path> inputs,Path output) throws IOException {
        if(inputs.size()!=1||Files.size(inputs.get(0))>1024L*1024) throw new IOException("One text file up to 1 MiB is required");
        String source=readUtf8(inputs.get(0));
        String html=source;
        if(options.operation().equals("convert.markdown_to_pdf")) {
            var controls=new com.vladsch.flexmark.util.data.MutableDataSet();controls.set(com.vladsch.flexmark.html.HtmlRenderer.SUPPRESS_HTML,true);
            var parser=com.vladsch.flexmark.parser.Parser.builder(controls).build();
            html=com.vladsch.flexmark.html.HtmlRenderer.builder(controls).build().render(parser.parse(source));
        }
        var allowed=new org.jsoup.safety.Safelist().addTags("p","br","h1","h2","h3","h4","h5","h6","ul","ol","li","blockquote","pre","code","strong","b","em","i","del","hr","table","thead","tbody","tr","th","td","a")
            .addAttributes("ol","start");
        var settings=new org.jsoup.nodes.Document.OutputSettings().syntax(org.jsoup.nodes.Document.OutputSettings.Syntax.xml)
            .escapeMode(org.jsoup.nodes.Entities.EscapeMode.xhtml).prettyPrint(false);
        String safe=org.jsoup.Jsoup.clean(html,"",allowed,settings);
        var document=org.jsoup.nodes.Document.createShell("");document.outputSettings(settings);document.body().html(safe);
        String paper=options.pageSize().equals("LETTER")?"Letter":"A4";
        document.head().appendElement("style").appendText("@page{size:"+paper+";margin:18mm} body{font-family:sans-serif;font-size:"+options.fontSize()+"pt;line-height:1.4;color:#172033} h1{font-size:2em}h2{font-size:1.55em}h3{font-size:1.25em} pre,code{font-family:monospace}pre{white-space:pre-wrap;background:#f1f5f9;padding:8pt} blockquote{border-left:3pt solid #64748b;padding-left:10pt;color:#475569} table{border-collapse:collapse;width:100%}th,td{border:0.5pt solid #94a3b8;padding:4pt} a{color:#1d4ed8} img,svg,iframe,object,embed,form,input,script{display:none}");
        try(OutputStream stream=Files.newOutputStream(output)) {
            var builder=new com.openhtmltopdf.pdfboxout.PdfRendererBuilder();builder.useFastMode();builder.withHtmlContent(document.outerHtml(),null);builder.toStream(stream);builder.run();
        } catch(Exception invalid) { throw new IOException("Document markup could not be rendered",invalid); }
        try(var pdf=Loader.loadPDF(output.toFile())) { if(pdf.getNumberOfPages()>100) { Files.deleteIfExists(output);throw new IOException("Markup output exceeds 100 pages"); } }
    }

    private static String readUtf8(Path input) throws IOException {
        try {
            var decoder=StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
            return decoder.decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(input))).toString();
        } catch(java.nio.charset.CharacterCodingException invalid) { throw new IOException("Invalid UTF-8 text",invalid); }
    }
    private static List<String> wrap(String line,PDType1Font font,int size,float maxWidth) throws IOException {
        if(line.isEmpty())return List.of("");var result=new ArrayList<String>();var current=new StringBuilder();
        for(int offset=0;offset<line.length();) {
            int codePoint=line.codePointAt(offset);String value=new String(Character.toChars(codePoint));offset+=Character.charCount(codePoint);
            String candidate=current+value;
            if(!current.isEmpty()&&font.getStringWidth(candidate)/1000f*size>maxWidth) { result.add(current.toString());current.setLength(0); }
            current.append(value);
        }
        result.add(current.toString());return result;
    }

    private static void watermarkImage(PdfOptions options,List<Path> inputs,Path output) throws IOException {
        if(inputs.size()!=2) throw new IllegalArgumentException("A PDF and one watermark image are required");
        try(var document=Loader.loadPDF(inputs.get(0).toFile())) {
            if(document.isEncrypted()) throw new IOException("Encrypted PDFs are unsupported");
            var image=ImageIO.read(inputs.get(1).toFile());
            if(image==null||image.getWidth()<1||image.getHeight()<1||(long)image.getWidth()*image.getHeight()>40_000_000L)
                throw new IOException("Invalid or oversized watermark image");
            var pdfImage=LosslessFactory.createFromImage(document,image);image.flush();
            for(int selected:options.select(document.getNumberOfPages())) {
                var page=document.getPage(selected-1);var box=page.getCropBox();float margin=24;
                float maxWidth=box.getWidth()*options.fontSize()/100f,maxHeight=box.getHeight()*options.fontSize()/100f;
                float scale=Math.min(maxWidth/pdfImage.getWidth(),maxHeight/pdfImage.getHeight());
                float width=pdfImage.getWidth()*scale,height=pdfImage.getHeight()*scale;
                float x=switch(options.position()) { case "TOP_LEFT","BOTTOM_LEFT" -> box.getLowerLeftX()+margin;case "TOP_RIGHT","BOTTOM_RIGHT" -> box.getUpperRightX()-margin-width;default -> box.getLowerLeftX()+(box.getWidth()-width)/2; };
                float y=switch(options.position()) { case "TOP_LEFT","TOP_RIGHT" -> box.getUpperRightY()-margin-height;case "BOTTOM_LEFT","BOTTOM_RIGHT" -> box.getLowerLeftY()+margin;default -> box.getLowerLeftY()+(box.getHeight()-height)/2; };
                var alpha=new PDExtendedGraphicsState();alpha.setNonStrokingAlphaConstant((float)options.opacity());
                try(var content=new PDPageContentStream(document,page,PDPageContentStream.AppendMode.APPEND,true,true)) {
                    content.saveGraphicsState();content.setGraphicsStateParameters(alpha);content.drawImage(pdfImage,x,y,width,height);content.restoreGraphicsState();
                }
            }
            document.save(output.toFile());
        }
    }

    private static void split(PDDocument source,PdfOptions options,Path output) throws IOException {
        try(var zip=new ZipOutputStream(Files.newOutputStream(output))) {
            int part=1;
            for(var group:options.groups(source.getNumberOfPages())) {
                zip.putNextEntry(new ZipEntry(String.format(Locale.ROOT,"part-%02d.pdf",part++)));
                try(var result=new PDDocument()) { for(int page:group) importPage(result,source.getPage(page-1));result.save(zip); }
                zip.closeEntry();
            }
        }
    }

    private static void render(PDDocument source,List<Integer> pages,int dpi,Path output) throws IOException {
        var renderer=new PDFRenderer(source);
        try(var zip=new ZipOutputStream(Files.newOutputStream(output))) {
            for(int page:pages) {
                zip.putNextEntry(new ZipEntry(String.format(Locale.ROOT,"page-%04d.png",page)));
                var image=renderer.renderImageWithDPI(page-1,dpi,ImageType.RGB);
                if(!ImageIO.write(image,"PNG",zip)) throw new IOException("PNG encoder unavailable");
                image.flush();zip.closeEntry();
            }
        }
    }

    private static void extractText(PDDocument source,List<Integer> pages,Path output) throws IOException {
        var text=new StringBuilder();var stripper=new org.apache.pdfbox.text.PDFTextStripper();
        for(int page:pages) {
            stripper.setStartPage(page);stripper.setEndPage(page);
            if(!text.isEmpty()) text.append(System.lineSeparator());text.append(stripper.getText(source));
        }
        Files.writeString(output,text.toString(),StandardCharsets.UTF_8);
    }

    private static void writeMetadata(PDDocument source,Path output) throws IOException {
        var info=source.getDocumentInformation();
        String json="{\n  \"title\": "+json(info.getTitle())+",\n  \"author\": "+json(info.getAuthor())+
            ",\n  \"subject\": "+json(info.getSubject())+",\n  \"keywords\": "+json(info.getKeywords())+"\n}\n";
        Files.writeString(output,json,StandardCharsets.UTF_8);
    }
    private static String json(String value) {
        if(value==null)return "null";return "\""+value.replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n").replace("\t","\\t")+"\"";
    }

    private static void importPage(PDDocument target,PDPage original) throws IOException {
        var imported=target.importPage(original);
        imported.setResources(original.getResources());imported.setMediaBox(original.getMediaBox());
        imported.setCropBox(original.getCropBox());imported.setRotation(original.getRotation());
    }

    private static void addPageNumbers(PDDocument document,List<Integer> pages,PdfOptions options) throws IOException {
        var font=new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        for(int index=0;index<pages.size();index++) {
            var page=document.getPage(pages.get(index)-1);String value=Integer.toString(options.startNumber()+index);
            var box=page.getCropBox();float width=font.getStringWidth(value)/1000f*options.fontSize();
            float margin=28,x=switch(options.position()) {
                case "TOP_LEFT","BOTTOM_LEFT" -> box.getLowerLeftX()+margin;
                case "TOP_RIGHT","BOTTOM_RIGHT" -> box.getUpperRightX()-margin-width;
                default -> box.getLowerLeftX()+(box.getWidth()-width)/2;
            };
            float y=options.position().startsWith("TOP")?box.getUpperRightY()-margin-options.fontSize():box.getLowerLeftY()+margin;
            try(var content=new PDPageContentStream(document,page,PDPageContentStream.AppendMode.APPEND,true,true)) {
                content.beginText();content.setFont(font,options.fontSize());content.setNonStrokingColor(java.awt.Color.DARK_GRAY);
                content.newLineAtOffset(x,y);content.showText(value);content.endText();
            }
        }
    }

    private static void addWatermark(PDDocument document,List<Integer> pages,PdfOptions options) throws IOException {
        var font=new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        for(int selected:pages) {
            var page=document.getPage(selected-1);var box=page.getCropBox();
            float width=font.getStringWidth(options.text())/1000f*options.fontSize();
            float centerX=box.getLowerLeftX()+box.getWidth()/2,centerY=box.getLowerLeftY()+box.getHeight()/2;
            var alpha=new PDExtendedGraphicsState();alpha.setNonStrokingAlphaConstant((float)options.opacity());
            try(var content=new PDPageContentStream(document,page,PDPageContentStream.AppendMode.APPEND,true,true)) {
                content.saveGraphicsState();content.setGraphicsStateParameters(alpha);content.setNonStrokingColor(java.awt.Color.GRAY);
                content.transform(Matrix.getTranslateInstance(centerX,centerY));
                content.transform(Matrix.getRotateInstance(Math.toRadians(options.angle()),0,0));
                content.beginText();content.setFont(font,options.fontSize());content.newLineAtOffset(-width/2,-options.fontSize()/2f);
                content.showText(options.text());content.endText();content.restoreGraphicsState();
            }
        }
    }
}
