package vn.laishop.search;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SearchRetention {
    private final JdbcTemplate jdbc;
    private final MediaStore media;
    public SearchRetention(JdbcTemplate jdbc,MediaStore media) { this.jdbc=jdbc; this.media=media; }
    @Scheduled(cron="0 0 3 * * *") public void clean() throws IOException {
        jdbc.update("DELETE FROM search_queries WHERE created_at < now()-interval '7 days'");
        var referenced=new HashSet<>(jdbc.query("SELECT audio_url FROM voice_queries UNION SELECT image_url FROM image_queries",(r,n)->r.getString(1)));
        try(var paths=Files.list(media.root())) { for(var path:paths.toList()) if(Files.isRegularFile(path) && Files.getLastModifiedTime(path).toInstant().isBefore(Instant.now().minus(7,ChronoUnit.DAYS)) && !referenced.contains(path.getFileName().toString())) media.delete(path.getFileName().toString()); }
    }
}
