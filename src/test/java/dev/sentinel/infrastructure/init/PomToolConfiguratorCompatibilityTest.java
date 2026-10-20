package dev.sentinel.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.domain.init.CompatibilityProjectSnapshot;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.PomChange;
import dev.sentinel.domain.init.ToolchainSnapshot;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PomToolConfiguratorCompatibilityTest {
  private static final int JAVA_17 = 17;
  private static final int JAVA_25 = 25;
  // Older than every managed plugin supports.
  private static final int JAVA_7 = 7;

  @Test
  void selectsCatalogVersionForMissingPlugin(@TempDir final Path root) throws IOException {
    writePom(root, "<project><build/></project>");
    final Project project = project(root);
    final PomToolConfigurator configurator =
        new PomToolConfigurator(ignored -> snapshot(JAVA_17, Map.of()));

    configurator.apply(project, List.of(gate("format")), null, finding -> true);

    assertThat(Files.readString(root.resolve("pom.xml"))).contains("<version>3.10.3</version>");
  }

  @Test
  void asksBeforeUpdatingIncompatiblePluginAndPreservesConfiguration(@TempDir final Path root)
      throws IOException {
    writePom(
        root,
        "<project><build><plugins><plugin><artifactId>spotless-maven-plugin</artifactId>"
            + "<version>1.0.0</version><configuration><java>mine</java></configuration>"
            + "</plugin></plugins></build></project>");
    final Project project = project(root);
    final PomToolConfigurator configurator =
        new PomToolConfigurator(
            ignored -> snapshot(JAVA_17, Map.of("spotless-maven-plugin", "1.0.0")));
    final String original = Files.readString(root.resolve("pom.xml"));

    final PomChange change =
        configurator.apply(project, List.of(gate("format")), null, ignored -> false);

    assertThat(Files.readString(root.resolve("pom.xml"))).isEqualTo(original);
    assertThat(change.warnings()).anyMatch(message -> message.contains("declined"));
  }

  @Test
  void rejectsProjectWithNoCompatibleVersionBeforeWriting(@TempDir final Path root)
      throws IOException {
    writePom(root, "<project><build/></project>");
    final Project project = project(root);
    final PomToolConfigurator configurator =
        new PomToolConfigurator(ignored -> snapshot(JAVA_7, Map.of()));
    final String original = Files.readString(root.resolve("pom.xml"));

    assertThatThrownBy(
            () -> configurator.apply(project, List.of(gate("format")), null, ignored -> true))
        .hasMessageContaining("No catalog version");
    assertThat(Files.readString(root.resolve("pom.xml"))).isEqualTo(original);
  }

  @Test
  void configuresJava25MutationWithCurrentPitestVersions(@TempDir final Path root)
      throws IOException {
    writePom(root, "<project><build/></project>");

    new PomToolConfigurator(ignored -> snapshot(JAVA_25, Map.of()))
        .apply(project(root), List.of(gate("mutation")), null, ignored -> true);

    assertThat(Files.readString(root.resolve("pom.xml")))
        .contains(
            "<artifactId>pitest-maven</artifactId>",
            "<version>1.30.1</version>",
            "<artifactId>pitest-junit5-plugin</artifactId>",
            "<version>1.2.3</version>")
        .doesNotContain("historyInputFile", "historyOutputFile");
  }

  private static CompatibilityProjectSnapshot snapshot(
      final int javaVersion, final Map<String, String> plugins) {
    return new CompatibilityProjectSnapshot(
        new Project(Path.of("."), Language.JAVA, BuildTool.MAVEN, Framework.NONE),
        new ToolchainSnapshot(javaVersion, Optional.of("3.9.0"), Optional.empty(), Framework.NONE),
        plugins);
  }

  private static Project project(final Path root) {
    return new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  }

  private static InitGateOption gate(final String id) {
    return new InitGateOption(id, id, List.of("mvn", id), true, "available");
  }

  private static void writePom(final Path root, final String content) throws IOException {
    Files.writeString(root.resolve("pom.xml"), content);
  }
}
