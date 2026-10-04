package dev.sentinel;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class TestProjects {

    public static final String PLAIN_POM = """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>example</groupId><artifactId>plain</artifactId><version>1</version>
            </project>
            """;

    public static final String SPRING_BOOT_POM = """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <parent>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-starter-parent</artifactId>
                <version>4.0.8</version>
              </parent>
              <artifactId>app</artifactId>
            </project>
            """;

    private TestProjects() {
    }

    public static Path withPom(Path dir, String pom) {
        try {
            Files.writeString(dir.resolve("pom.xml"), pom);
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
