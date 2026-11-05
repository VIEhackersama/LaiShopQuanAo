package vn.laishop.search;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.laishop.catalog.CatalogDao;
import vn.laishop.catalog.CatalogTypes.*;
import vn.laishop.common.ApiException;
import static vn.laishop.search.SearchTypes.*;

@Service
public class SearchService {
    private final JdbcTemplate jdbc;
    private final CatalogDao catalog;
    private final ObjectMapper json;
    public SearchService(JdbcTemplate jdbc,CatalogDao catalog,ObjectMapper json) { this.jdbc=jdbc; this.catalog=catalog; this.json=json; }
    public record SearchOwner(Long customer,String session) { }
    public record StoredQuery(long id,String type,String status,String conditions,String text,String media,String region) { }
    public StoredQuery owned(long id,SearchOwner owner) {
        List<StoredQuery> rows=jdbc.query("SELECT q.id,q.query_type,q.status,q.search_conditions::text,COALESCE(t.text,v.transcribed_text) text,COALESCE(v.audio_url,i.image_url) media,i.selected_region::text region FROM search_queries q LEFT JOIN text_queries t ON t.id=q.id LEFT JOIN voice_queries v ON v.id=q.id LEFT JOIN image_queries i ON i.id=q.id WHERE q.id=? AND (q.customer_id=? OR q.search_session=?)",(r,n)->new StoredQuery(r.getLong(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6),r.getString(7)),id,owner.customer(),owner.session());
        if(rows.isEmpty()) throw ApiException.missing(); return rows.getFirst();
    }
    @Transactional public SearchResponse text(TextSearch request,SearchOwner owner) {
        String text=request.text().strip(); if(text.isEmpty()) throw new ApiException(400,"EMPTY_KEYWORD","Nhập từ khóa để tìm kiếm.");
        long id=create("TEXT","COMPLETED",request.conditions(),owner);
        jdbc.update("INSERT INTO text_queries(id,text) VALUES (?,?)",id,text);
        saveCandidates(id,catalog.candidates(request.conditions(),text));
        return response(id,owner,request.page(),request.pageSize());
    }
    public long create(String type,String status,Conditions conditions,SearchOwner owner) { return jdbc.queryForObject("INSERT INTO search_queries(customer_id,search_session,query_type,status,search_conditions) VALUES (?,?,?,?,?::jsonb) RETURNING id",Long.class,owner.customer(),owner.session(),type,status,encode(conditions)); }
    public void saveCandidates(long id,List<Candidate> candidates) { jdbc.batchUpdate("INSERT INTO search_results(query_id,product_id,rank,score) VALUES (?,?,?,?)",candidates,500,(ps,c)->{ps.setLong(1,id);ps.setLong(2,c.id());ps.setInt(3,c.rank());ps.setBigDecimal(4,c.score());}); }
    public SearchResponse response(long id,SearchOwner owner,int page,int size) {
        StoredQuery query=owned(id,owner); Conditions conditions=decode(query.conditions(),Conditions.class);
        return new SearchResponse(id,query.type(),query.status(),query.text(),conditions,catalog.queryPage(id,conditions,page,size));
    }
    @Transactional public SearchResponse refine(long id,RefineSearch request,SearchOwner owner) {
        StoredQuery original=owned(id,owner);
        if(original.type().equals("IMAGE")) throw new ApiException(503,"IMAGE_REFINEMENT_REQUIRES_PROVIDER","Tìm kiếm ảnh cần adapter hình ảnh được cấu hình.");
        if(original.text()==null) throw ApiException.conflict("TRANSCRIPT_REQUIRED","Cần xác nhận phiên âm trước khi lọc.");
        long next=create(original.type(),"COMPLETED",request.conditions(),owner);
        if(original.type().equals("TEXT")) jdbc.update("INSERT INTO text_queries(id,text) VALUES (?,?)",next,original.text());
        else jdbc.update("INSERT INTO voice_queries(id,audio_url,transcribed_text) VALUES (?,?,?)",next,original.media(),original.text());
        saveCandidates(next,catalog.candidates(request.conditions(),original.text())); return response(next,owner,request.page(),request.pageSize());
    }
    @Transactional public SearchResponse executeVoice(long id,VoiceExecute request,SearchOwner owner) {
        StoredQuery original=owned(id,owner); if(!original.type().equals("VOICE")) throw ApiException.missing();
        String text=request.text().strip(); if(text.isEmpty()) throw new ApiException(400,"EMPTY_KEYWORD","Phiên âm không được rỗng.");
        long next=create("VOICE","COMPLETED",request.conditions(),owner);
        jdbc.update("INSERT INTO voice_queries(id,audio_url,transcribed_text) VALUES (?,?,?)",next,original.media(),text);
        saveCandidates(next,catalog.candidates(request.conditions(),text)); return response(next,owner,request.page(),request.pageSize());
    }
    public String encode(Object value) { try { return json.writeValueAsString(value); } catch(JsonProcessingException error) { throw new IllegalStateException("Cannot serialize search data",error); } }
    public <T> T decode(String value,Class<T> type) { try { return json.readValue(value,type); } catch(JsonProcessingException error) { throw new IllegalStateException("Invalid stored search data",error); } }
}
