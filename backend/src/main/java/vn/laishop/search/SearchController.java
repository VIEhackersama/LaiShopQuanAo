package vn.laishop.search;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import static vn.laishop.search.SearchTypes.*;

@RestController
@RequestMapping("/api/v1/search")
public class SearchController {
    private final SearchService service;
    private final MediaSearchService media;
    public SearchController(SearchService service,MediaSearchService media) { this.service=service; this.media=media; }
    static SearchService.SearchOwner owner(Principal principal,HttpServletRequest request) {
        var session=request.getSession();
        String key=(String)session.getAttribute("searchOwner"); if(key==null) { key=UUID.randomUUID().toString(); session.setAttribute("searchOwner",key); }
        return new SearchService.SearchOwner(principal==null?null:Long.parseLong(principal.getName()),key);
    }
    @PostMapping("/text") SearchResponse text(@Valid @RequestBody TextSearch body,Principal p,HttpServletRequest r) { return service.text(body,owner(p,r)); }
    @GetMapping("/queries/{id}") SearchResponse get(@PathVariable long id,@RequestParam(required=false) Integer page,@RequestParam(required=false) Integer pageSize,Principal p,HttpServletRequest r) { return service.response(id,owner(p,r),page==null?0:page,pageSize==null?24:pageSize); }
    @PostMapping("/queries/{id}/refine") SearchResponse refine(@PathVariable long id,@Valid @RequestBody RefineSearch body,Principal p,HttpServletRequest r) throws java.io.IOException { var owner=owner(p,r); return service.owned(id,owner).type().equals("IMAGE")?media.refine(id,body,owner):service.refine(id,body,owner); }
    @PostMapping("/voice/{id}/execute") SearchResponse execute(@PathVariable long id,@Valid @RequestBody VoiceExecute body,Principal p,HttpServletRequest r) { return service.executeVoice(id,body,owner(p,r)); }
}
