package vn.laishop.catalog;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import static vn.laishop.catalog.CatalogTypes.*;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
    private final CatalogDao catalog;
    public CatalogController(CatalogDao catalog) { this.catalog=catalog; }
    @GetMapping("/categories") List<CategoryResponse> categories() { return catalog.categories(); }
    @GetMapping("/products") ProductPage products(@Valid @ModelAttribute Conditions conditions,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize) { return catalog.page(conditions,null,page==null?0:page,pageSize==null?24:pageSize); }
    @GetMapping("/products/{id}") ProductResponse product(@PathVariable long id) { return catalog.product(id); }
}
