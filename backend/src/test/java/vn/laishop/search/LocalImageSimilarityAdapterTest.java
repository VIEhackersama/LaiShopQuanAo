package vn.laishop.search;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import vn.laishop.common.ApiException;
import static org.junit.jupiter.api.Assertions.*;
import static vn.laishop.search.SearchTypes.*;

class LocalImageSimilarityAdapterTest {
    @Test void describesDecodedPixelsAndAppliesCrop() throws Exception {
        BufferedImage image = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 10; y++) for (int x = 0; x < 20; x++) image.setRGB(x, y, (x < 10 ? Color.RED : Color.BLUE).getRGB());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);

        double[] right = LocalImageSimilarityAdapter.describe(bytes.toByteArray(), new Region(.5, 0, .5, 1));
        assertArrayEquals(new double[]{0, 0, 255}, right, .01);
    }

    @Test void scoresNearestColourHighest() {
        double[] query = {230, 225, 215};
        assertTrue(LocalImageSimilarityAdapter.similarity(query, new double[]{232, 223, 214})
                > LocalImageSimilarityAdapter.similarity(query, new double[]{40, 40, 40}));
    }

    @Test void rejectsUndecodableBytesAndMalformedEmbeddings() {
        assertEquals("UNDECODABLE_IMAGE", assertThrows(ApiException.class,
                () -> LocalImageSimilarityAdapter.describe(new byte[]{1, 2, 3}, null)).code());
        assertThrows(IllegalStateException.class, () -> LocalImageSimilarityAdapter.parseEmbedding("{\"rgb\":1}"));
    }

    @Test void rejectsOversizedImageBeforePixelAllocation() throws Exception {
        ApiException error = assertThrows(ApiException.class,
                () -> LocalImageSimilarityAdapter.describe(pngHeader(10_000, 10_000), null, 16_000_000));
        assertEquals("IMAGE_DIMENSIONS_EXCEEDED", error.code());
    }

    @Test void parsesValidEmbeddingAndRejectsOutOfRangeValues() {
        assertArrayEquals(new double[]{232, 229, 220},
                LocalImageSimilarityAdapter.parseEmbedding("[232,229,220]"), .001);
        assertThrows(IllegalStateException.class,
                () -> LocalImageSimilarityAdapter.parseEmbedding("[300,0,0]"));
    }

    private static byte[] pngHeader(int width, int height) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream data = new DataOutputStream(output);
        data.write(new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10});
        ByteArrayOutputStream contents = new ByteArrayOutputStream();
        DataOutputStream chunk = new DataOutputStream(contents);
        chunk.writeInt(width); chunk.writeInt(height);
        chunk.writeByte(8); chunk.writeByte(2); chunk.writeByte(0); chunk.writeByte(0); chunk.writeByte(0);
        byte[] body = contents.toByteArray();
        data.writeInt(body.length); data.writeBytes("IHDR"); data.write(body);
        CRC32 crc = new CRC32(); crc.update("IHDR".getBytes(java.nio.charset.StandardCharsets.US_ASCII)); crc.update(body);
        data.writeInt((int) crc.getValue());
        return output.toByteArray();
    }
}
