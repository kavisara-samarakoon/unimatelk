package com.unimatelk.service;

import com.unimatelk.support.UploadTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImageValidatorTest {
    @ParameterizedTest
    @ValueSource(strings = {"png", "jpeg", "webp"})
    void acceptsSupportedContentWithoutTrustingSuppliedMimeOrFilename(String format) throws Exception {
        byte[] bytes = UploadTestSupport.image(format);
        var validated = ImageValidator.upload(new MockMultipartFile("file", "../../unsafe.svg", "text/html", bytes));
        assertArrayEquals(bytes, validated.bytes());
        assertEquals("image/" + format, validated.format().contentType().toString());
    }

    @Test
    void rejectsNullAndEmptyFiles() {
        assertStatus(400, () -> ImageValidator.upload(null));
        assertStatus(400, () -> ImageValidator.upload(new MockMultipartFile("file", new byte[0])));
    }

    @Test
    void rejectsOversizedFiles() {
        assertStatus(413, () -> ImageValidator.upload(new MockMultipartFile("file",
                new byte[ImageValidator.MAX_UPLOAD_BYTES + 1])));
    }

    @Test
    void boundsActualStreamEvenIfReportedSizeLies() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(1L);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[ImageValidator.MAX_UPLOAD_BYTES + 1]));
        assertStatus(413, () -> ImageValidator.upload(file));
    }

    @Test
    void closesTheMultipartStreamAfterValidation() throws Exception {
        var input = spy(new ByteArrayInputStream(UploadTestSupport.image("png")));
        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenReturn(input);
        ImageValidator.upload(file);
        verify(input).close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"<html>synthetic</html>", "<svg xmlns='http://www.w3.org/2000/svg'/>",
            "alert('synthetic')", "not an image", "GIF89a"})
    void spoofedImageMimeCannotMakeUnsupportedBytesAcceptable(String content) {
        assertStatus(400, () -> ImageValidator.upload(new MockMultipartFile("file", "photo.png", "image/png", content.getBytes())));
    }

    @Test
    void rejectsTruncatedImageData() throws Exception {
        byte[] bytes = UploadTestSupport.image("png");
        assertStatus(400, () -> ImageValidator.upload(new MockMultipartFile("file", Arrays.copyOf(bytes, 35))));
    }

    @Test
    void rejectsUnreadablePartWithControlledClientError() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenThrow(new IOException("Synthetic read failure"));
        assertStatus(400, () -> ImageValidator.upload(file));
    }

    @Test
    void boundsDimensionsAndPixelCountBeforeDecoding() throws Exception {
        assertStatus(400, () -> ImageValidator.inspect(pngHeader(16_385, 2)));
        assertStatus(400, () -> ImageValidator.inspect(pngHeader(8_000, 6_000)));
    }

    @Test
    void acceptsTheExactFiveMiBBoundary() throws Exception {
        byte[] bytes = Arrays.copyOf(UploadTestSupport.image("png"), ImageValidator.MAX_UPLOAD_BYTES);
        assertEquals(ImageValidator.Format.PNG, ImageValidator.upload(new MockMultipartFile("file", bytes)).format());
    }

    private static byte[] pngHeader(int width, int height) throws IOException {
        byte[] bytes = UploadTestSupport.image("png");
        ByteBuffer.wrap(bytes).putInt(16, width).putInt(20, height);
        CRC32 crc = new CRC32();
        crc.update(bytes, 12, 17);
        ByteBuffer.wrap(bytes).putInt(29, (int) crc.getValue());
        return bytes;
    }

    private static void assertStatus(int status, org.junit.jupiter.api.function.Executable action) {
        assertEquals(status, assertThrows(ResponseStatusException.class, action).getStatusCode().value());
    }
}
