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
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Approved compatibility updates change only the managed plugin's project-level version. */
class PomToolVersionUpdateTest {
  private static final int JAVA_17 = 17;
  private static final String POM = "pom.xml";

  @Test
  void updatesOnlyTheManagedPluginVersionWhenOthersShareIt(@TempDir final Path root)
      throws IOException {
    Files.writeString(
        root.resolve(POM),
        "<project><build><plugins>"
            + "<plugin><artifactId>before</artifactId><version>1.0.0</version></plugin>"
            + "<plugin><artifactId>spotless-maven-plugin</artifactId><version>1.0.0</version>"
            + "</plugin>"
            + "<plugin><artifactId>after</artifactId><version>1.0.0</version></plugin>"
            + "</plugins></build></project>");

    approvedFormatUpdate(root);

    assertThat(Files.readString(root.resolve(POM)))
        .isEqualTo(
            "<project><build><plugins>"
                + "<plugin><artifactId>before</artifactId><version>1.0.0</version></plugin>"
                + "<plugin><artifactId>spotless-maven-plugin</artifactId><version>3.10.3</version>"
                + "</plugin>"
                + "<plugin><artifactId>after</artifactId><version>1.0.0</version></plugin>"
                + "</plugins></build></project>");
  }

  @Test
  void refusesVersionUpdateWhenManagedPluginHasNoVersion(@TempDir final Path root)
      throws IOException {
    Files.writeString(
        root.resolve(POM),
        "<project><build><plugins>"
            + "<plugin><artifactId>spotless-maven-plugin</artifactId></plugin>"
            + "<plugin><artifactId>after</artifactId><version>1.0.0</version></plugin>"
            + "</plugins></build></project>");
    final String original = Files.readString(root.resolve(POM));

    assertThatThrownBy(() -> approvedFormatUpdate(root))
        .hasMessageContaining("Could not safely update managed Maven plugin spotless-maven-plugin");
    assertThat(Files.readString(root.resolve(POM))).isEqualTo(original);
  }

  @Test
  void skipsVersionUpdateWhenManagedPluginIsOnlyInProfile(@TempDir final Path root)
      throws IOException {
    Files.writeString(
        root.resolve(POM),
        "<project><profiles><profile><build><plugins>"
            + "<plugin><artifactId>spotless-maven-plugin</artifactId><version>1.0.0</version>"
            + "</plugin></plugins></build></profile></profiles></project>");
    final String original = Files.readString(root.resolve(POM));

    final PomChange change = approvedFormatUpdate(root);

    assertThat(Files.readString(root.resolve(POM))).isEqualTo(original);
    assertThat(change.tools()).isEmpty();
    assertThat(change.warnings())
        .anyMatch(message -> message.contains("not declared in the project-level build"));
  }

  private static PomChange approvedFormatUpdate(final Path root) {
    final Predicate<Object> approveAll = ignored -> true;
    return new PomToolConfigurator(
            ignored -> snapshot(JAVA_17, Map.of("spotless-maven-plugin", "1.0.0")))
        .apply(project(root), List.of(gate("format")), null, approveAll);
  }

  private static Project project(final Path root) {
    return new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  }

  private static InitGateOption gate(final String id) {
    return new InitGateOption(id, id, List.of("mvn", id), true, "available");
  }

  private static CompatibilityProjectSnapshot snapshot(
      final int javaVersion, final Map<String, String> plugins) {
    return new CompatibilityProjectSnapshot(
        project(Path.of(".")),
        new ToolchainSnapshot(javaVersion, Optional.of("3.9.0"), Optional.empty(), Framework.NONE),
        plugins);
  }
}
