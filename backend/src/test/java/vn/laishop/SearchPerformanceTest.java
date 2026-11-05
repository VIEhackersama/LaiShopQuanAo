package vn.laishop;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.laishop.catalog.CatalogTypes.*;
import vn.laishop.search.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL}","spring.datasource.username=${TEST_DATABASE_USER}","spring.datasource.password=${TEST_DATABASE_PASSWORD}"})
class SearchPerformanceTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired SearchService searches;
    @Test @Transactional void textSearchOnTenThousandProducts() {
        assertTrue(jdbc.queryForObject("SELECT current_database()",String.class).endsWith("_test"));
        jdbc.execute("INSERT INTO products(category_id,name,description) SELECT 1,'Benchmark garment '||n,'Cotton benchmark collection' FROM generate_series(1,10000) n");
        jdbc.execute("INSERT INTO product_variants(product_id,sku,size,color,price,stock_quantity) SELECT id,'BENCH-'||id,'M','Kem',200000,10 FROM products WHERE name LIKE 'Benchmark garment %'");
        jdbc.execute("ANALYZE products"); jdbc.execute("ANALYZE product_variants");
        var conditions=new Conditions(null,List.of(),List.of(),null,null,Sort.RELEVANCE);
        var request=new SearchTypes.TextSearch("benchmark",conditions,0,24);
        List<Double> milliseconds=new ArrayList<>();
        for(int index=0;index<12;index++) { long started=System.nanoTime(); var result=searches.text(request,new SearchService.SearchOwner(null,"benchmark-session")); assertEquals(10000,result.products().total()); if(index>=2) milliseconds.add((System.nanoTime()-started)/1_000_000.0); }
        milliseconds.sort(Double::compareTo); double p95=milliseconds.get((int)Math.ceil(milliseconds.size()*.95)-1);
        String plan=String.join("\n",jdbc.query("EXPLAIN (ANALYZE,BUFFERS) SELECT id FROM products WHERE search_text LIKE '%garment 4242%'",(r,n)->r.getString(1)));
        System.out.println("SEARCH_BENCHMARK products=10000 matches=10000 warmups=2 samples=10 p95_ms="+p95+"\n"+plan);
        assertTrue(p95<=2000,"Search p95 exceeded 2000 ms: "+p95);
    }
}
