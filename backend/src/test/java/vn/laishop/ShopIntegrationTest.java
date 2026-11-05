package vn.laishop;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import vn.laishop.cart.*;
import vn.laishop.common.ApiException;
import vn.laishop.order.*;
import vn.laishop.search.*;
import vn.laishop.catalog.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static vn.laishop.cart.CartTypes.*;
import static vn.laishop.order.OrderTypes.*;
import static vn.laishop.catalog.CatalogTypes.*;

@SpringBootTest(properties={"spring.datasource.url=${TEST_DATABASE_URL}","spring.datasource.username=${TEST_DATABASE_USER}","spring.datasource.password=${TEST_DATABASE_PASSWORD}","app.bank.name=DEMO BANK","app.bank.account=DEMO-ONLY","app.bank.holder=TEST SHOP","server.servlet.session.cookie.secure=false"})
@AutoConfigureMockMvc
class ShopIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired CartService carts;
    @Autowired OrderService orders;
    @Autowired CatalogDao catalog;
    @Autowired SearchService searches;
    @Autowired PasswordEncoder passwords;
    @Autowired MockMvc http;
    @Autowired ObjectMapper json;
    long first;
    long second;
    long variant;
    @BeforeEach void prepare() {
        assertTrue(jdbc.queryForObject("SELECT current_database()",String.class).endsWith("_test"),"Integration tests require a dedicated database ending in _test");
        jdbc.execute("TRUNCATE customers CASCADE");
        jdbc.update("UPDATE product_variants SET stock_quantity=12,active=true");
        jdbc.update("UPDATE products SET active=true");
        first=customer("first@example.test"); second=customer("second@example.test");
        variant=jdbc.queryForObject("SELECT min(id) FROM product_variants",Long.class);
    }
    long customer(String email) { return jdbc.queryForObject("INSERT INTO customers(full_name,email,password_hash) VALUES ('Test Customer',?,?) RETURNING id",Long.class,email,passwords.encode("integration-password")); }
    PlaceOrderRequest request(long customer,PaymentMethod method) { var cart=carts.view(customer); return new PlaceOrderRequest("Test Receiver","0912345678","123 Test Street",method,cart.version(),new BigDecimal(cart.totalAmount())); }
    @Test void emptyReadDoesNotCreateCart() { assertTrue(carts.view(first).items().isEmpty()); assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM carts",Integer.class)); }
    @Test void quantitiesMergeAndDifferentVariantsRemainSeparate() {
        carts.add(first,new AddItem(variant,2)); var merged=carts.add(first,new AddItem(variant,3)); assertEquals(1,merged.items().size()); assertEquals(5,merged.totalQuantity());
        long other=jdbc.queryForObject("SELECT min(id) FROM product_variants WHERE id<>?",Long.class,variant);
        assertEquals(2,carts.add(first,new AddItem(other,1)).items().size()); assertEquals(12,jdbc.queryForObject("SELECT stock_quantity FROM product_variants WHERE id=?",Integer.class,variant));
    }
    @Test void clearKeepsCartAndStock() { var before=carts.add(first,new AddItem(variant,1)); var after=carts.clear(first); assertEquals(before.id(),after.id()); assertEquals("0",after.totalAmount()); assertEquals(12,jdbc.queryForObject("SELECT stock_quantity FROM product_variants WHERE id=?",Integer.class,variant)); }
    @Test void stockFailureRollsBackCartMutation() { var cart=carts.add(first,new AddItem(variant,2)); assertThrows(ApiException.class,()->carts.add(first,new AddItem(variant,20))); assertEquals(cart,carts.view(first)); }
    @Test void cartOwnershipIsEnforced() { long item=carts.add(first,new AddItem(variant,1)).items().getFirst().id(); assertEquals(404,assertThrows(ApiException.class,()->carts.remove(second,item)).status()); }
    @Test void firstCartCreationIsSerialized() throws Exception {
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<CartResponse>> futures=executor.invokeAll(List.of(()->carts.add(first,new AddItem(variant,1)),()->carts.add(first,new AddItem(variant,1))));
            for(var future:futures) future.get(10,TimeUnit.SECONDS);
        }
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM carts WHERE customer_id=?",Integer.class,first)); assertEquals(2,carts.view(first).totalQuantity());
    }
    @Test void orderReplayDoesNotDoubleChargeStockAndSnapshotsSurviveChanges() {
        carts.add(first,new AddItem(variant,2)); var body=request(first,PaymentMethod.COD); String key=UUID.randomUUID().toString();
        var order=orders.place(first,key,body); assertEquals(order.id(),orders.place(first,key,body).id()); assertEquals(10,jdbc.queryForObject("SELECT stock_quantity FROM product_variants WHERE id=?",Integer.class,variant)); assertTrue(carts.view(first).items().isEmpty());
        String snapshotPrice=order.items().getFirst().unitPrice(); jdbc.update("UPDATE product_variants SET price=price+10000 WHERE id=?",variant);
        assertEquals(snapshotPrice,orders.detail(first,order.id()).items().getFirst().unitPrice()); jdbc.update("UPDATE product_variants SET price=price-10000 WHERE id=?",variant);
        assertEquals("PENDING",order.paymentStatus()); assertEquals(404,assertThrows(ApiException.class,()->orders.detail(second,order.id())).status());
    }
    @Test void changedRequestWithSameKeyFails() { carts.add(first,new AddItem(variant,1)); String key=UUID.randomUUID().toString(); var body=request(first,PaymentMethod.COD); orders.place(first,key,body); var changed=new PlaceOrderRequest("Other",body.receiverPhone(),body.shippingAddress(),body.paymentMethod(),body.cartVersion(),body.expectedTotal()); assertEquals("IDEMPOTENCY_CONFLICT",assertThrows(ApiException.class,()->orders.place(first,key,changed)).code()); }
    @Test void stalePriceOrVersionRequiresConfirmation() { carts.add(first,new AddItem(variant,1)); var body=request(first,PaymentMethod.COD); jdbc.update("UPDATE product_variants SET price=price+10000 WHERE id=?",variant); try { assertEquals("CHECKOUT_CHANGED",assertThrows(ApiException.class,()->orders.place(first,UUID.randomUUID().toString(),body)).code()); assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM orders",Integer.class)); } finally { jdbc.update("UPDATE product_variants SET price=price-10000 WHERE id=?",variant); } }
    @Test void onlyOneBuyerGetsLastUnit() throws Exception {
        jdbc.update("UPDATE product_variants SET stock_quantity=1 WHERE id=?",variant); carts.add(first,new AddItem(variant,1)); carts.add(second,new AddItem(variant,1)); var a=request(first,PaymentMethod.COD); var b=request(second,PaymentMethod.COD);
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Integer>> results=executor.invokeAll(List.<Callable<Integer>>of(()->attempt(first,a),()->attempt(second,b)));
            int successes=0; for(var result:results) successes+=result.get(10,TimeUnit.SECONDS); assertEquals(1,successes);
        }
        assertEquals(0,jdbc.queryForObject("SELECT stock_quantity FROM product_variants WHERE id=?",Integer.class,variant)); assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM orders",Integer.class));
    }
    int attempt(long customer,PlaceOrderRequest request) { try { orders.place(customer,UUID.randomUUID().toString(),request); return 1; } catch(ApiException error) { assertEquals("STOCK_UNAVAILABLE",error.code()); return 0; } }
    @Test void databaseWriteFailureRollsBackEveryOrderWrite() {
        carts.add(first,new AddItem(variant,1)); var body=request(first,PaymentMethod.COD);
        jdbc.execute("CREATE FUNCTION test_payment_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'Intentional payment persistence failure'; END $$");
        jdbc.execute("CREATE TRIGGER test_payment_failure BEFORE INSERT ON payments FOR EACH ROW EXECUTE FUNCTION test_payment_failure()");
        try { assertThrows(org.springframework.dao.DataAccessException.class,()->orders.place(first,UUID.randomUUID().toString(),body)); assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM orders",Integer.class)); assertEquals(12,jdbc.queryForObject("SELECT stock_quantity FROM product_variants WHERE id=?",Integer.class,variant)); assertEquals(1,carts.view(first).totalQuantity()); }
        finally { jdbc.execute("DROP TRIGGER test_payment_failure ON payments"); jdbc.execute("DROP FUNCTION test_payment_failure()"); }
    }
    @Test void filtersMustMatchOneVariantAndPricesVary() {
        Long product=jdbc.queryForObject("SELECT product_id FROM product_variants WHERE id=?",Long.class,variant);
        jdbc.update("UPDATE product_variants SET active=false WHERE product_id=?",product);
        jdbc.update("UPDATE product_variants SET active=true,size='TEST-M',color='TEST-BLACK',price=111000 WHERE id=?",variant);
        try { var result=catalog.page(new Conditions(null,List.of("TEST-M"),List.of("TEST-WHITE"),null,null,Sort.PRICE_ASC),null,0,24); assertEquals(0,result.total()); var matches=catalog.page(new Conditions(null,List.of("TEST-M"),List.of("TEST-BLACK"),new BigDecimal("100000"),new BigDecimal("120000"),Sort.PRICE_ASC),null,0,24); assertEquals(1,matches.total()); assertEquals("111000.00",matches.items().getFirst().minPrice()); }
        finally { jdbc.update("UPDATE product_variants SET size='S',color='Kem',price=189000 WHERE id=?",variant); jdbc.update("UPDATE product_variants SET active=true WHERE product_id=?",product); }
    }
    @Test void anonymousSearchIsPrivateAndAccentInsensitive() { var owner=new SearchService.SearchOwner(null,"session-one"); var response=searches.text(new SearchTypes.TextSearch("ao thun",new Conditions(null,List.of(),List.of(),null,null,Sort.RELEVANCE),0,24),owner); assertTrue(response.products().items().stream().anyMatch(p->p.name().equals("Áo thun Everyday"))); assertEquals(404,assertThrows(ApiException.class,()->searches.response(response.queryId(),new SearchService.SearchOwner(null,"session-two"),0,24)).status()); }
    @Test void loginCsrfValidationAndMediaBoundaries() throws Exception {
        http.perform(get("/api/v1/cart")).andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/auth/login").contentType("application/json").content("{\"email\":\"first@example.test\",\"password\":\"integration-password\"}")).andExpect(status().isForbidden());
        var result=http.perform(post("/api/v1/auth/login").with(csrf()).contentType("application/json").content("{\"email\":\"first@example.test\",\"password\":\"integration-password\"}")).andExpect(status().isOk()).andReturn();
        var cookies=result.getResponse().getCookies();
        http.perform(get("/api/v1/cart").cookie(cookies)).andExpect(status().isOk());
        http.perform(post("/api/v1/cart/items").cookie(cookies).with(csrf()).contentType("application/json").content("{\"variantId\":"+variant+",\"quantity\":0}")).andExpect(status().isBadRequest());
        http.perform(get("/api/v1/search/capabilities")).andExpect(status().isOk()).andExpect(jsonPath("$.voice").value(false));
        http.perform(multipart("/api/v1/search/voice").file("file",new byte[]{1,2,3}).with(csrf())).andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("SEARCH_PROVIDER_NOT_CONFIGURED"));
        http.perform(get("/api/v1/cart").cookie(cookies)).andExpect(jsonPath("$.items").isEmpty());
    }
    @Test void bankTransferIsPendingWithInstructions() { carts.add(first,new AddItem(variant,1)); var order=orders.place(first,UUID.randomUUID().toString(),request(first,PaymentMethod.BANK_TRANSFER)); assertEquals("PENDING",order.paymentStatus()); assertNotNull(order.bank()); assertEquals(order.orderNumber(),order.transferContent()); }
}
