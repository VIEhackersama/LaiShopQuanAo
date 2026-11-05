package vn.laishop;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.laishop.common.ApiException;
import vn.laishop.search.SearchService;
import vn.laishop.search.ai.*;
import static org.junit.jupiter.api.Assertions.*;
import static vn.laishop.search.ai.AiTypes.*;

/** Requires a real local Ollama with qwen3:8b; no provider stubs. */
@EnabledIfEnvironmentVariable(named="RUN_AI_TESTS",matches="true")
@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL}","spring.datasource.username=${TEST_DATABASE_USER}","spring.datasource.password=${TEST_DATABASE_PASSWORD}","app.ai.enabled=true","app.ai.model=qwen3:8b","app.ai.timeout-seconds=60"})
class AiSearchIntegrationTest {
    @Autowired AiSearchService ai;
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void dedicatedDatabase() { assertTrue(jdbc.queryForObject("SELECT current_database()",String.class).endsWith("_test")); }
    @Test void realModelKeepsFiltersAndQueriesActualVariants() {
        var owner=new SearchService.SearchOwner(null,"live-ai-"+UUID.randomUUID());
        long started=System.nanoTime();
        ChatResponse first=ai.chat(new ChatRequest(null,null,"Tìm áo màu đen size M dưới 300 nghìn"),owner);
        assertEquals(1L,first.appliedFilters().categoryId()); assertEquals(List.of("M"),first.appliedFilters().sizes()); assertEquals(List.of("Đen"),first.appliedFilters().colors()); assertEquals(0,first.appliedFilters().maxPrice().compareTo(new BigDecimal("300000"))); assertTrue(first.products().total()>0);
        for(var product:first.products().items()) assertTrue(jdbc.queryForObject("SELECT count(*) FROM product_variants WHERE product_id=? AND size='M' AND color='Đen' AND price<=300000 AND stock_quantity>0 AND active",Integer.class,product.id())>0);
        ChatResponse next=ai.chat(new ChatRequest(first.conversationId(),first.version(),"Giữ loại áo, màu và size, chỉ lấy dưới 200 nghìn và xếp giá rẻ trước"),owner);
        assertEquals(first.conversationId(),next.conversationId()); assertEquals(2,next.version()); assertEquals(List.of("Đen"),next.appliedFilters().colors()); assertEquals(List.of("M"),next.appliedFilters().sizes()); assertEquals(0,next.appliedFilters().maxPrice().compareTo(new BigDecimal("200000"))); assertEquals(vn.laishop.catalog.CatalogTypes.Sort.PRICE_ASC,next.appliedFilters().sort());
        assertTrue(next.products().total()>0); assertTrue(next.products().items().stream().allMatch(p->new BigDecimal(p.maxPrice()).compareTo(new BigDecimal("200000"))<=0));
        assertEquals(404,assertThrows(ApiException.class,()->ai.conversation(first.conversationId(),new SearchService.SearchOwner(null,"different-session"))).status());
        assertEquals("AI_CONVERSATION_CHANGED",assertThrows(ApiException.class,()->ai.chat(new ChatRequest(first.conversationId(),1L,"đổi size"),owner)).code());
        System.out.println("AI_LIVE_EVIDENCE model="+first.model()+" firstMatches="+first.products().total()+" refinedMatches="+next.products().total()+" total_ms="+(System.nanoTime()-started)/1_000_000);
    }
    @Test void unavailableCombinationReturnsEmptyInsteadOfInventedProducts() {
        var owner=new SearchService.SearchOwner(null,"live-ai-"+UUID.randomUUID());
        var result=ai.chat(new ChatRequest(null,null,"Tìm váy màu đen dưới 1000 đồng, chỉ lấy hàng còn kho"),owner);
        assertNotNull(result.queryId()); assertEquals(0,result.products().total()); assertTrue(result.products().items().isEmpty());
    }
    @Test void contradictoryBudgetGetsClarification() {
        var owner=new SearchService.SearchOwner(null,"live-ai-"+UUID.randomUUID());
        var result=ai.chat(new ChatRequest(null,null,"Tôi cần tìm áo có giá tối thiểu 500 nghìn nhưng tối đa 200 nghìn. Hãy hỏi lại vì hai điều kiện mâu thuẫn."),owner);
        assertNull(result.queryId()); assertFalse(result.reply().isBlank()); assertEquals(0,result.products().total());
    }
}
