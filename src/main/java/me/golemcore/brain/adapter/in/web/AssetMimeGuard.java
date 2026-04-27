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

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import org.springframework.web.multipart.MultipartFile;

/**
 * Allow-listed MIME types for asset uploads. SVG, HTML and other script-bearing
 * types are intentionally excluded because assets are served back with
 * {@code Content-Disposition: inline}. For declared image MIME types the first
 * bytes of the upload are validated against the format magic-number, so renamed
 * executables disguised as images are rejected.
 */
public final class AssetMimeGuard {

    private AssetMimeGuard() {
    }

    private static final Set<String> ALLOWED_MIMES = Set.of(
            "image/png",
            "image/jpeg",
            "image/gif",
            "image/webp",
            "application/pdf",
            "text/plain",
            "text/markdown",
            "audio/mpeg",
            "audio/wav",
            "audio/ogg",
            "video/mp4",
            "video/webm",
            "video/ogg");

    public static void validate(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        String declared = normalize(file.getContentType());
        if (declared == null || !ALLOWED_MIMES.contains(declared)) {
            throw new IllegalArgumentException("Unsupported content type: " + file.getContentType());
        }
        if (declared.startsWith("image/") || "application/pdf".equals(declared)) {
            byte[] head = readHead(file, 12);
            if (!matchesMagic(declared, head)) {
                throw new IllegalArgumentException(
                        "File contents do not match declared content type " + declared);
            }
        }
    }

    private static String normalize(String contentType) {
        if (contentType == null) {
            return null;
        }
        int semicolon = contentType.indexOf(';');
        String trimmed = (semicolon < 0 ? contentType : contentType.substring(0, semicolon)).trim();
        return trimmed.isEmpty() ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private static byte[] readHead(MultipartFile file, int size) throws IOException {
        try (InputStream in = file.getInputStream()) {
            byte[] buffer = new byte[size];
            int read = 0;
            while (read < size) {
                int n = in.read(buffer, read, size - read);
                if (n < 0) {
                    break;
                }
                read += n;
            }
            if (read < size) {
                byte[] truncated = new byte[read];
                System.arraycopy(buffer, 0, truncated, 0, read);
                return truncated;
            }
            return buffer;
        }
    }

    private static boolean matchesMagic(String mime, byte[] head) {
        return switch (mime) {
        case "image/png" -> startsWith(head, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
        case "image/jpeg" -> startsWith(head, 0xFF, 0xD8, 0xFF);
        case "image/gif" -> startsWith(head, 0x47, 0x49, 0x46, 0x38);
        case "image/webp" -> head.length >= 12
                && startsWith(head, 0x52, 0x49, 0x46, 0x46)
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P';
        case "application/pdf" -> startsWith(head, 0x25, 0x50, 0x44, 0x46, 0x2D); // %PDF-
        default -> true;
        };
    }

    private static boolean startsWith(byte[] data, int... prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if ((data[i] & 0xff) != (prefix[i] & 0xff)) {
                return false;
            }
        }
        return true;
    }
}
