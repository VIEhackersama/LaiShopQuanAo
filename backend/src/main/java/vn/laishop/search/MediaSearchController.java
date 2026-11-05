package vn.laishop.search;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.security.Principal;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import static vn.laishop.search.SearchTypes.*;

@RestController
@RequestMapping("/api/v1/search")
public class MediaSearchController {
    private final MediaSearchService media;
    public MediaSearchController(MediaSearchService media) { this.media=media; }
    @GetMapping("/capabilities") Capabilities capabilities() { return media.capabilities(); }
    @PostMapping(value="/voice",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    SearchResponse voice(@RequestPart("file") MultipartFile file,Principal p,HttpServletRequest request) throws IOException { return media.voice(file,SearchController.owner(p,request)); }
    @PostMapping(value="/image",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    SearchResponse image(@RequestPart("file") MultipartFile file,@Valid @RequestPart("options") MediaSearchService.ImageOptions options,Principal p,HttpServletRequest request) throws IOException { return media.image(file,options,SearchController.owner(p,request)); }
    @GetMapping("/queries/{id}/media") ResponseEntity<byte[]> read(@PathVariable long id,Principal p,HttpServletRequest request) throws IOException { return media.read(id,SearchController.owner(p,request)); }
}
