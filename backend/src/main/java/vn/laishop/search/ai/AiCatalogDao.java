package vn.laishop.search.ai;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import vn.laishop.catalog.CatalogTypes.Candidate;
import static vn.laishop.search.ai.AiTypes.*;

@Repository
public class AiCatalogDao {
    private final NamedParameterJdbcTemplate jdbc;
    public AiCatalogDao(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    public List<String> sizes() { return jdbc.query("SELECT DISTINCT size FROM product_variants WHERE active ORDER BY size",new MapSqlParameterSource(),(r,n)->r.getString(1)); }
    public List<String> colors() { return jdbc.query("SELECT DISTINCT color FROM product_variants WHERE active ORDER BY color",new MapSqlParameterSource(),(r,n)->r.getString(1)); }
    public List<Candidate> search(SearchArguments filter) {
        StringBuilder sql=new StringBuilder("SELECT p.id FROM products p JOIN categories c ON c.id=p.category_id JOIN product_variants v ON v.product_id=p.id WHERE p.active AND c.active AND v.active");
        MapSqlParameterSource params=new MapSqlParameterSource();
        if(filter.categoryId()!=null) { sql.append(" AND c.id=:category"); params.addValue("category",filter.categoryId()); }
        if(!filter.sizes().isEmpty()) { sql.append(" AND v.size IN (:sizes)"); params.addValue("sizes",filter.sizes()); }
        if(!filter.colors().isEmpty()) { sql.append(" AND v.color IN (:colors)"); params.addValue("colors",filter.colors()); }
        if(filter.minPrice()!=null) { sql.append(" AND v.price>=:min"); params.addValue("min",filter.minPrice()); }
        if(filter.maxPrice()!=null) { sql.append(" AND v.price<=:max"); params.addValue("max",filter.maxPrice()); }
        if(filter.inStockOnly()) sql.append(" AND v.stock_quantity>0");
        if(!filter.terms().isEmpty()) {
            sql.append(" AND (");
            for(int index=0;index<filter.terms().size();index++) {
                if(index>0) sql.append(" OR ");
                sql.append("p.search_text LIKE '%' || lower(unaccent(:term").append(index).append(")) || '%' ESCAPE '\\'");
                params.addValue("term"+index,filter.terms().get(index).replace("\\","\\\\").replace("%","\\%").replace("_","\\_"));
            }
            sql.append(")");
        }
        sql.append(" GROUP BY p.id ORDER BY ").append(switch(filter.sort()) { case PRICE_ASC -> "min(v.price),p.id"; case PRICE_DESC -> "min(v.price) DESC,p.id"; case NAME_ASC -> "p.name,p.id"; case NEWEST,RELEVANCE -> "p.created_at DESC,p.id DESC"; });
        return jdbc.query(sql.toString(),params,(r,n)->new Candidate(r.getLong(1),n+1,BigDecimal.ONE));
    }
}
