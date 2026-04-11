package dev.golemcore.brain.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class HexagonalArchitectureTest {

    private static final Path MAIN_SOURCES = Path.of("src/main/java");
    private static final String BASE_PACKAGE = "dev.golemcore.brain";

    @Test
    void shouldKeepApplicationLayerIndependentFromAdaptersAndSpringInfrastructure() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path sourceFile : javaFiles(MAIN_SOURCES.resolve("dev/golemcore/brain/application"))) {
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
        for (Path sourceFile : javaFiles(MAIN_SOURCES.resolve("dev/golemcore/brain/domain"))) {
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
        for (Path sourceFile : javaFiles(MAIN_SOURCES.resolve("dev/golemcore/brain"))) {
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
        for (Path sourceFile : javaFiles(MAIN_SOURCES.resolve("dev/golemcore/brain/application/port/out"))) {
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
        return Files.readAllLines(sourceFile).stream()
                .map(String::trim)
                .filter(line -> line.endsWith(";"))
                .filter(line -> line.contains("("))
                .filter(line -> !line.startsWith("package "))
                .filter(line -> !line.startsWith("import "))
                .filter(line -> !line.startsWith("public interface "))
                .filter(line -> !line.startsWith("}"))
                .toList();
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
