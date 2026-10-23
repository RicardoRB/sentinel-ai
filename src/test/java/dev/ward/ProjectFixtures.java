package dev.ward;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Minimal project files for tests. */
public final class ProjectFixtures {

  public static final String PLAIN_POM =
      """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>example</groupId><artifactId>plain</artifactId><version>1</version>
            </project>
            """;

  public static final String SPRING_BOOT_POM =
      """
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

  private ProjectFixtures() {}

  /** Writes {@code pom} as the {@code pom.xml} of {@code dir} and returns {@code dir}. */
  public static Path withPom(final Path dir, final String pom) {
    try {
      Files.writeString(dir.resolve("pom.xml"), pom);
      return dir;
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Returns a plain Maven project rooted at {@code root}, outside any nested layout. */
  public static Project maven(final Path root) {
    return new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  }
}
