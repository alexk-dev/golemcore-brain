/*
 * Copyright 2026 Aleksei Kuleshov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contact: alex@kuleshov.tech
 */

package me.golemcore.brain.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AssetMimeGuardTest {

    private static final byte[] PNG_HEADER = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 0, 0, 0, 0, 0 };
    private static final byte[] JPEG_HEADER = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0, 0 };
    private static final byte[] GIF_HEADER = { 'G', 'I', 'F', '8', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
    private static final byte[] WEBP_HEADER = { 'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P',
            0, 0, 0, 0 };
    private static final byte[] PDF_HEADER = { '%', 'P', 'D', 'F', '-', 0, 0, 0, 0, 0, 0, 0 };
    private static final byte[] HTML_HEADER = "<html><body>".getBytes();

    @Test
    void shouldAcceptPngWithCorrectMagic() {
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", PNG_HEADER);
        assertDoesNotThrow(() -> AssetMimeGuard.validate(file));
    }

    @Test
    void shouldAcceptJpegWithCorrectMagic() {
        MockMultipartFile file = new MockMultipartFile("file", "x.jpg", "image/jpeg", JPEG_HEADER);
        assertDoesNotThrow(() -> AssetMimeGuard.validate(file));
    }

    @Test
    void shouldAcceptGifWithCorrectMagic() {
        MockMultipartFile file = new MockMultipartFile("file", "x.gif", "image/gif", GIF_HEADER);
        assertDoesNotThrow(() -> AssetMimeGuard.validate(file));
    }

    @Test
    void shouldAcceptWebpWithCorrectMagic() {
        MockMultipartFile file = new MockMultipartFile("file", "x.webp", "image/webp", WEBP_HEADER);
        assertDoesNotThrow(() -> AssetMimeGuard.validate(file));
    }

    @Test
    void shouldAcceptPdfWithCorrectMagic() {
        MockMultipartFile file = new MockMultipartFile("file", "x.pdf", "application/pdf", PDF_HEADER);
        assertDoesNotThrow(() -> AssetMimeGuard.validate(file));
    }

    @Test
    void shouldRejectPngDeclaredButHtmlContent() {
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", HTML_HEADER);
        assertThrows(IllegalArgumentException.class, () -> AssetMimeGuard.validate(file));
    }

    @Test
    void shouldRejectSvgWhichIsNotAllowListed() {
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "x.svg", "image/svg+xml", svg);
        assertThrows(IllegalArgumentException.class, () -> AssetMimeGuard.validate(file));
    }

    @Test
    void shouldRejectExecutableMime() {
        MockMultipartFile file = new MockMultipartFile("file", "x.sh", "application/x-sh", "echo".getBytes());
        assertThrows(IllegalArgumentException.class, () -> AssetMimeGuard.validate(file));
    }

    @Test
    void shouldRejectMissingContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "x.png", null, PNG_HEADER);
        assertThrows(IllegalArgumentException.class, () -> AssetMimeGuard.validate(file));
    }

    @Test
    void shouldRejectEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "x.png", "image/png", new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> AssetMimeGuard.validate(file));
    }

    @Test
    void shouldAcceptTextPlainWithoutMagicCheck() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());
        assertDoesNotThrow(() -> AssetMimeGuard.validate(file));
    }
}
