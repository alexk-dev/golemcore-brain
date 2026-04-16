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

import java.util.ArrayList;
import java.util.List;

/**
 * Paragraph-aware chunker: splits a body into chunks by packing whole
 * paragraphs under a character budget, and falls back to char slicing only when
 * a single paragraph is larger than the budget. This preserves paragraph-level
 * semantics for embeddings — cleaving a sentence mid-word materially hurts
 * dense retrieval quality.
 */
public final class WikiDocumentChunker {

    private final int chunkSize;
    private final int overlap;

    public WikiDocumentChunker(int chunkSize, int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be non-negative and smaller than chunkSize");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public List<Chunk> chunk(String body) {
        String safeBody = body == null ? "" : body.strip();
        if (safeBody.isEmpty()) {
            return List.of();
        }
        if (safeBody.length() <= chunkSize) {
            return List.of(new Chunk(0, safeBody));
        }
        List<String> paragraphs = splitParagraphs(safeBody);
        List<String> packed = packParagraphs(paragraphs);
        List<Chunk> chunks = new ArrayList<>();
        int index = 0;
        for (String text : packed) {
            chunks.add(new Chunk(index, text));
            index++;
        }
        return chunks;
    }

    private List<String> splitParagraphs(String body) {
        List<String> paragraphs = new ArrayList<>();
        for (String candidate : body.split("\\n{2,}")) {
            String trimmed = candidate.strip();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }
        return paragraphs;
    }

    private List<String> packParagraphs(List<String> paragraphs) {
        List<String> packed = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            if (paragraph.length() > chunkSize) {
                flush(current, packed);
                packed.addAll(sliceOverlongParagraph(paragraph));
                continue;
            }
            int projectedLength = current.length() == 0 ? paragraph.length()
                    : current.length() + 2 + paragraph.length();
            if (projectedLength > chunkSize) {
                flush(current, packed);
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(paragraph);
        }
        flush(current, packed);
        return packed;
    }

    private void flush(StringBuilder current, List<String> packed) {
        if (current.length() == 0) {
            return;
        }
        packed.add(current.toString());
        current.setLength(0);
    }

    private List<String> sliceOverlongParagraph(String paragraph) {
        List<String> slices = new ArrayList<>();
        int cursor = 0;
        int step = chunkSize - overlap;
        while (cursor < paragraph.length()) {
            int end = Math.min(cursor + chunkSize, paragraph.length());
            slices.add(paragraph.substring(cursor, end));
            if (end == paragraph.length()) {
                break;
            }
            cursor += step;
        }
        return slices;
    }

    public record Chunk(int index, String text) {
    }
}
