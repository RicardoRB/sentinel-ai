package dev.sentinel;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureBoundaryTest {

    @Test
    void domainSourceDoesNotImportAdaptersOrFrameworks() throws IOException {
        Path source = Path.of("src/main/java/dev/sentinel/domain");
        try (Stream<Path> files = Files.walk(source)) {
            List<String> violations = files.filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> {
                        try {
                            return Files.readAllLines(path).stream()
                                    .filter(line -> line.startsWith("import "))
                                    .filter(line -> line.contains("com.google.inject")
                                            || line.contains("picocli")
                                            || line.contains("java.lang.Process")
                                            || line.contains("java.nio.file.Files")
                                            || line.contains("java.nio.file.FileSystem")
                                            || line.contains("org.springframework"))
                                    .map(line -> path + ": " + line);
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    }).toList();
            assertThat(violations).as("domain dependency violations").isEmpty();
        }
    }

    @Test
    void pomDoesNotDeclareSpring() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"));
        assertThat(pom).doesNotContain("spring-boot").doesNotContain("org.springframework");
    }
}
