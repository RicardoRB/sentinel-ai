package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** JVM languages come from the source directories, never from the build script. */
class JvmSourceLanguagesTest {
  @TempDir Path dir;

  @Test
  void javaSourcesWithKotlinBuildScriptAreJava() throws IOException {
    Files.writeString(dir.resolve("build.gradle.kts"), "plugins { java }\n");
    Files.createDirectories(dir.resolve("src/main/java"));

    final Project project = detect();

    assertThat(project.language()).isEqualTo(Language.JAVA);
    assertThat(project.languages()).containsExactly(Language.JAVA);
  }

  @Test
  void onlyKotlinSourcesAreKotlin() throws IOException {
    Files.writeString(dir.resolve("build.gradle"), "");
    Files.createDirectories(dir.resolve("src/main/kotlin"));

    final Project project = detect();

    assertThat(project.language()).isEqualTo(Language.KOTLIN);
    assertThat(project.languages()).containsExactly(Language.KOTLIN);
  }

  @Test
  void mixedModulesListBothWithJavaFirst() throws IOException {
    Files.writeString(dir.resolve("settings.gradle.kts"), "include(\"api\", \"core\")\n");
    Files.writeString(dir.resolve("build.gradle.kts"), "");
    Files.createDirectories(dir.resolve("api/src/main/kotlin"));
    Files.createDirectories(dir.resolve("core/src/main/java"));

    final Project project = detect();

    assertThat(project.language()).isEqualTo(Language.JAVA);
    assertThat(project.languages()).containsExactly(Language.JAVA, Language.KOTLIN);
  }

  @Test
  void noSourcesFallBackToJava() throws IOException {
    Files.writeString(dir.resolve("pom.xml"), "<project/>");

    assertThat(detect().languages()).containsExactly(Language.JAVA);
  }

  @Test
  void buildOutputAndHiddenDirectoriesAreIgnored() throws IOException {
    Files.writeString(dir.resolve("build.gradle"), "");
    Files.createDirectories(dir.resolve("src/main/java"));
    Files.createDirectories(dir.resolve("build/generated/src/main/kotlin"));
    Files.createDirectories(dir.resolve(".gradle/src/main/kotlin"));

    assertThat(detect().languages()).containsExactly(Language.JAVA);
  }

  private Project detect() {
    return FileSystemProjectInspection.projectAt(dir).orElseThrow();
  }
}
