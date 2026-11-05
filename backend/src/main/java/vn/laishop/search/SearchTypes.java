package vn.laishop.search;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import vn.laishop.catalog.CatalogTypes.*;

public final class SearchTypes {
    private SearchTypes() { }
    public record TextSearch(@NotBlank @Size(max=100) String text,@NotNull @Valid Conditions conditions,@Min(0) int page,@Min(1) @Max(100) int pageSize) { }
    public record RefineSearch(@NotNull @Valid Conditions conditions,@Min(0) int page,@Min(1) @Max(100) int pageSize) { }
    public record VoiceExecute(@NotBlank @Size(max=100) String text,@NotNull @Valid Conditions conditions,@Min(0) int page,@Min(1) @Max(100) int pageSize) { }
    public record SearchResponse(long queryId,String type,String status,String text,Conditions conditions,ProductPage products) { }
    public record Capabilities(boolean voice,boolean image) { }
    public record Region(@DecimalMin("0") @DecimalMax("1") double x,@DecimalMin("0") @DecimalMax("1") double y,@DecimalMin(value="0",inclusive=false) @DecimalMax("1") double width,@DecimalMin(value="0",inclusive=false) @DecimalMax("1") double height) { }
    public record AudioInput(byte[] bytes,String contentType) { public AudioInput { bytes=bytes.clone(); } @Override public byte[] bytes() { return bytes.clone(); } }
    public record Transcript(String text,double durationSeconds) { }
    public record ImageInput(byte[] bytes,String contentType,Region region,Conditions conditions) { public ImageInput { bytes=bytes.clone(); } @Override public byte[] bytes() { return bytes.clone(); } }
    public record ImageMatch(long productId,BigDecimal score) { }
    public record ImageMatches(List<ImageMatch> items) { public ImageMatches { items=List.copyOf(items); } }
}
