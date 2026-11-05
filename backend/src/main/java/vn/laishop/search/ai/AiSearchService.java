package vn.laishop.search.ai;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import vn.laishop.catalog.CatalogDao;
import vn.laishop.common.ApiException;
import vn.laishop.search.SearchService;
import static vn.laishop.search.ai.AiTypes.*;

@Service
public class AiSearchService {
    private final List<AiSearchPort> providers;
    private final CatalogDao catalog;
    private final AiCatalogDao aiCatalog;
    private final AiConversationDao conversations;
    public AiSearchService(List<AiSearchPort> providers,CatalogDao catalog,AiCatalogDao aiCatalog,AiConversationDao conversations) {
        if(providers.size()>1) throw new IllegalStateException("Configure exactly one AI search provider");
        this.providers=List.copyOf(providers); this.catalog=catalog; this.aiCatalog=aiCatalog; this.conversations=conversations;
    }
    public AiCapabilities capabilities() { return providers.isEmpty()?new AiCapabilities(false,"ollama","","Chatbot chưa được bật trên server."):new AiCapabilities(true,providers.getFirst().provider(),providers.getFirst().model(),"AI phân tích yêu cầu; giá và sản phẩm được truy vấn từ catalog thật."); }
    public ChatResponse chat(ChatRequest request,SearchService.SearchOwner owner) {
        if(providers.isEmpty()) throw new ApiException(503,"AI_NOT_CONFIGURED","Chatbot chưa được cấu hình. Bật AI_ENABLED và kiểm tra Ollama.");
        if((request.conversationId()==null)!=(request.expectedVersion()==null)) throw new ApiException(400,"AI_VERSION_REQUIRED","conversationId và expectedVersion phải cùng có hoặc cùng trống.");
        Conversation previous=request.conversationId()==null?null:conversations.owned(request.conversationId(),owner);
        if(previous!=null && previous.version()!=request.expectedVersion()) throw ApiException.conflict("AI_CONVERSATION_CHANGED","Phiên bản hội thoại đã thay đổi. Hãy tải lại hội thoại.");
        var categories=catalog.categories(); var sizes=aiCatalog.sizes(); var colors=aiCatalog.colors();
        TurnContext context=new TurnContext(categories,sizes,colors,previous==null?null:previous.state(),previous==null?List.of():previous.messages(),request.message().strip());
        AiSearchPort provider=providers.getFirst();
        Decision decision=provider.decide(context);
        if(decision instanceof SearchDecision search) {
            var f=search.arguments();
            if(f.categoryId()!=null && categories.stream().noneMatch(c->c.id()==f.categoryId())) throw new ApiException(502,"AI_INVALID_FILTER","AI trả danh mục không tồn tại: "+f.categoryId());
            if(f.minPrice()!=null && f.maxPrice()!=null && f.minPrice().compareTo(f.maxPrice())>0) throw new ApiException(502,"AI_INVALID_FILTER","AI trả khoảng giá không hợp lệ.");
            SearchArguments normalized=new SearchArguments(f.terms().stream().map(String::strip).toList(),f.categoryId(),f.sizes().stream().map(String::strip).map(s->s.toUpperCase(Locale.ROOT)).toList(),f.colors().stream().map(String::strip).toList(),f.minPrice(),f.maxPrice(),f.sort(),f.inStockOnly());
            decision=new SearchDecision(normalized);
        }
        return conversations.save(request,previous,owner,decision,provider.provider(),provider.model());
    }
    public Conversation conversation(java.util.UUID id,SearchService.SearchOwner owner) { return conversations.owned(id,owner); }
}
