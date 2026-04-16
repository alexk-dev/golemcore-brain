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

package me.golemcore.brain.adapter.out.index;

import me.golemcore.brain.adapter.out.index.memory.InMemoryWikiEmbeddingIndexAdapter;
import me.golemcore.brain.adapter.out.index.sqlite.SqliteWikiEmbeddingIndexAdapter;
import me.golemcore.brain.application.port.out.WikiEmbeddingIndexPort;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SuppressWarnings("PMD.TestClassWithoutTestCases")
class EmbeddingIndexAdapterSelectionTest {

    @SpringBootTest(properties = { "brain.indexing.embedding-adapter=sqlite" })
    static class SqliteAdapterActivation {
        @TempDir
        static Path tempDir;

        @DynamicPropertySource
        static void properties(DynamicPropertyRegistry registry) {
            registry.add("brain.storage-root", () -> tempDir.toString());
        }

        @Autowired
        WikiEmbeddingIndexPort port;

        @Test
        void shouldUseSqliteAdapterByDefault() {
            assertInstanceOf(SqliteWikiEmbeddingIndexAdapter.class, port);
        }
    }

    @SpringBootTest(properties = { "brain.indexing.embedding-adapter=in-memory" })
    static class InMemoryAdapterActivation {
        @TempDir
        static Path tempDir;

        @DynamicPropertySource
        static void properties(DynamicPropertyRegistry registry) {
            registry.add("brain.storage-root", () -> tempDir.toString());
        }

        @Autowired
        WikiEmbeddingIndexPort port;

        @Test
        void shouldUseInMemoryAdapterWhenConfigured() {
            assertInstanceOf(InMemoryWikiEmbeddingIndexAdapter.class, port);
        }
    }
}
