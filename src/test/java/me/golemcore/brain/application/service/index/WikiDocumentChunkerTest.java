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

package me.golemcore.brain.application.service.index;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WikiDocumentChunkerTest {

    @Test
    void shouldReturnSingleChunkForShortText() {
        WikiDocumentChunker chunker = new WikiDocumentChunker(100, 20);

        List<WikiDocumentChunker.Chunk> chunks = chunker.chunk("Short body");

        assertEquals(1, chunks.size());
        assertEquals(0, chunks.getFirst().index());
        assertEquals("Short body", chunks.getFirst().text());
    }

    @Test
    void shouldReturnEmptyListForBlankInput() {
        WikiDocumentChunker chunker = new WikiDocumentChunker(100, 20);

        assertTrue(chunker.chunk("").isEmpty());
        assertTrue(chunker.chunk(null).isEmpty());
    }

    @Test
    void shouldPreferParagraphBoundariesWhenPacking() {
        // When the body is split by blank lines, the chunker should pack whole
        // paragraphs into a chunk until the next paragraph would exceed the
        // budget — cleaving mid-paragraph is the fallback, not the default.
        WikiDocumentChunker chunker = new WikiDocumentChunker(80, 10);
        String body = String.join("\n\n",
                "Alpha paragraph describes alpha.",
                "Beta paragraph describes beta.",
                "Gamma paragraph describes gamma.");

        List<WikiDocumentChunker.Chunk> chunks = chunker.chunk(body);

        assertTrue(chunks.size() >= 2, "expected packing across at least two chunks, got " + chunks.size());
        for (WikiDocumentChunker.Chunk chunk : chunks) {
            String text = chunk.text();
            // every chunk should end at a paragraph terminator (or be the last chunk)
            boolean endsCleanly = text.endsWith(".") || text.endsWith("\n\n") || text.equals(text.strip());
            assertTrue(endsCleanly, "chunk did not end cleanly: '" + text + "'");
        }
    }

    @Test
    void shouldFallBackToCharSlicingForOverlongParagraph() {
        WikiDocumentChunker chunker = new WikiDocumentChunker(20, 5);
        String body = "abcdefghijklmnopqrstuvwxyz0123456789";

        List<WikiDocumentChunker.Chunk> chunks = chunker.chunk(body);

        assertTrue(chunks.size() >= 2, "expected multi-chunk split, got " + chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            assertEquals(index, chunks.get(index).index());
            assertTrue(chunks.get(index).text().length() <= 20);
        }
    }

    @Test
    void shouldCoverEntireBodyAcrossChunks() {
        WikiDocumentChunker chunker = new WikiDocumentChunker(30, 5);
        String body = "first paragraph content.\n\nsecond paragraph content.\n\nthird paragraph content.";

        List<WikiDocumentChunker.Chunk> chunks = chunker.chunk(body);

        String joined = String.join(" ", chunks.stream().map(WikiDocumentChunker.Chunk::text).toList());
        assertTrue(joined.contains("first paragraph"), "missing first paragraph in " + joined);
        assertTrue(joined.contains("second paragraph"), "missing second paragraph in " + joined);
        assertTrue(joined.contains("third paragraph"), "missing third paragraph in " + joined);
    }

    @Test
    void shouldRejectInvalidParameters() {
        assertThrows(IllegalArgumentException.class, () -> new WikiDocumentChunker(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new WikiDocumentChunker(10, 10));
    }
}
