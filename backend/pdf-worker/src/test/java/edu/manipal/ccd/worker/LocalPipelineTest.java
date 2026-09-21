package edu.manipal.ccd.worker;
import edu.manipal.ccd.local.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import com.fasterxml.jackson.databind.*;
import org.apache.pdfbox.*;
import org.apache.pdfbox.pdmodel.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="E2E_URL",matches=".+")
class LocalPipelineTest {
    static final ObjectMapper JSON=new ObjectMapper();
    String base=System.getenv("E2E_URL");
    HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL))
        .connectTimeout(Duration.ofSeconds(10)).build();
    static byte[] pdf(boolean a4) throws Exception {
        try(var document=new PDDocument();var output=new ByteArrayOutputStream()) {
            document.addPage(new PDPage(a4?org.apache.pdfbox.pdmodel.common.PDRectangle.A4:org.apache.pdfbox.pdmodel.common.PDRectangle.LETTER));
            try(var content=new PDPageContentStream(document,document.getPage(0))) {
                content.beginText();content.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),18);
                content.newLineAtOffset(50,700);content.showText(a4?"DocFlow - Document A":"DocFlow - Document B");content.endText();
            }
            document.save(output);return output.toByteArray();
        }
    }
    static byte[] png(int width,int height) throws Exception {
        var image=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics=image.createGraphics();graphics.setColor(java.awt.Color.WHITE);graphics.fillRect(0,0,width,height);
        graphics.setColor(java.awt.Color.BLUE);graphics.drawString("DocFlow",5,Math.min(height-5,20));graphics.dispose();
        try(var output=new ByteArrayOutputStream()) { javax.imageio.ImageIO.write(image,"PNG",output);return output.toByteArray(); }
    }
    static byte[] docx() throws Exception {
        var bytes=new ByteArrayOutputStream();
        try(var zip=new java.util.zip.ZipOutputStream(bytes)) {
            var entries=Map.of(
                "[Content_Types].xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>",
                "_rels/.rels","<?xml version=\"1.0\" encoding=\"UTF-8\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>",
                "word/document.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>DocFlow Office conversion</w:t></w:r></w:p><w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/></w:sectPr></w:body></w:document>");
            for(var entry:entries.entrySet()){zip.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
        }
        return bytes.toByteArray();
    }
    static byte[] odt() throws Exception {
        var bytes=new ByteArrayOutputStream();
        try(var zip=new java.util.zip.ZipOutputStream(bytes)) {
            byte[] mime="application/vnd.oasis.opendocument.text".getBytes(StandardCharsets.US_ASCII);
            var mimeEntry=new java.util.zip.ZipEntry("mimetype");mimeEntry.setMethod(java.util.zip.ZipEntry.STORED);mimeEntry.setSize(mime.length);mimeEntry.setCompressedSize(mime.length);var crc=new java.util.zip.CRC32();crc.update(mime);mimeEntry.setCrc(crc.getValue());zip.putNextEntry(mimeEntry);zip.write(mime);zip.closeEntry();
            var entries=Map.of(
                "META-INF/manifest.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><manifest:manifest xmlns:manifest=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\" manifest:version=\"1.2\"><manifest:file-entry manifest:full-path=\"/\" manifest:media-type=\"application/vnd.oasis.opendocument.text\"/><manifest:file-entry manifest:full-path=\"content.xml\" manifest:media-type=\"text/xml\"/></manifest:manifest>",
                "content.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><office:document-content xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\" office:version=\"1.2\"><office:body><office:text><text:p>DocFlow OpenDocument conversion</text:p></office:text></office:body></office:document-content>");
            for(var entry:entries.entrySet()){zip.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
        } return bytes.toByteArray();
    }
    static byte[] ods() throws Exception {
        var bytes=new ByteArrayOutputStream();
        try(var zip=new java.util.zip.ZipOutputStream(bytes)) {
            byte[] mime="application/vnd.oasis.opendocument.spreadsheet".getBytes(StandardCharsets.US_ASCII);
            var mimeEntry=new java.util.zip.ZipEntry("mimetype");mimeEntry.setMethod(java.util.zip.ZipEntry.STORED);mimeEntry.setSize(mime.length);mimeEntry.setCompressedSize(mime.length);var crc=new java.util.zip.CRC32();crc.update(mime);mimeEntry.setCrc(crc.getValue());zip.putNextEntry(mimeEntry);zip.write(mime);zip.closeEntry();
            var entries=Map.of(
                "META-INF/manifest.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><manifest:manifest xmlns:manifest=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\" manifest:version=\"1.2\"><manifest:file-entry manifest:full-path=\"/\" manifest:media-type=\"application/vnd.oasis.opendocument.spreadsheet\"/><manifest:file-entry manifest:full-path=\"content.xml\" manifest:media-type=\"text/xml\"/></manifest:manifest>",
                "content.xml","<?xml version=\"1.0\" encoding=\"UTF-8\"?><office:document-content xmlns:office=\"urn:oasis:names:tc:opendocument:xmlns:office:1.0\" xmlns:table=\"urn:oasis:names:tc:opendocument:xmlns:table:1.0\" xmlns:text=\"urn:oasis:names:tc:opendocument:xmlns:text:1.0\" office:version=\"1.2\"><office:body><office:spreadsheet><table:table table:name=\"Sheet1\"><table:table-row><table:table-cell office:value-type=\"string\"><text:p>DocFlow Spreadsheet conversion</text:p></table:table-cell></table:table-row></table:table></office:spreadsheet></office:body></office:document-content>");
            for(var entry:entries.entrySet()){zip.putNextEntry(new java.util.zip.ZipEntry(entry.getKey()));zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}
        } return bytes.toByteArray();
    }
    @BeforeAll static void demoFixtures() throws Exception {
        var directory=java.nio.file.Path.of("target","demo-inputs");java.nio.file.Files.createDirectories(directory);
        java.nio.file.Files.write(directory.resolve("document-a.pdf"),pdf(true));
        java.nio.file.Files.write(directory.resolve("document-b.pdf"),pdf(false));
    }
    HttpResponse<byte[]> submit(byte[] first,byte[] second,boolean csrf) throws Exception {
        return submit(List.of(first,second),Map.of(),csrf);
    }
    HttpResponse<byte[]> submit(List<byte[]> files,Map<String,String> options,boolean csrf) throws Exception {
        return submit(client,files,options,csrf);
    }
    HttpResponse<byte[]> submit(HttpClient caller,List<byte[]> files,Map<String,String> options,boolean csrf) throws Exception {
        String boundary="fixture-"+UUID.randomUUID();var bytes=new ByteArrayOutputStream();
        for(var file:files) {
            bytes.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"files\"; filename=\"test.pdf\"\r\nContent-Type: application/pdf\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            bytes.write(file);bytes.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        for(var option:options.entrySet()) bytes.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+option.getKey()+"\"\r\n\r\n"+option.getValue()+"\r\n").getBytes(StandardCharsets.UTF_8));
        bytes.write(("--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
        var request=HttpRequest.newBuilder(URI.create(base+"/api/v1/jobs")).timeout(Duration.ofSeconds(30))
            .header("Content-Type","multipart/form-data; boundary="+boundary).POST(HttpRequest.BodyPublishers.ofByteArray(bytes.toByteArray()));
        if(csrf) request.header("X-Requested-With","docflow");
        return caller.send(request.build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    HttpResponse<byte[]> get(HttpClient caller,String path) throws Exception {
        return caller.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(15)).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    HttpResponse<byte[]> post(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(15))
            .header("X-Requested-With","docflow").POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    HttpResponse<byte[]> json(HttpClient caller,String path,String body) throws Exception {
        return caller.send(HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(20))
            .header("Content-Type","application/json").header("X-Requested-With","docflow")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofByteArray());
    }
    JsonNode await(String id,String expected) throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(75).toNanos();
        while(System.nanoTime()<deadline) {
            var response=get(client,"/api/v1/jobs/"+id);assertEquals(200,response.statusCode());
            var job=JSON.readTree(response.body());
            if(job.path("status").asText().equals(expected)) return job;
            if(job.path("status").asText().equals("FAILED") && !expected.equals("FAILED")) fail(job.toString());
            Thread.sleep(500);
        }
        throw new AssertionError("Job did not reach "+expected);
    }
    @Test void endToEndMergeIsolationAndValidation() throws Exception {
        assertEquals(403,submit(pdf(true),pdf(false),false).statusCode());
        assertEquals(400,submit("not PDF".getBytes(),pdf(false),true).statusCode());
        var accepted=submit(pdf(true),pdf(false),true);
        assertEquals(202,accepted.statusCode(),new String(accepted.body()));
        var id=JSON.readTree(accepted.body()).path("id").asText();
        assertTrue(accepted.headers().firstValue("set-cookie").orElse("").contains("HttpOnly"));
        assertEquals(404,get(HttpClient.newHttpClient(),"/api/v1/jobs/"+id).statusCode());
        assertEquals(404,get(HttpClient.newHttpClient(),"/api/v1/jobs/"+id+"/download").statusCode());
        await(id,"COMPLETED");
        var outsider=HttpClient.newHttpClient();
        var otherGuest=HttpRequest.newBuilder(URI.create(base+"/api/v1/jobs/"+id))
            .header("Cookie","docflow_guest="+"a".repeat(64)).build();
        assertEquals(404,outsider.send(otherGuest,HttpResponse.BodyHandlers.ofByteArray()).statusCode());
        var download=get(client,"/api/v1/jobs/"+id+"/download");assertEquals(200,download.statusCode());
        try(var merged=Loader.loadPDF(download.body())) {
            assertEquals(2,merged.getNumberOfPages());
            assertEquals(org.apache.pdfbox.pdmodel.common.PDRectangle.A4.getWidth(),merged.getPage(0).getMediaBox().getWidth());
            assertEquals(org.apache.pdfbox.pdmodel.common.PDRectangle.LETTER.getWidth(),merged.getPage(1).getMediaBox().getWidth());
        }
        // Replay the original message twice: terminal work must not run again or change its output.
        var db=new JdbcTemplate(new DriverManagerDataSource(System.getenv("DB_URL"),System.getenv("DB_USER"),System.getenv("DB_PASSWORD")));
        var record=db.queryForMap("SELECT owner_key,dispatch_id,output_key FROM merge_jobs WHERE id=?",UUID.fromString(id));
        var storage=new Storage(System.getenv("STORAGE_CONNECTION"));
        var replay=JSON.writeValueAsString(Map.of("schemaVersion",1,"jobId",id,
            "ownerKey",record.get("owner_key"),"dispatchId",record.get("dispatch_id").toString()));
        storage.queue.sendMessage(replay);storage.queue.sendMessage(replay);
        Thread.sleep(2500);
        var unchanged=db.queryForMap("SELECT attempts,output_key FROM merge_jobs WHERE id=?",UUID.fromString(id));
        assertEquals(1,((Number)unchanged.get("attempts")).intValue());
        assertEquals(record.get("output_key"),unchanged.get("output_key"));
        var bad=submit("%PDF-malformed".getBytes(),pdf(false),true);assertEquals(202,bad.statusCode());
        var failed=await(JSON.readTree(bad.body()).path("id").asText(),"FAILED");
        assertEquals("INVALID_OR_UNSUPPORTED_PDF",failed.path("error").asText());
        assertEquals(1,failed.path("attempts").asInt());
    }
    @Test void allPageOperationsRoundTripThroughQueue() throws Exception {
        var merge=submit(pdf(true),pdf(false),true);assertEquals(202,merge.statusCode());
        String mergedId=JSON.readTree(merge.body()).path("id").asText();await(mergedId,"COMPLETED");
        byte[] input=get(client,"/api/v1/jobs/"+mergedId+"/download").body();
        java.nio.file.Files.write(java.nio.file.Path.of("target","demo-inputs","two-pages.pdf"),input);
        for(String operation:List.of("pdf.extract","pdf.rotate","pdf.reorder","pdf.delete","pdf.duplicate_pages","pdf.insert_blank","pdf.page_numbers","pdf.watermark_text","pdf.metadata_update","pdf.metadata_remove")) {
            var options=new HashMap<String,String>();options.put("operation",operation);
            if(!operation.equals("pdf.insert_blank")&&!operation.equals("pdf.page_numbers")&&!operation.startsWith("pdf.metadata_")) options.put("pages",operation.equals("pdf.reorder")?"2,1":operation.equals("pdf.watermark_text")?"1":"2");
            if(operation.equals("pdf.rotate")) options.put("angle","90");
            if(operation.equals("pdf.duplicate_pages")) options.put("copies","2");
            if(operation.equals("pdf.insert_blank")) { options.put("insertAt","2");options.put("copies","2");options.put("pageSize","LETTER"); }
            if(operation.equals("pdf.page_numbers")) { options.put("startNumber","10");options.put("fontSize","12");options.put("position","BOTTOM_CENTER"); }
            if(operation.equals("pdf.watermark_text")) { options.put("text","QUEUE WATERMARK");options.put("fontSize","48");options.put("angle","45");options.put("opacity","0.3"); }
            if(operation.equals("pdf.metadata_update")) { options.put("title","DocFlow Cloud");options.put("author","Sample Author");options.put("keywords","documents,pdf"); }
            var accepted=submit(List.of(input),options,true);assertEquals(202,accepted.statusCode(),new String(accepted.body()));
            String id=JSON.readTree(accepted.body()).path("id").asText();
            var completed=await(id,"COMPLETED");assertEquals(operation,completed.path("operation").asText());
            assertEquals(1,completed.path("attempts").asInt());
            var response=get(client,"/api/v1/jobs/"+id+"/download");assertEquals(200,response.statusCode());
            try(var result=Loader.loadPDF(response.body())) {
                int expected=operation.equals("pdf.duplicate_pages")||operation.equals("pdf.insert_blank")?4:
                    operation.equals("pdf.rotate")||operation.equals("pdf.reorder")||operation.equals("pdf.page_numbers")||operation.equals("pdf.watermark_text")||operation.startsWith("pdf.metadata_")?2:1;
                assertEquals(expected,result.getNumberOfPages());
                var text=new org.apache.pdfbox.text.PDFTextStripper().getText(result);
                if(operation.equals("pdf.extract")) { assertTrue(text.contains("Document B"));assertFalse(text.contains("Document A")); }
                if(operation.equals("pdf.delete")) { assertTrue(text.contains("Document A"));assertFalse(text.contains("Document B")); }
                if(operation.equals("pdf.reorder")) assertTrue(text.indexOf("Document B")<text.indexOf("Document A"));
                if(operation.equals("pdf.rotate")) { assertEquals(0,result.getPage(0).getRotation());assertEquals(90,result.getPage(1).getRotation()); }
                if(operation.equals("pdf.duplicate_pages")) assertEquals(3,text.split("Document B",-1).length-1);
                if(operation.equals("pdf.insert_blank")) { assertTrue(text.contains("Document A"));assertTrue(text.contains("Document B")); }
                if(operation.equals("pdf.page_numbers")) { assertTrue(text.contains("10"));assertTrue(text.contains("11")); }
                if(operation.equals("pdf.watermark_text")) {
                    String compacted=text.replaceAll("\\s+","");assertTrue(compacted.contains("QUEUEWATERMARK"),text);
                    assertEquals(1,compacted.split("QUEUEWATERMARK",-1).length-1);
                }
                if(operation.equals("pdf.metadata_update")) { assertEquals("DocFlow Cloud",result.getDocumentInformation().getTitle());assertEquals("Sample Author",result.getDocumentInformation().getAuthor()); }
                if(operation.equals("pdf.metadata_remove")) { assertNull(result.getDocumentInformation().getTitle());assertNull(result.getDocumentInformation().getAuthor()); }
            }
        }
        assertEquals(400,submit(List.of(input),Map.of("operation","pdf.extract","pages","1,1"),true).statusCode());
        assertEquals(400,submit(List.of(input),Map.of("operation","pdf.rotate","angle","45"),true).statusCode());
        assertEquals(400,submit(List.of(input,input),Map.of("operation","pdf.extract","pages","1"),true).statusCode());
        for(var options:List.of(Map.of("operation","pdf.extract","pages","3"),Map.of("operation","pdf.reorder","pages","1"),Map.of("operation","pdf.delete","pages","1-2"))) {
            var accepted=submit(List.of(input),options,true);assertEquals(202,accepted.statusCode());
            String id=JSON.readTree(accepted.body()).path("id").asText();var failed=await(id,"FAILED");
            assertEquals("INVALID_PAGE_OPTIONS",failed.path("error").asText());assertEquals(1,failed.path("attempts").asInt());
            assertEquals(409,get(client,"/api/v1/jobs/"+id+"/download").statusCode());
        }
    }
    @Test void typedResultsAndImageConversionRoundTripThroughQueue() throws Exception {
        var merge=submit(pdf(true),pdf(false),true);assertEquals(202,merge.statusCode());
        String mergedId=JSON.readTree(merge.body()).path("id").asText();await(mergedId,"COMPLETED");
        byte[] input=get(client,"/api/v1/jobs/"+mergedId+"/download").body();
        for(var item:List.of(
            Map.of("operation","pdf.split","pages","1,2"),
            Map.of("operation","pdf.render_images","pages","1","dpi","72"),
            Map.of("operation","pdf.extract_text","pages","1-2"),
            Map.of("operation","pdf.metadata_read"))) {
            String operation=item.get("operation");var accepted=submit(List.of(input),item,true);
            assertEquals(202,accepted.statusCode(),new String(accepted.body()));String id=JSON.readTree(accepted.body()).path("id").asText();
            await(id,"COMPLETED");var result=get(client,"/api/v1/jobs/"+id+"/download");assertEquals(200,result.statusCode());
            String contentType=result.headers().firstValue("content-type").orElse("");
            if(operation.equals("pdf.split")||operation.equals("pdf.render_images")) {
                assertTrue(contentType.startsWith("application/zip"));int entries=0;
                try(var zip=new java.util.zip.ZipInputStream(new ByteArrayInputStream(result.body()))) { while(zip.getNextEntry()!=null) entries++; }
                assertEquals(operation.equals("pdf.split")?2:1,entries);
            } else if(operation.equals("pdf.extract_text")) {
                assertTrue(contentType.startsWith("text/plain"));assertTrue(new String(result.body(),StandardCharsets.UTF_8).contains("Document A"));
            } else {
                assertTrue(contentType.startsWith("application/json"));assertTrue(JSON.readTree(result.body()).has("title"));
            }
        }
        var converted=submit(List.of(png(40,80),png(80,40)),Map.of("operation","convert.image_to_pdf","pageSize","A4"),true);
        assertEquals(202,converted.statusCode(),new String(converted.body()));String convertedId=JSON.readTree(converted.body()).path("id").asText();
        await(convertedId,"COMPLETED");var result=get(client,"/api/v1/jobs/"+convertedId+"/download");
        assertTrue(result.headers().firstValue("content-type").orElse("").startsWith("application/pdf"));
        try(var pdf=Loader.loadPDF(result.body())) { assertEquals(2,pdf.getNumberOfPages());assertTrue(pdf.getPage(0).getMediaBox().getHeight()>pdf.getPage(0).getMediaBox().getWidth());assertTrue(pdf.getPage(1).getMediaBox().getWidth()>pdf.getPage(1).getMediaBox().getHeight()); }
        for(var request:List.of(
            Map.entry(List.of(input,png(80,40)),Map.of("operation","pdf.watermark_image","pages","1","scalePercent","25","opacity","0.4","position","CENTER")),
            Map.entry(List.of(input),Map.of("operation","pdf.optimize")),
            Map.entry(List.of(input),Map.of("operation","pdf.compress","quality","EBOOK")))) {
            var accepted=submit(request.getKey(),request.getValue(),true);assertEquals(202,accepted.statusCode(),new String(accepted.body()));
            String id=JSON.readTree(accepted.body()).path("id").asText();await(id,"COMPLETED");var download=get(client,"/api/v1/jobs/"+id+"/download");
            assertTrue(download.headers().firstValue("content-type").orElse("").startsWith("application/pdf"));
            try(var pdf=Loader.loadPDF(download.body())) { assertEquals(2,pdf.getNumberOfPages()); }
        }
        byte[] notes="DocFlow UTF-8 text conversion\nSecond line".getBytes(StandardCharsets.UTF_8);
        var textJob=submit(List.of(notes),Map.of("operation","convert.text_to_pdf","pageSize","A4","fontSize","11"),true);
        assertEquals(202,textJob.statusCode(),new String(textJob.body()));String textId=JSON.readTree(textJob.body()).path("id").asText();await(textId,"COMPLETED");
        try(var pdf=Loader.loadPDF(get(client,"/api/v1/jobs/"+textId+"/download").body())) { assertTrue(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf).contains("DocFlow UTF-8 text conversion")); }
        for(var conversion:List.of(
            Map.entry("convert.markdown_to_pdf","# Markdown heading\n\n- queue worker\n- private storage\n\n![blocked](https://invalid.example/x.png)"),
            Map.entry("convert.html_to_pdf","<h1>Restricted HTML</h1><p>Local rendering</p><img src='https://invalid.example/x.png'><script>danger()</script>"))) {
            var accepted=submit(List.of(conversion.getValue().getBytes(StandardCharsets.UTF_8)),Map.of("operation",conversion.getKey(),"pageSize","A4","fontSize","11"),true);
            assertEquals(202,accepted.statusCode(),new String(accepted.body()));String id=JSON.readTree(accepted.body()).path("id").asText();await(id,"COMPLETED");
            try(var pdf=Loader.loadPDF(get(client,"/api/v1/jobs/"+id+"/download").body())) {
                String rendered=new org.apache.pdfbox.text.PDFTextStripper().getText(pdf);assertFalse(rendered.contains("danger"));
                assertTrue(rendered.contains(conversion.getKey().contains("markdown")?"Markdown heading":"Restricted HTML"));
            }
        }
        var office=submit(List.of(docx()),Map.of("operation","convert.docx_to_pdf"),true);
        assertEquals(202,office.statusCode(),new String(office.body()));String officeId=JSON.readTree(office.body()).path("id").asText();await(officeId,"COMPLETED");
        try(var pdf=Loader.loadPDF(get(client,"/api/v1/jobs/"+officeId+"/download").body())) { assertTrue(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf).contains("DocFlow Office conversion")); }
        var openDocument=submit(List.of(odt()),Map.of("operation","convert.odt_to_pdf"),true);
        assertEquals(202,openDocument.statusCode(),new String(openDocument.body()));String odtId=JSON.readTree(openDocument.body()).path("id").asText();await(odtId,"COMPLETED");
        try(var pdf=Loader.loadPDF(get(client,"/api/v1/jobs/"+odtId+"/download").body())) { assertTrue(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf).contains("DocFlow OpenDocument conversion")); }
        var spreadsheet=submit(List.of(ods()),Map.of("operation","convert.ods_to_pdf"),true);
        assertEquals(202,spreadsheet.statusCode(),new String(spreadsheet.body()));String odsId=JSON.readTree(spreadsheet.body()).path("id").asText();await(odsId,"COMPLETED");
        try(var pdf=Loader.loadPDF(get(client,"/api/v1/jobs/"+odsId+"/download").body())) { assertTrue(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf).contains("DocFlow Spreadsheet conversion")); }
        assertEquals(400,submit(List.of("not a docx".getBytes(StandardCharsets.UTF_8)),Map.of("operation","convert.docx_to_pdf"),true).statusCode());
        assertEquals(400,submit(List.of("not an odt".getBytes(StandardCharsets.UTF_8)),Map.of("operation","convert.odt_to_pdf"),true).statusCode());
        assertEquals(400,submit(List.of("not an ods".getBytes(StandardCharsets.UTF_8)),Map.of("operation","convert.ods_to_pdf"),true).statusCode());
        assertEquals(400,submit(List.of(new byte[]{(byte)0xc3,0x28}),Map.of("operation","convert.text_to_pdf"),true).statusCode());
        assertEquals(400,submit(List.of("not-image".getBytes(StandardCharsets.UTF_8)),Map.of("operation","convert.image_to_pdf"),true).statusCode());
    }
    @Test void durableClaimsRetriesAndStaleWorkerFencing() throws Exception {
        // Transaction rollback keeps synthetic repository-only jobs invisible to the live dispatcher.
        var source=new DriverManagerDataSource(System.getenv("DB_URL"),System.getenv("DB_USER"),System.getenv("DB_PASSWORD"));
        var isolated=new JobStore(source);
        new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(source)).executeWithoutResult(tx->{
            var id=UUID.randomUUID();String owner="test-"+UUID.randomUUID();isolated.create(id,owner,2);
            var initial=isolated.find(id,owner).orElseThrow();
            assertTrue(isolated.pending().stream().anyMatch(j->j.id().equals(id)));
            var attempt=isolated.claim(id,owner,initial.dispatch()).orElseThrow();
            assertTrue(isolated.claim(id,owner,initial.dispatch()).isEmpty());
            assertTrue(isolated.find(id,"another-owner").isEmpty());
            isolated.fail(attempt,true,"TEST_TRANSIENT");
            assertFalse(isolated.complete(attempt,"stale.pdf"));
            assertFalse(isolated.pending().stream().anyMatch(j->j.id().equals(id)),"Retry must wait for backoff");
            for(int i=2;i<=3;i++) {
                new JdbcTemplate(source).update("UPDATE merge_jobs SET retry_at=now()-interval '1 second' WHERE id=?",id);
                isolated.pending();
                var queued=isolated.find(id,owner).orElseThrow();
                assertTrue(isolated.claim(id,owner,initial.dispatch()).isEmpty());
                var next=isolated.claim(id,owner,queued.dispatch()).orElseThrow();
                assertEquals(i,next.attempts());isolated.fail(next,true,"TEST_TRANSIENT");
            }
            assertEquals("FAILED",isolated.find(id,owner).orElseThrow().status());
            var recoverId=UUID.randomUUID();isolated.create(recoverId,owner,2);
            var queued=isolated.find(recoverId,owner).orElseThrow();
            var stale=isolated.claim(recoverId,owner,queued.dispatch()).orElseThrow();
            new JdbcTemplate(source).update("UPDATE merge_jobs SET lease_until=now()-interval '1 second' WHERE id=?",recoverId);
            assertFalse(isolated.renew(stale));
            assertFalse(isolated.complete(stale,"late.pdf"));
            isolated.recover();
            assertEquals("RETRYING",isolated.find(recoverId,owner).orElseThrow().status());
            new JdbcTemplate(source).update("UPDATE merge_jobs SET retry_at=now()-interval '1 second' WHERE id=?",recoverId);
            isolated.pending();
            var recovered=isolated.find(recoverId,owner).orElseThrow();
            assertEquals("QUEUED",recovered.status());
            assertNotEquals(stale.dispatch(),recovered.dispatch());
            assertTrue(isolated.pending().stream().anyMatch(j->j.id().equals(recoverId)));
            assertTrue(isolated.claim(recoverId,owner,recovered.dispatch()).isPresent());
            tx.setRollbackOnly();
        });
    }
    @Test void cancellationIsOwnerScopedFencesWorkAndDeletesFiles() throws Exception {
        var seed=submit(pdf(true),pdf(false),true);assertEquals(202,seed.statusCode());
        await(JSON.readTree(seed.body()).path("id").asText(),"COMPLETED");
        String token=((CookieManager)client.cookieHandler().orElseThrow()).getCookieStore().getCookies().stream()
            .filter(cookie->cookie.getName().equals("docflow_guest")).findFirst().orElseThrow().getValue();
        var digest=java.security.MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        String owner=java.util.HexFormat.of().formatHex(digest);UUID id=UUID.randomUUID();
        var jobs=new JobStore(System.getenv("DB_URL"),System.getenv("DB_USER"),System.getenv("DB_PASSWORD"));
        jobs.beginUpload(id,owner,1,new edu.manipal.ccd.core.PdfOptions("pdf.rotate","",90));
        var storage=new Storage(System.getenv("STORAGE_CONNECTION"));String key=id+"/inputs/0.pdf";
        storage.put(key,new ByteArrayInputStream(pdf(true)),pdf(true).length);
        try {
            var outsider=HttpClient.newHttpClient();
            var denied=HttpRequest.newBuilder(URI.create(base+"/api/v1/jobs/"+id+"/cancel"))
                .header("X-Requested-With","docflow").header("Cookie","docflow_guest="+"a".repeat(64))
                .POST(HttpRequest.BodyPublishers.noBody()).build();
            assertEquals(404,outsider.send(denied,HttpResponse.BodyHandlers.ofByteArray()).statusCode());
            var cancelled=post("/api/v1/jobs/"+id+"/cancel");assertEquals(200,cancelled.statusCode(),new String(cancelled.body()));
            assertEquals("CANCELLED",JSON.readTree(cancelled.body()).path("status").asText());
            assertFalse(storage.blobs.getBlobClient(key).exists());
            assertEquals(409,post("/api/v1/jobs/"+id+"/cancel").statusCode());
        } finally { storage.deletePrefix(id+"/"); }
    }
    @Test void optionalAccountsPersistHistoryAndEnforceAuthorization() throws Exception {
        String email="docflow-"+UUID.randomUUID()+"@example.test",password="SecureDemo12345";
        var firstCookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);var first=HttpClient.newBuilder().cookieHandler(firstCookies).build();
        var secondCookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);var second=HttpClient.newBuilder().cookieHandler(secondCookies).build();
        var outsiderCookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);var outsider=HttpClient.newBuilder().cookieHandler(outsiderCookies).build();
        String credentials=JSON.writeValueAsString(Map.of("email",email,"password",password));
        var registered=json(first,"/api/v1/auth/register",credentials);assertEquals(201,registered.statusCode(),new String(registered.body()));
        assertTrue(JSON.readTree(registered.body()).path("authenticated").asBoolean());
        assertEquals(409,json(second,"/api/v1/auth/register",credentials).statusCode());
        assertEquals(401,json(second,"/api/v1/auth/login",JSON.writeValueAsString(Map.of("email",email,"password","WrongPassword999"))).statusCode());
        var accepted=submit(first,List.of(pdf(true),pdf(false)),Map.of("operation","pdf.merge"),true);assertEquals(202,accepted.statusCode());
        String id=JSON.readTree(accepted.body()).path("id").asText();
        long deadline=System.nanoTime()+Duration.ofSeconds(30).toNanos();
        while(System.nanoTime()<deadline) { var state=get(first,"/api/v1/jobs/"+id);if(JSON.readTree(state.body()).path("status").asText().equals("COMPLETED"))break;Thread.sleep(300); }
        assertEquals(404,get(outsider,"/api/v1/jobs/"+id).statusCode());
        assertEquals(200,json(second,"/api/v1/auth/login",credentials).statusCode());
        assertEquals(200,get(second,"/api/v1/jobs/"+id).statusCode());
        assertTrue(new String(get(second,"/api/v1/jobs").body()).contains(id));
        var delete=second.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/jobs/"+id)).timeout(Duration.ofSeconds(15))
            .header("X-Requested-With","docflow").DELETE().build(),HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(204,delete.statusCode(),new String(delete.body()));assertEquals(404,get(first,"/api/v1/jobs/"+id).statusCode());
        assertEquals(200,json(second,"/api/v1/auth/logout","{}").statusCode());
        assertFalse(JSON.readTree(get(second,"/api/v1/auth/me").body()).path("authenticated").asBoolean());
        assertEquals(404,get(second,"/api/v1/jobs/"+id).statusCode());
    }
    @Test void documentPasswordsAreEncryptedTemporaryAndErased() throws Exception {
        String password="QueueDocumentSecret123";var protect=submit(List.of(pdf(true)),Map.of("operation","pdf.protect","documentPassword",password),true);
        assertEquals(202,protect.statusCode(),new String(protect.body()));String protectedId=JSON.readTree(protect.body()).path("id").asText();await(protectedId,"COMPLETED");
        byte[] protectedPdf=get(client,"/api/v1/jobs/"+protectedId+"/download").body();
        assertThrows(org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException.class,()->Loader.loadPDF(protectedPdf));
        try(var pdf=Loader.loadPDF(protectedPdf,password)) { assertTrue(pdf.isEncrypted());assertEquals(1,pdf.getNumberOfPages()); }
        var db=new JdbcTemplate(new DriverManagerDataSource(System.getenv("DB_URL"),System.getenv("DB_USER"),System.getenv("DB_PASSWORD")));
        assertEquals(0,db.queryForObject("SELECT count(*) FROM job_secrets WHERE job_id=?",Integer.class,UUID.fromString(protectedId)));
        assertEquals("",db.queryForObject("SELECT text_value FROM merge_jobs WHERE id=?",String.class,UUID.fromString(protectedId)));
        var unlock=submit(List.of(protectedPdf),Map.of("operation","pdf.unlock","documentPassword",password),true);assertEquals(202,unlock.statusCode());
        String unlockId=JSON.readTree(unlock.body()).path("id").asText();await(unlockId,"COMPLETED");
        try(var pdf=Loader.loadPDF(get(client,"/api/v1/jobs/"+unlockId+"/download").body())) { assertFalse(pdf.isEncrypted());assertEquals(1,pdf.getNumberOfPages()); }
        var wrong=submit(List.of(protectedPdf),Map.of("operation","pdf.unlock","documentPassword","WrongPassword"),true);assertEquals(202,wrong.statusCode());
        String wrongId=JSON.readTree(wrong.body()).path("id").asText();var failed=await(wrongId,"FAILED");assertEquals(1,failed.path("attempts").asInt());
        assertEquals(0,db.queryForObject("SELECT count(*) FROM job_secrets WHERE job_id=?",Integer.class,UUID.fromString(wrongId)));
        assertFalse(new String(get(client,"/api/v1/jobs").body(),StandardCharsets.UTF_8).contains(password));
    }
    @Test void workerStopsRetryingMissingInputsAfterThreeAttempts() throws Exception {
        var jobs=new JobStore(System.getenv("DB_URL"),System.getenv("DB_USER"),System.getenv("DB_PASSWORD"));
        var id=UUID.randomUUID();String owner="test-"+UUID.randomUUID();jobs.create(id,owner,2);
        long deadline=System.nanoTime()+Duration.ofSeconds(45).toNanos();
        while(System.nanoTime()<deadline) {
            var job=jobs.find(id,owner).orElseThrow();
            if(job.status().equals("FAILED")) { assertEquals(3,job.attempts());return; }
            Thread.sleep(500);
        }
        fail("Missing-input job did not exhaust its bounded retries");
    }
    @Test void uploadReservationsAndPartialExpiryAreRetryable() throws Exception {
        var source=new DriverManagerDataSource(System.getenv("DB_URL"),System.getenv("DB_USER"),System.getenv("DB_PASSWORD"));
        var id=UUID.randomUUID();String owner="test-"+UUID.randomUUID();
        var isolated=new JobStore(source) {
            @Override public List<Job> expired() { return super.expired().stream().filter(j->j.id().equals(id)).toList(); }
        };
        var storage=new Storage(System.getenv("STORAGE_CONNECTION"));
        String first=id+"/inputs/0.pdf",second=id+"/inputs/1.pdf";
        try {
            storage.put(first,new ByteArrayInputStream(new byte[]{1}),1);
            storage.put(second,new ByteArrayInputStream(new byte[]{2}),1);
            new org.springframework.transaction.support.TransactionTemplate(new org.springframework.jdbc.datasource.DataSourceTransactionManager(source)).executeWithoutResult(tx->{
                isolated.beginUpload(id,owner,2);
                assertEquals("CREATED",isolated.find(id,owner).orElseThrow().status());
                assertFalse(isolated.pending().stream().anyMatch(j->j.id().equals(id)));
                var db=new JdbcTemplate(source);
                db.update("UPDATE merge_jobs SET expires_at=now()-interval '1 second' WHERE id=?",id);
                assertFalse(isolated.queueUpload(id,owner),"Expired upload cannot be activated");
                var interrupted=new ExpiryCleanup(isolated,prefix->{storage.delete(first);throw new IllegalStateException("Simulated storage outage");});
                assertEquals(1,interrupted.run().failed());
                assertEquals("CREATED",isolated.find(id,owner).orElseThrow().status());
                assertTrue(storage.blobs.getBlobClient(second).exists());
                var cleanup=new ExpiryCleanup(isolated,storage::deletePrefix);
                assertEquals(1,cleanup.run().removed());
                assertEquals("EXPIRED",isolated.find(id,owner).orElseThrow().status());
                assertFalse(storage.blobs.getBlobClient(first).exists());
                assertFalse(storage.blobs.getBlobClient(second).exists());
                assertEquals(0,cleanup.run().removed());
                tx.setRollbackOnly();
            });
        } finally { storage.deletePrefix(id+"/"); }
    }
    @Test void expiredResultIsDeniedAndWorkerDeletesItsFiles() throws Exception {
        var accepted=submit(pdf(true),pdf(false),true);assertEquals(202,accepted.statusCode());
        String id=JSON.readTree(accepted.body()).path("id").asText();await(id,"COMPLETED");
        var db=new JdbcTemplate(new DriverManagerDataSource(System.getenv("DB_URL"),System.getenv("DB_USER"),System.getenv("DB_PASSWORD")));
        String output=db.queryForObject("SELECT output_key FROM merge_jobs WHERE id=?",String.class,UUID.fromString(id));
        db.update("UPDATE merge_jobs SET expires_at=now()-interval '1 second' WHERE id=?",UUID.fromString(id));
        assertEquals(410,get(client,"/api/v1/jobs/"+id+"/download").statusCode());
        long deadline=System.nanoTime()+Duration.ofSeconds(15).toNanos();
        while(System.nanoTime()<deadline && !"EXPIRED".equals(db.queryForObject("SELECT status FROM merge_jobs WHERE id=?",String.class,UUID.fromString(id)))) Thread.sleep(250);
        assertEquals("EXPIRED",db.queryForObject("SELECT status FROM merge_jobs WHERE id=?",String.class,UUID.fromString(id)));
        var storage=new Storage(System.getenv("STORAGE_CONNECTION"));
        for(String key:List.of(id+"/inputs/0.pdf",id+"/inputs/1.pdf",output)) assertFalse(storage.blobs.getBlobClient(key).exists(),key);
    }
}
