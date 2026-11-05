package vn.laishop.search.ai;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionTemplate;
import vn.laishop.catalog.CatalogDao;
import vn.laishop.catalog.CatalogTypes.*;
import vn.laishop.common.ApiException;
import vn.laishop.search.SearchService;
import static vn.laishop.search.ai.AiTypes.*;

@Repository
public class AiConversationDao {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final SearchService searches;
    private final CatalogDao catalog;
    private final AiCatalogDao aiCatalog;
    public AiConversationDao(JdbcTemplate jdbc,TransactionTemplate transactions,SearchService searches,CatalogDao catalog,AiCatalogDao aiCatalog) { this.jdbc=jdbc; this.transactions=transactions; this.searches=searches; this.catalog=catalog; this.aiCatalog=aiCatalog; }
    @org.springframework.transaction.annotation.Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Conversation owned(UUID id,SearchService.SearchOwner owner) {
        record Header(UUID id,long version,String state) { }
        List<Header> rows=jdbc.query("SELECT id,version,state::text FROM ai_conversations WHERE id=? AND (customer_id=? OR search_session=?)",(r,n)->new Header(r.getObject(1,UUID.class),r.getLong(2),r.getString(3)),id,owner.customer(),owner.session());
        if(rows.isEmpty()) throw ApiException.missing();
        var header=rows.getFirst();
        List<Message> messages=jdbc.query("SELECT role,content,query_id FROM (SELECT id,role,content,query_id FROM ai_messages WHERE conversation_id=? ORDER BY id DESC LIMIT 12) recent ORDER BY id",(r,n)->new Message(r.getString(1),r.getString(2),r.getObject(3,Long.class)),id);
        return new Conversation(header.id(),header.version(),searches.decode(header.state(),SearchArguments.class),messages);
    }
    public ChatResponse save(ChatRequest request,Conversation previous,SearchService.SearchOwner owner,Decision decision,String provider,String model) {
        return transactions.execute(status->{
            UUID id=previous==null?UUID.randomUUID():previous.id();
            long version=previous==null?1:previous.version()+1;
            SearchArguments state=decision instanceof SearchDecision search?search.arguments():previous==null?new SearchArguments(List.of(),null,List.of(),List.of(),null,null,Sort.RELEVANCE,true):previous.state();
            if(previous==null) jdbc.update("INSERT INTO ai_conversations(id,customer_id,search_session,version,state) VALUES (?,?,?,?,?::jsonb)",id,owner.customer(),owner.session(),version,searches.encode(state));
            else if(jdbc.update("UPDATE ai_conversations SET version=?,state=?::jsonb,updated_at=now() WHERE id=? AND version=?",version,searches.encode(state),id,previous.version())!=1) throw ApiException.conflict("AI_CONVERSATION_CHANGED","Hội thoại đã thay đổi ở lượt khác. Hãy tải lại trước khi gửi tiếp.");
            Long queryId=null;
            ProductPage products=new ProductPage(List.of(),0,12,0);
            String reply;
            if(decision instanceof SearchDecision search) {
                SearchArguments filter=search.arguments();
                List<Candidate> candidates=aiCatalog.search(filter);
                Conditions conditions=new Conditions(filter.categoryId(),filter.sizes(),filter.colors(),filter.minPrice(),filter.maxPrice(),filter.sort());
                queryId=searches.create("TEXT","COMPLETED",conditions,owner);
                jdbc.update("INSERT INTO text_queries(id,text) VALUES (?,?)",queryId,"Tìm kiếm qua hội thoại AI");
                searches.saveCandidates(queryId,candidates);
                products=catalog.queryPage(queryId,conditions,0,12);
                reply=products.total()==0?"Mình chưa tìm thấy sản phẩm đáp ứng đủ các điều kiện này. Bạn muốn đổi màu, size hoặc khoảng giá nào?":"Mình tìm thấy "+products.total()+" sản phẩm phù hợp trong LaiShopQuanAo. Các thẻ bên dưới hiển thị giá và tình trạng từ catalog hiện tại.";
            } else reply=((ClarifyDecision)decision).arguments().question();
            jdbc.update("INSERT INTO ai_messages(conversation_id,role,content) VALUES (?,'user',?)",id,request.message().strip());
            jdbc.update("INSERT INTO ai_messages(conversation_id,role,content,query_id) VALUES (?,'assistant',?,?)",id,reply,queryId);
            return new ChatResponse(id,version,reply,state,queryId,products,provider,model);
        });
    }
}
