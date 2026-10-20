package dev.sentinel.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * japicmp compares the released baseline with the jar this build produced, project classes only.
 */
class PomJapicmpConfigurationTest {
  private static final String POM =
      "<project><modelVersion>4.0.0</modelVersion><groupId>com.acme</groupId>"
          + "<artifactId>lib</artifactId><version>1</version></project>";

  @TempDir Path root;

  @Test
  void comparesTheLocallyBuiltJarAndIncludesOnlyProjectClasses() throws IOException {
    final String pom = configure(POM);

    assertThat(pom)
        .contains(
            "<version>RELEASE</version>",
            "<path>${project.build.directory}/${project.build.finalName}.jar</path>",
            "<include>${project.groupId}.*</include>",
            "<breakBuildOnBinaryIncompatibleModifications>true");
  }

  @Test
  void keepsAnExistingJapicmpDeclaration() throws IOException {
    final String existing =
        POM.replace(
            "</project>",
            "<build><plugins><plugin><groupId>com.github.siom79.japicmp</groupId>"
                + "<artifactId>japicmp-maven-plugin</artifactId><version>0.23.1</version>"
                + "</plugin></plugins></build></project>");

    assertThat(configure(existing)).isEqualTo(existing);
  }

  private String configure(final String pom) throws IOException {
    Files.writeString(root.resolve("pom.xml"), pom);
    new PomToolConfigurator()
        .apply(
            new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE),
            List.of(
                new InitGateOption(
                    "api-compat", "api-compat", List.of("mvn", "japicmp:cmp"), true, "ok")),
            null);
    return Files.readString(root.resolve("pom.xml"));
  }
}
