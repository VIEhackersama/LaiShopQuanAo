package vn.laishop.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import vn.laishop.common.ApiException;
import static org.junit.jupiter.api.Assertions.*;
import static vn.laishop.catalog.CatalogTypes.*;
import static vn.laishop.search.SearchTypes.*;

class MediaSearchServiceVoiceTest {
    @Test void invalidTranscriptMarksCreatedQueryFailed() throws Exception {
        Fixture fixture=new Fixture(input->new Transcript("",3));
        ApiException error=assertThrows(ApiException.class,()->fixture.service.voice(fixture.webm(),3,fixture.owner));
        assertEquals(422,error.status());
        assertEquals("INVALID_TRANSCRIPT",error.code());
        assertTrue(fixture.jdbc.failed(41L));
    }

    @Test void adapterValidationErrorMarksCreatedQueryFailedAndStays422() throws Exception {
        Fixture fixture=new Fixture(input->{ throw new ApiException(422,"INVALID_DEMO_AUDIO","bad audio"); });
        ApiException error=assertThrows(ApiException.class,()->fixture.service.voice(fixture.webm(),3,fixture.owner));
        assertEquals(422,error.status());
        assertEquals("INVALID_DEMO_AUDIO",error.code());
        assertTrue(fixture.jdbc.failed(41L));
    }

    @Test void imageAdapterValidationErrorMarksQueryFailedAndStays422() throws Exception {
        RecordingJdbcTemplate jdbc=new RecordingJdbcTemplate();
        ImageSimilarityPort image=input->{ throw new ApiException(422,"UNDECODABLE_IMAGE","bad image"); };
        MediaSearchService service=new MediaSearchService(List.of(),List.of(image),new StubSearchService(),
                new StubMediaStore(),jdbc,new ImmediateTransactions(),null);
        Conditions conditions=new Conditions(null,List.of(),List.of(),null,null,Sort.RELEVANCE);
        ApiException error=assertThrows(ApiException.class,()->service.runImage(52L,
                new ImageInput(new byte[]{1},"image/png",null,conditions),
                new SearchService.SearchOwner(null,"image-test-session")));
        assertEquals(422,error.status());
        assertEquals("UNDECODABLE_IMAGE",error.code());
        assertTrue(jdbc.failed(52L));
    }

    private static final class Fixture {
        final RecordingJdbcTemplate jdbc=new RecordingJdbcTemplate();
        final SearchService.SearchOwner owner=new SearchService.SearchOwner(null,"voice-test-session");
        final MediaSearchService service;

        Fixture(SpeechRecognitionPort speech) throws Exception {
            service=new MediaSearchService(List.of(speech),List.of(),new StubSearchService(),
                    new StubMediaStore(),jdbc,new ImmediateTransactions(),null);
        }

        org.springframework.mock.web.MockMultipartFile webm() {
            byte[] bytes=new byte[32]; bytes[0]=0x1A; bytes[1]=0x45; bytes[2]=(byte)0xDF; bytes[3]=(byte)0xA3; bytes[8]=1;
            return new org.springframework.mock.web.MockMultipartFile("file","voice.webm","audio/webm",bytes);
        }
    }

    private static final class StubSearchService extends SearchService {
        StubSearchService() { super(null,null,new ObjectMapper()); }
        @Override public long create(String type,String status,Conditions conditions,SearchOwner owner) { return 41L; }
        @Override public SearchResponse response(long id,SearchOwner owner,int page,int size) { return null; }
    }

    private static final class StubMediaStore extends MediaStore {
        StubMediaStore() throws IOException { super(Files.createTempDirectory("voice-media-test").toString()); }
        @Override public String save(byte[] bytes) { return "00000000-0000-0000-0000-000000000001"; }
    }

    private static final class ImmediateTransactions extends TransactionTemplate {
        @Override public <T> T execute(TransactionCallback<T> action) { return action.doInTransaction(null); }
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private final List<Update> updates=new ArrayList<>();
        @Override public int update(String sql,Object... args) { updates.add(new Update(sql,args)); return 1; }
        boolean failed(long id) {
            return updates.stream().anyMatch(update->update.sql().equals("UPDATE search_queries SET status='FAILED' WHERE id=?")
                    && update.args().length==1 && update.args()[0].equals(id));
        }
        private record Update(String sql,Object[] args) { }
    }
}
