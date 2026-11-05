package vn.laishop.search;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import vn.laishop.search.ai.AiSearchService;
import static vn.laishop.search.ai.AiTypes.*;

@RestController
@RequestMapping("/api/v1/search/ai")
public class AiSearchController {
    private final AiSearchService service;
    public AiSearchController(AiSearchService service) { this.service=service; }
    @GetMapping("/capabilities") AiCapabilities capabilities() { return service.capabilities(); }
    @PostMapping("/chat") ChatResponse chat(@Valid @RequestBody ChatRequest request,Principal p,HttpServletRequest r) { return service.chat(request,SearchController.owner(p,r)); }
    @GetMapping("/conversations/{id}") Conversation conversation(@PathVariable UUID id,Principal p,HttpServletRequest r) { return service.conversation(id,SearchController.owner(p,r)); }
}
