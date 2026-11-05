package vn.laishop.cart;

import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.web.bind.annotation.*;
import static vn.laishop.cart.CartTypes.*;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {
    private final CartService service;
    public CartController(CartService service) { this.service=service; }
    @GetMapping CartResponse view(Principal p) { return service.view(Long.parseLong(p.getName())); }
    @PostMapping("/items") CartResponse add(Principal p,@Valid @RequestBody AddItem body) { return service.add(Long.parseLong(p.getName()),body); }
    @PatchMapping("/items/{id}") CartResponse update(Principal p,@PathVariable long id,@Valid @RequestBody UpdateItem body) { return service.update(Long.parseLong(p.getName()),id,body); }
    @DeleteMapping("/items/{id}") CartResponse remove(Principal p,@PathVariable long id) { return service.remove(Long.parseLong(p.getName()),id); }
    @DeleteMapping("/items") CartResponse clear(Principal p) { return service.clear(Long.parseLong(p.getName())); }
}
