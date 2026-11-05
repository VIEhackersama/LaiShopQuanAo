package vn.laishop.catalog;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import vn.laishop.common.ApiException;
import static vn.laishop.catalog.CatalogTypes.*;

@Repository
public class CatalogDao {
    private final NamedParameterJdbcTemplate jdbc;
    public CatalogDao(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    private static final String FROM = " FROM products p JOIN categories c ON c.id=p.category_id JOIN product_variants v ON v.product_id=p.id WHERE p.active AND c.active AND v.active ";
    private record Selection(String sql, MapSqlParameterSource parameters, Sort sort) { }
    private Selection selection(Conditions filter, String text) {
        if(filter.minPrice()!=null && filter.maxPrice()!=null && filter.minPrice().compareTo(filter.maxPrice())>0) throw new ApiException(400,"INVALID_PRICE_RANGE","Giá tối thiểu không được lớn hơn giá tối đa.");
        StringBuilder sql=new StringBuilder(FROM);
        MapSqlParameterSource params=new MapSqlParameterSource();
        if(filter.categoryId()!=null) { sql.append(" AND c.id=:category"); params.addValue("category",filter.categoryId()); }
        if(filter.sizes()!=null && !filter.sizes().isEmpty()) { sql.append(" AND v.size IN (:sizes)"); params.addValue("sizes",filter.sizes()); }
        if(filter.colors()!=null && !filter.colors().isEmpty()) { sql.append(" AND v.color IN (:colors)"); params.addValue("colors",filter.colors()); }
        if(filter.minPrice()!=null) { sql.append(" AND v.price>=:min"); params.addValue("min",filter.minPrice()); }
        if(filter.maxPrice()!=null) { sql.append(" AND v.price<=:max"); params.addValue("max",filter.maxPrice()); }
        if(text!=null) { sql.append(" AND p.search_text LIKE '%' || lower(unaccent(:text)) || '%' ESCAPE '\\'"); params.addValue("text",text.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")); params.addValue("rankText",text); }
        return new Selection(sql.toString(),params,filter.sort()==null?Sort.RELEVANCE:filter.sort());
    }
    private static String order(Sort sort) { return switch(sort) { case PRICE_ASC -> "min(v.price),p.id"; case PRICE_DESC -> "min(v.price) DESC,p.id"; case NEWEST,RELEVANCE -> "p.created_at DESC,p.id DESC"; case NAME_ASC -> "p.name,p.id"; }; }
    public ProductPage page(Conditions filter, String text, int page, int pageSize) {
        if(page<0 || pageSize<1 || pageSize>100) throw new ApiException(400,"INVALID_PAGE","page phải >= 0; pageSize từ 1 đến 100.");
        Selection selected=selection(filter,text);
        Long total=jdbc.queryForObject("SELECT count(DISTINCT p.id)"+selected.sql(),selected.parameters(),Long.class);
        String query="SELECT p.id,p.name,p.description,(SELECT image_url FROM product_images WHERE product_id=p.id ORDER BY display_order LIMIT 1) image_url,min(v.price) min_price,max(v.price) max_price,array_agg(DISTINCT v.color) colors,bool_or(v.stock_quantity>0) in_stock"+selected.sql()+" GROUP BY p.id ORDER BY "+order(selected.sort())+" LIMIT :limit OFFSET :offset";
        List<ProductCard> items=jdbc.query(query,selected.parameters().addValue("limit",pageSize).addValue("offset",(long)page*pageSize),(r,n)->new ProductCard(r.getLong("id"),r.getString("name"),r.getString("description"),r.getString("image_url"),r.getBigDecimal("min_price").toPlainString(),r.getBigDecimal("max_price").toPlainString(),Arrays.asList((String[])r.getArray("colors").getArray()),r.getBoolean("in_stock")));
        return new ProductPage(items,page,pageSize,total);
    }
    public List<Candidate> candidates(Conditions filter, String text) {
        Selection selected=selection(filter,text);
        String score=text==null?"1":"similarity(p.search_text,lower(unaccent(:rankText)))";
        String ordering=text!=null && selected.sort()==Sort.RELEVANCE?score+" DESC,p.id":order(selected.sort());
        return jdbc.query("SELECT p.id,"+score+" score"+selected.sql()+" GROUP BY p.id ORDER BY "+ordering,selected.parameters(),(r,n)->new Candidate(r.getLong(1),n+1,r.getBigDecimal(2)));
    }
    public ProductPage queryPage(long query,Conditions filter,int page,int size) {
        if(page<0 || size<1 || size>100) throw new ApiException(400,"INVALID_PAGE","page >= 0, pageSize từ 1 đến 100.");
        Selection selected=selection(filter,null);
        String from=selected.sql()+" AND EXISTS(SELECT 1 FROM search_results sr WHERE sr.query_id=:query AND sr.product_id=p.id)";
        var params=selected.parameters().addValue("query",query).addValue("limit",size).addValue("offset",(long)page*size);
        Long total=jdbc.queryForObject("SELECT count(DISTINCT p.id)"+from,params,Long.class);
        String sql="SELECT p.id,p.name,p.description,(SELECT image_url FROM product_images WHERE product_id=p.id ORDER BY display_order LIMIT 1) image_url,min(v.price) min_price,max(v.price) max_price,array_agg(DISTINCT v.color) colors,bool_or(v.stock_quantity>0) in_stock"+from+" GROUP BY p.id ORDER BY (SELECT rank FROM search_results WHERE query_id=:query AND product_id=p.id) LIMIT :limit OFFSET :offset";
        List<ProductCard> items=jdbc.query(sql,params,(r,n)->new ProductCard(r.getLong("id"),r.getString("name"),r.getString("description"),r.getString("image_url"),r.getBigDecimal("min_price").toPlainString(),r.getBigDecimal("max_price").toPlainString(),Arrays.asList((String[])r.getArray("colors").getArray()),r.getBoolean("in_stock")));
        return new ProductPage(items,page,size,total);
    }
    public List<CategoryResponse> categories() { return jdbc.query("SELECT id,name FROM categories WHERE active ORDER BY id",new MapSqlParameterSource(),(r,n)->new CategoryResponse(r.getLong(1),r.getString(2))); }
    public ProductResponse product(long id) {
        var params=new MapSqlParameterSource("id",id);
        record Header(long id,String name,String description,String category) { }
        List<Header> headers=jdbc.query("SELECT p.id,p.name,p.description,c.name FROM products p JOIN categories c ON c.id=p.category_id WHERE p.id=:id AND p.active AND c.active",params,(r,n)->new Header(r.getLong(1),r.getString(2),r.getString(3),r.getString(4)));
        if(headers.isEmpty()) throw ApiException.missing();
        List<VariantResponse> variants=jdbc.query("SELECT * FROM product_variants WHERE product_id=:id AND active ORDER BY id",params,(r,n)->new VariantResponse(r.getLong("id"),r.getString("sku"),r.getString("size"),r.getString("color"),r.getBigDecimal("price").toPlainString(),r.getInt("stock_quantity"),r.getBoolean("active")));
        List<ImageResponse> images=jdbc.query("SELECT image_url,alt_text FROM product_images WHERE product_id=:id ORDER BY display_order",params,(r,n)->new ImageResponse(r.getString(1),r.getString(2)));
        Header h=headers.getFirst(); return new ProductResponse(h.id(),h.name(),h.description(),h.category(),variants,images);
    }
}
