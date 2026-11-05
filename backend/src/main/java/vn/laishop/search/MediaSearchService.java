package vn.laishop.search;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.laishop.common.ApiException;
import vn.laishop.catalog.CatalogDao;
import vn.laishop.catalog.CatalogTypes.*;
import static vn.laishop.search.SearchTypes.*;

@org.springframework.stereotype.Service
public class MediaSearchService {
    private static final Logger LOG=LoggerFactory.getLogger(MediaSearchService.class);
    private final List<SpeechRecognitionPort> speech;
    private final List<ImageSimilarityPort> image;
    private final SearchService searches;
    private final MediaStore media;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final CatalogDao catalog;
    public MediaSearchService(List<SpeechRecognitionPort> speech,List<ImageSimilarityPort> image,SearchService searches,MediaStore media,JdbcTemplate jdbc,TransactionTemplate transactions,CatalogDao catalog) {
        if(speech.size()>1 || image.size()>1) throw new IllegalStateException("Configure at most one adapter per media search port");
        this.speech=List.copyOf(speech); this.image=List.copyOf(image); this.searches=searches; this.media=media; this.jdbc=jdbc; this.transactions=transactions; this.catalog=catalog;
    }
    Capabilities capabilities() { return new Capabilities(!speech.isEmpty(),!image.isEmpty()); }
    private static ApiException unavailable() { return new ApiException(503,"SEARCH_PROVIDER_NOT_CONFIGURED","Chức năng đang chờ tích hợp dịch vụ xử lý. Bạn có thể tìm bằng văn bản."); }
    SearchResponse voice(MultipartFile file,double durationSeconds,SearchService.SearchOwner owner) throws IOException {
        if(speech.isEmpty()) throw unavailable();
        if(!Double.isFinite(durationSeconds) || durationSeconds<1 || durationSeconds>30) throw new ApiException(422,"INVALID_AUDIO_DURATION","Bản ghi âm phải dài từ 1 đến 30 giây.");
        byte[] bytes=checked(file); String type=audioType(bytes);
        String name=media.save(bytes);
        Conditions conditions=new Conditions(null,List.of(),List.of(),null,null,Sort.RELEVANCE);
        Long id=transactions.execute(status->{ long next=searches.create("VOICE","PROCESSING",conditions,owner); jdbc.update("INSERT INTO voice_queries(id,audio_url) VALUES (?,?)",next,name); return next; });
        Transcript transcript;
        try {
            transcript=retry(()->speech.getFirst().transcribe(new AudioInput(bytes,type,durationSeconds)),"voice");
            if(transcript==null || transcript.text()==null || transcript.text().isBlank() || transcript.text().length()>100 || !Double.isFinite(transcript.durationSeconds()) || transcript.durationSeconds()<1 || transcript.durationSeconds()>30)
                throw new ApiException(422,"INVALID_TRANSCRIPT","Âm thanh phải dài 1–30 giây và có nội dung phiên âm tối đa 100 ký tự.");
        } catch(ProviderUnavailableException error) {
            fail(id); throw new ApiException(503,"SEARCH_PROVIDER_FAILED",error.getMessage());
        } catch(ApiException error) {
            fail(id); throw error;
        } catch(RuntimeException error) {
            fail(id); throw error;
        }
        transactions.executeWithoutResult(status->{ jdbc.update("UPDATE voice_queries SET transcribed_text=? WHERE id=?",transcript.text(),id); jdbc.update("UPDATE search_queries SET status='AWAITING_CONFIRMATION' WHERE id=?",id); });
        return searches.response(id,owner,0,24);
    }
    SearchResponse image(MultipartFile file,ImageOptions options,SearchService.SearchOwner owner) throws IOException {
        if(image.isEmpty()) throw unavailable();
        byte[] bytes=checked(file); String type=imageType(bytes); Region region=options.region();
        if(region!=null && (region.x()+region.width()>1 || region.y()+region.height()>1)) throw new ApiException(400,"INVALID_REGION","Vùng chọn phải nằm trong ảnh.");
        String name=media.save(bytes);
        Long id=transactions.execute(status->{long next=searches.create("IMAGE","PROCESSING",options.conditions(),owner); jdbc.update("INSERT INTO image_queries(id,image_url,selected_region) VALUES (?,?,?::jsonb)",next,name,region==null?null:searches.encode(region)); return next;});
        return runImage(id,new ImageInput(bytes,type,region,options.conditions()),owner);
    }
    public record ImageOptions(@jakarta.validation.constraints.NotNull @Valid Conditions conditions,@Valid Region region) { }
    SearchResponse runImage(long id,ImageInput input,SearchService.SearchOwner owner) {
        try {
            ImageMatches matches=retry(()->image.getFirst().search(input),"image");
            if(matches==null || matches.items().stream().anyMatch(m->m.score()==null) || matches.items().stream().map(ImageMatch::productId).distinct().count()!=matches.items().size()) throw new IllegalStateException("Image adapter must return unique product IDs and finite decimal scores");
            var eligible=catalog.candidates(input.conditions(),null).stream().map(Candidate::id).collect(java.util.stream.Collectors.toSet());
            List<ImageMatch> filtered=matches.items().stream().filter(m->eligible.contains(m.productId())).toList();
            List<ImageMatch> sorted=switch(input.conditions().sort()==null?Sort.RELEVANCE:input.conditions().sort()) { case RELEVANCE -> filtered; default -> catalog.candidates(input.conditions(),null).stream().flatMap(c->filtered.stream().filter(m->m.productId()==c.id())).toList(); };
            transactions.executeWithoutResult(status->{ for(int n=0;n<sorted.size();n++) { var match=sorted.get(n); jdbc.update("INSERT INTO search_results(query_id,product_id,rank,score) VALUES (?,?,?,?)",id,match.productId(),n+1,match.score()); } jdbc.update("UPDATE search_queries SET status='COMPLETED' WHERE id=?",id); });
            return searches.response(id,owner,0,24);
        } catch(ProviderUnavailableException error) {
            fail(id); throw new ApiException(503,"SEARCH_PROVIDER_FAILED",error.getMessage());
        } catch(ApiException error) {
            fail(id); throw error;
        } catch(RuntimeException error) {
            fail(id); throw error;
        }
    }
    SearchResponse refine(long id,RefineSearch body,SearchService.SearchOwner owner) throws IOException {
        if(image.isEmpty()) throw unavailable(); var original=searches.owned(id,owner); if(!original.type().equals("IMAGE")) throw ApiException.missing();
        byte[] bytes=media.read(original.media()); Region region=original.region()==null?null:searches.decode(original.region(),Region.class);
        Long next=transactions.execute(status->{ long created=searches.create("IMAGE","PROCESSING",body.conditions(),owner); jdbc.update("INSERT INTO image_queries(id,image_url,selected_region) VALUES (?,?,?::jsonb)",created,original.media(),original.region()); return created; });
        return runImage(next,new ImageInput(bytes,imageType(bytes),region,body.conditions()),owner);
    }
    ResponseEntity<byte[]> read(long id,SearchService.SearchOwner owner) throws IOException {
        var query=searches.owned(id,owner); if(query.media()==null) throw ApiException.missing(); byte[] bytes=media.read(query.media());
        return ResponseEntity.ok().header("Cache-Control","private, no-store").header("X-Content-Type-Options","nosniff").contentType(MediaType.parseMediaType(query.type().equals("IMAGE")?imageType(bytes):audioType(bytes))).body(bytes);
    }
    private static byte[] checked(MultipartFile file) throws IOException { if(file.getSize()>10*1024*1024) throw new ApiException(413,"FILE_TOO_LARGE","File tối đa 10 MB."); if(file.isEmpty()) throw new ApiException(400,"EMPTY_FILE","File không được rỗng."); return file.getBytes(); }
    private static String imageType(byte[] bytes) {
        if(bytes.length>12 && (bytes[0]&255)==255 && (bytes[1]&255)==216 && (bytes[2]&255)==255) return "image/jpeg";
        if(bytes.length>24 && bytes[0]==(byte)137 && bytes[1]==80 && bytes[2]==78 && bytes[3]==71) return "image/png";
        throw new ApiException(400,"INVALID_IMAGE","File phải có nội dung JPEG hoặc PNG hợp lệ; adapter phải giải mã nội dung trước khi nhận dạng.");
    }
    private static String audioType(byte[] bytes) {
        if(bytes.length>12 && new String(bytes,0,4,java.nio.charset.StandardCharsets.US_ASCII).equals("RIFF") && new String(bytes,8,4,java.nio.charset.StandardCharsets.US_ASCII).equals("WAVE")) return "audio/wav";
        if(bytes.length>4 && bytes[0]==0x1A && bytes[1]==0x45 && bytes[2]==(byte)0xDF && bytes[3]==(byte)0xA3) return "audio/webm";
        if(bytes.length>12 && new String(bytes,4,4,java.nio.charset.StandardCharsets.US_ASCII).equals("ftyp")) return "audio/mp4";
        throw new ApiException(400,"INVALID_AUDIO","Bản ghi phải là WebM, MP4 hoặc WAV hợp lệ.");
    }
    private static <T> T retry(Supplier<T> operation,String provider) {
        for(int attempt=1;attempt<=3;attempt++) { try { return operation.get(); } catch(ProviderUnavailableException error) { LOG.atWarn().addKeyValue("provider",provider).addKeyValue("attempt",attempt).setCause(error).log("search_provider_failed"); if(attempt==3) throw error; } }
        throw new IllegalStateException("Retry loop exited unexpectedly");
    }
    private void fail(long id) { jdbc.update("UPDATE search_queries SET status='FAILED' WHERE id=?",id); }
}
