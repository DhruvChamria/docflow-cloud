package edu.manipal.ccd.api;
import edu.manipal.ccd.local.*;
import jakarta.servlet.http.*;
import java.util.*;
import java.io.*;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
@RestController
@Profile({"local","cloud"})
@RequestMapping("/api/v1")
class JobsController {
    private final JobStore jobs; private final Storage storage; private final Identity identities;private final SecretBox secrets;private final int maxActiveJobs;
    JobsController(JobStore jobs,Storage storage,Identity identities,SecretBox secrets,
                   @Value("${app.jobs.max-active-per-owner:5}") int maxActiveJobs) {
        this.jobs=jobs;this.storage=storage;this.identities=identities;this.secrets=secrets;this.maxActiveJobs=maxActiveJobs;
    }
    record View(UUID id,String status,int attempts,String error,Instant expiresAt,String operation,long outputSize,String outputChecksum) {}
    private View view(JobStore.Job job) { return new View(job.id(),job.status(),job.attempts(),job.error(),job.expires(),job.operation(),job.outputSize(),job.outputChecksum()); }
    @PostMapping(value="/jobs",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<View> submit(@RequestParam("files") List<MultipartFile> files,
        @RequestParam(defaultValue="pdf.merge") String operation,
        @RequestParam(defaultValue="") String pages,@RequestParam(defaultValue="0") int angle,
        @RequestParam(defaultValue="1") int copies,@RequestParam(defaultValue="1") int insertAt,
        @RequestParam(defaultValue="A4") String pageSize,@RequestParam(defaultValue="") String text,
        @RequestParam(defaultValue="BOTTOM_CENTER") String position,@RequestParam(defaultValue="1") int startNumber,
        @RequestParam(defaultValue="12") int fontSize,@RequestParam(defaultValue="0.25") double opacity,
        @RequestParam(defaultValue="144") int dpi,
        @RequestParam(defaultValue="25") int scalePercent,
        @RequestParam(defaultValue="EBOOK") String quality,
        @RequestParam(defaultValue="") String title,@RequestParam(defaultValue="") String author,
        @RequestParam(defaultValue="") String subject,@RequestParam(defaultValue="") String keywords,
        @RequestParam(defaultValue="") String documentPassword,
        HttpServletRequest request,HttpServletResponse response) throws IOException {
        identities.checkMutation(request);
        edu.manipal.ccd.core.PdfOptions options;
        try {
            String storedText=operation.equals("pdf.metadata_update")?edu.manipal.ccd.core.PdfOptions.metadataPayload(title,author,subject,keywords):operation.equals("pdf.compress")?quality:text;
            int storedFontSize=operation.equals("pdf.render_images")?dpi:operation.equals("pdf.watermark_image")?scalePercent:fontSize;
            options=new edu.manipal.ccd.core.PdfOptions(operation,pages,angle,copies,insertAt,pageSize,storedText,position,startNumber,storedFontSize,opacity);
        }
        catch(IllegalArgumentException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,invalid.getMessage()); }
        boolean passwordJob=operation.equals("pdf.protect")||operation.equals("pdf.unlock");
        if(passwordJob) {
            int minimum=operation.equals("pdf.protect")?8:1;
            if(documentPassword.length()<minimum||documentPassword.length()>128) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Document password length is invalid");
        } else if(!documentPassword.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"This operation does not accept a document password");
        boolean invalidCount=switch(operation) {
            case "pdf.merge" -> files.size()<2||files.size()>10;
            case "convert.image_to_pdf" -> files.isEmpty()||files.size()>20;
            case "pdf.watermark_image" -> files.size()!=2;
            default -> files.size()!=1;
        };
        if(invalidCount) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid number of files for this operation");
        long total=0;
        for(int fileIndex=0;fileIndex<files.size();fileIndex++) {
            var file=files.get(fileIndex);
            total+=file.getSize();
            boolean textConversion=Set.of("convert.text_to_pdf","convert.markdown_to_pdf","convert.html_to_pdf").contains(operation);
            long perFileLimit=textConversion?1024L*1024:25L*1024*1024;
            if(file.isEmpty() || file.getSize()>perFileLimit || total>90L*1024*1024)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,textConversion?"Maximum 1 MiB for text conversion":"Maximum 25 MiB per file and 90 MiB total");
            try(var input=file.getInputStream()) {
                byte[] header=input.readNBytes(12);
                boolean expectsImage=operation.equals("convert.image_to_pdf")||(operation.equals("pdf.watermark_image")&&fileIndex==1);
                var officeFormat=switch(operation) {
                    case "convert.docx_to_pdf" -> OfficePackageValidator.Format.DOCX;
                    case "convert.pptx_to_pdf" -> OfficePackageValidator.Format.PPTX;
                    case "convert.odt_to_pdf" -> OfficePackageValidator.Format.ODT;
                    case "convert.odp_to_pdf" -> OfficePackageValidator.Format.ODP;
                    case "convert.ods_to_pdf" -> OfficePackageValidator.Format.ODS;
                    default -> null;
                };
                boolean valid=textConversion?isUtf8Text(file):expectsImage?isImage(header):officeFormat!=null?OfficePackageValidator.valid(file,officeFormat):startsWith(header,"%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                if(!valid) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,textConversion?"Expected valid UTF-8 text":expectsImage?"Expected a JPG or PNG image":officeFormat!=null?"Expected a valid "+officeFormat.name()+" package":"Expected a PDF");
            }
        }
        String owner=identities.owner(request,response,true);
        if(jobs.activeCount(owner)>=maxActiveJobs)
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Finish or cancel an active job before submitting another");
        UUID id=UUID.randomUUID();
        // Reserve metadata before the first blob so interrupted uploads remain discoverable for cleanup.
        jobs.beginUpload(id,owner,files.size(),options,UUID.fromString((String)request.getAttribute(RequestCorrelationFilter.ATTRIBUTE)));
        try {
            if(passwordJob)jobs.storeSecret(id,secrets.encrypt(documentPassword.toCharArray()));
            for(int i=0;i<files.size();i++) try(var stream=files.get(i).getInputStream()) {
                storage.put(id+"/inputs/"+i+".bin",stream,files.get(i).getSize());
            }
        } catch(RuntimeException|IOException failure) {
            try { jobs.failUpload(id,owner); } catch(Exception ignored) { /* CREATED still expires */ }
            try { jobs.deleteSecret(id); } catch(Exception ignored) { /* expiry cleanup retries */ }
            try { storage.deletePrefix(id+"/"); } catch(Exception ignored) { /* lifecycle retries deletion */ }
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Upload unavailable; retry later");
        }
        // Do not delete inputs after an ambiguous activation commit failure.
        if(!jobs.queueUpload(id,owner)) throw new ResponseStatusException(HttpStatus.GONE,"Upload expired; please start again");
        return ResponseEntity.accepted().header("Location","/api/v1/jobs/"+id)
            .body(view(jobs.find(id,owner).orElseThrow()));
    }
    private JobStore.Job owned(UUID id,HttpServletRequest request,HttpServletResponse response) {
        var job=jobs.find(id,identities.owner(request,response,false))
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Job not found"));
        if(job.expires().isBefore(Instant.now())) throw new ResponseStatusException(HttpStatus.GONE,"Job expired");
        return job;
    }
    @GetMapping("/jobs")
    ResponseEntity<List<View>> history(@RequestParam(defaultValue="20") int limit,@RequestParam(required=false) Instant before,
                                      HttpServletRequest request,HttpServletResponse response) {
        if(limit<1||limit>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"History limit must be between 1 and 100");
        var result=identities.ownerIfPresent(request).map(owner->jobs.history(owner,limit,before)).orElse(List.of());
        var builder=ResponseEntity.ok();
        if(result.size()==limit)builder.header("X-Next-Before",result.get(result.size()-1).created().toString());
        return builder.body(result.stream().map(this::view).toList());
    }
    @GetMapping("/jobs/{id}")
    View status(@PathVariable UUID id,HttpServletRequest request,HttpServletResponse response) { return view(owned(id,request,response)); }
    @PostMapping("/jobs/{id}/cancel")
    View cancel(@PathVariable UUID id,HttpServletRequest request,HttpServletResponse response) {
        identities.checkMutation(request);
        String owner=identities.owner(request,response,false);
        var existing=jobs.find(id,owner).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Job not found"));
        if(existing.expires().isBefore(Instant.now())) throw new ResponseStatusException(HttpStatus.GONE,"Job expired");
        if(!jobs.cancel(id,owner)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Only active jobs can be cancelled");
        jobs.deleteSecret(id);
        try { storage.deletePrefix(id+"/"); } catch(RuntimeException ignored) { /* expiry cleanup retries residual blobs */ }
        return view(jobs.find(id,owner).orElseThrow());
    }
    @DeleteMapping("/jobs/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id,HttpServletRequest request,HttpServletResponse response) {
        identities.checkMutation(request);String owner=identities.owner(request,response,false);
        var job=jobs.find(id,owner).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Job not found"));
        if(job.status().matches("CREATED|QUEUED|PROCESSING|RETRYING")) jobs.cancel(id,owner);
        try { storage.deletePrefix(id+"/"); }
        catch(RuntimeException unavailable) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"File deletion unavailable; retry later"); }
        if(!jobs.remove(id,owner)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Job changed while being deleted; retry");
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/jobs/{id}/download")
    ResponseEntity<StreamingResponseBody> download(@PathVariable UUID id,HttpServletRequest request,HttpServletResponse response) {
        var job=owned(id,request,response);
        if(!job.status().equals("COMPLETED")) throw new ResponseStatusException(HttpStatus.CONFLICT,"Result is not ready");
        var builder=ResponseEntity.ok().contentType(resultType(job.operation()))
            .header("Content-Disposition","attachment; filename=\""+resultName(job.operation())+"\"")
            .header("X-Content-Type-Options","nosniff");
        if(job.outputSize()>0)builder.contentLength(job.outputSize());
        if(job.outputChecksum()!=null&&job.outputChecksum().matches("[a-f0-9]{64}"))builder.header("X-Checksum-SHA256",job.outputChecksum());
        return builder.body(output->{try(var input=storage.open(job.output())) { input.transferTo(output); }});
    }
    private String resultName(String operation) {
        return switch(operation) {
            case "pdf.merge" -> "merged.pdf";
            case "pdf.split" -> "split-pdfs.zip";
            case "pdf.extract" -> "extracted-pages.pdf";
            case "pdf.rotate" -> "rotated.pdf";
            case "pdf.reorder" -> "reordered.pdf";
            case "pdf.delete" -> "pages-removed.pdf";
            case "pdf.duplicate_pages" -> "duplicated-pages.pdf";
            case "pdf.insert_blank" -> "blank-pages-inserted.pdf";
            case "pdf.page_numbers" -> "numbered.pdf";
            case "pdf.watermark_text" -> "watermarked.pdf";
            case "pdf.watermark_image" -> "image-watermarked.pdf";
            case "convert.image_to_pdf" -> "images.pdf";
            case "convert.text_to_pdf" -> "text-document.pdf";
            case "convert.markdown_to_pdf" -> "markdown-document.pdf";
            case "convert.html_to_pdf" -> "html-document.pdf";
            case "convert.docx_to_pdf" -> "word-document.pdf";
            case "convert.pptx_to_pdf" -> "presentation.pdf";
            case "convert.odt_to_pdf" -> "open-document.pdf";
            case "convert.odp_to_pdf" -> "open-presentation.pdf";
            case "convert.ods_to_pdf" -> "spreadsheet.pdf";
            case "pdf.render_images" -> "rendered-pages.zip";
            case "pdf.extract_text" -> "extracted-text.txt";
            case "pdf.metadata_read" -> "metadata.json";
            case "pdf.metadata_update" -> "metadata-updated.pdf";
            case "pdf.metadata_remove" -> "metadata-removed.pdf";
            case "pdf.optimize" -> "optimized.pdf";
            case "pdf.compress" -> "compressed.pdf";
            case "pdf.protect" -> "protected.pdf";
            case "pdf.unlock" -> "unlocked.pdf";
            default -> "document-result.pdf";
        };
    }
    private MediaType resultType(String operation) {
        return switch(operation) {
            case "pdf.split","pdf.render_images" -> MediaType.parseMediaType("application/zip");
            case "pdf.extract_text" -> MediaType.parseMediaType("text/plain;charset=UTF-8");
            case "pdf.metadata_read" -> MediaType.APPLICATION_JSON;
            default -> MediaType.APPLICATION_PDF;
        };
    }
    private static boolean startsWith(byte[] value,byte[] prefix) {
        if(value.length<prefix.length)return false;for(int i=0;i<prefix.length;i++)if(value[i]!=prefix[i])return false;return true;
    }
    private static boolean isImage(byte[] header) {
        return startsWith(header,new byte[]{(byte)0x89,'P','N','G',13,10,26,10}) || startsWith(header,new byte[]{(byte)0xff,(byte)0xd8,(byte)0xff});
    }
    private static boolean isUtf8Text(MultipartFile file) {
        try {
            byte[] bytes=file.getBytes();for(byte value:bytes)if(value==0)return false;
            java.nio.charset.StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes));return true;
        } catch(Exception invalid) { return false; }
    }
}
