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

package me.golemcore.brain.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HexagonalArchitectureTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");
    private static final String BASE_PACKAGE = "me.golemcore.brain";

    @Test
    void shouldKeepApplicationLayerIndependentFromAdaptersAndSpringInfrastructure() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path sourceFile : javaFiles(MAIN_SOURCES.resolve("me/golemcore/brain/application"))) {
            for (String importedType : importsOf(sourceFile)) {
                if (importedType.startsWith(BASE_PACKAGE + ".adapter.")
                        || importedType.startsWith(BASE_PACKAGE + ".config.")
                        || importedType.startsWith("org.springframework.")) {
                    violations.add(sourceFile + " imports " + importedType);
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> "Application layer must depend only on domain and ports:\n"
                + String.join("\n", violations));
    }

    @Test
    void shouldKeepDomainLayerFreeFromFrameworkSerializationAnnotations() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path sourceFile : javaFiles(MAIN_SOURCES.resolve("me/golemcore/brain/domain"))) {
            for (String importedType : importsOf(sourceFile)) {
                if (importedType.startsWith("com.fasterxml.jackson.")
                        || importedType.startsWith("org.springframework.")
                        || importedType.startsWith("jakarta.")) {
                    violations.add(sourceFile + " imports " + importedType);
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> "Domain layer must be framework independent:\n"
                + String.join("\n", violations));
    }

    @Test
    void shouldKeepSpringBeanAssemblyOutsideApplicationAndDomainLayers() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path sourceFile : javaFiles(MAIN_SOURCES.resolve("me/golemcore/brain"))) {
            String source = Files.readString(sourceFile);
            if (isApplicationOrDomainSource(sourceFile) && source.contains("@Bean")) {
                violations.add(sourceFile + " declares a Spring bean");
            }
            if (isSpringAssemblySource(sourceFile) && source.contains("@Bean") && !source.contains("@Configuration")) {
                violations.add(sourceFile + " declares @Bean outside a @Configuration class");
            }
        }

        assertTrue(violations.isEmpty(), () -> "Spring bean assembly must stay outside application/domain:\n"
                + String.join("\n", violations));
    }

    @Test
    void shouldRequireSpaceIdOnIndexPortPublicMethods() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path sourceFile : javaFiles(MAIN_SOURCES.resolve("me/golemcore/brain/application/port/out"))) {
            String fileName = sourceFile.getFileName().toString();
            if (!fileName.endsWith("IndexPort.java")) {
                continue;
            }
            for (String methodSignature : publicMethodSignatures(sourceFile)) {
                if (!methodSignature.contains("String spaceId")) {
                    violations.add(sourceFile + " exposes a space-less index method: " + methodSignature);
                }
            }
        }

        assertTrue(violations.isEmpty(), () -> "Index ports must require spaceId on every public method:\n"
                + String.join("\n", violations));
    }

    private static List<Path> javaFiles(Path root) throws IOException {
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            return stream.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    private static boolean isApplicationOrDomainSource(Path sourceFile) {
        String normalized = sourceFile.toString().replace('\\', '/');
        return normalized.contains("/application/") || normalized.contains("/domain/");
    }

    private static boolean isSpringAssemblySource(Path sourceFile) {
        String normalized = sourceFile.toString().replace('\\', '/');
        return !normalized.contains("/application/") && !normalized.contains("/domain/");
    }

    private static List<String> publicMethodSignatures(Path sourceFile) throws IOException {
        return javaSourceLines(sourceFile).stream()
                .map(String::trim)
                .filter(line -> line.endsWith(";"))
                .filter(line -> line.contains("("))
                .filter(line -> !line.startsWith("package "))
                .filter(line -> !line.startsWith("import "))
                .filter(line -> !line.startsWith("public interface "))
                .filter(line -> !line.startsWith("}"))
                .toList();
    }

    private static List<String> javaSourceLines(Path sourceFile) throws IOException {
        List<String> sourceLines = new ArrayList<>();
        boolean inBlockComment = false;
        for (String line : Files.readAllLines(sourceFile)) {
            String remaining = line;
            while (!remaining.isEmpty()) {
                if (inBlockComment) {
                    int commentEnd = remaining.indexOf("*/");
                    if (commentEnd < 0) {
                        remaining = "";
                    } else {
                        remaining = remaining.substring(commentEnd + 2);
                        inBlockComment = false;
                    }
                    continue;
                }

                int commentStart = remaining.indexOf("/*");
                if (commentStart < 0) {
                    sourceLines.add(remaining);
                    remaining = "";
                } else {
                    sourceLines.add(remaining.substring(0, commentStart));
                    remaining = remaining.substring(commentStart + 2);
                    inBlockComment = true;
                }
            }
        }
        return sourceLines;
    }

    private static List<String> importsOf(Path sourceFile) throws IOException {
        return Files.readAllLines(sourceFile).stream()
                .map(String::trim)
                .filter(line -> line.startsWith("import "))
                .filter(line -> !line.startsWith("import static "))
                .map(line -> line.substring("import ".length(), line.length() - 1))
                .toList();
    }
}
