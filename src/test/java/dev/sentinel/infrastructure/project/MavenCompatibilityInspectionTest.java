package dev.sentinel.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.FakeCommandExecutor;
import dev.sentinel.domain.init.CompatibilityProjectSnapshot;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MavenCompatibilityInspectionTest {
  private static final int JAVA_17 = 17;

  @Test
  void readsToolchainPropertiesAndDeclaredPluginVersions(@TempDir final Path root)
      throws Exception {
    Files.writeString(
        root.resolve("pom.xml"),
        """
        <project>
          <properties><java.version>17</java.version></properties>
          <build><plugins><plugin>
            <artifactId>spotless-maven-plugin</artifactId><version>2.44.4</version>
          </plugin></plugins></build>
        </project>
        """);
    final FakeCommandExecutor commands = new FakeCommandExecutor(0, "Apache Maven 3.9.8\n", "");
    final Project project =
        new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE, false);

    final CompatibilityProjectSnapshot snapshot =
        new MavenCompatibilityInspection(commands).inspect(project);

    assertThat(snapshot.toolchain().compilerRelease()).contains(JAVA_17);
    assertThat(snapshot.toolchain().mavenVersion()).contains("3.9.8");
    assertThat(snapshot.pluginVersion("spotless-maven-plugin")).contains("2.44.4");
    assertThat(commands.commands).containsExactly(List.of("mvn", "--version"));
  }

  @Test
  void treatsUnresolvedCompilerPropertyAsUnknown(@TempDir final Path root) throws Exception {
    Files.writeString(
        root.resolve("pom.xml"),
        "<project><properties><maven.compiler.release>${missing.release}</maven.compiler.release>"
            + "</properties></project>");
    final Project project =
        new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE, false);

    final CompatibilityProjectSnapshot snapshot =
        new MavenCompatibilityInspection(new FakeCommandExecutor(0, "Apache Maven 3.9.8\n", ""))
            .inspect(project);

    assertThat(snapshot.toolchain().compilerRelease()).isEmpty();
  }
}
