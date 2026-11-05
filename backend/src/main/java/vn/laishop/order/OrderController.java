package vn.laishop.order;

import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;
import static vn.laishop.order.OrderTypes.*;

@RestController
@RequestMapping("/api/v1")
public class OrderController {
    private final OrderService service;
    public OrderController(OrderService service) { this.service=service; }
    @GetMapping("/checkout") CheckoutResponse checkout(Principal p) { return service.checkout(Long.parseLong(p.getName())); }
    @PostMapping("/orders") OrderResponse place(Principal p,@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody PlaceOrderRequest body) { return service.place(Long.parseLong(p.getName()),key,body); }
    @GetMapping("/orders/{id}") OrderResponse detail(Principal p,@PathVariable long id) { return service.detail(Long.parseLong(p.getName()),id); }
    @GetMapping("/orders") OrderPage list(Principal p,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize) { return service.list(Long.parseLong(p.getName()),page==null?0:page,pageSize==null?24:pageSize); }
}
