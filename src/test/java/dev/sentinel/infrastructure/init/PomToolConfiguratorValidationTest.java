package dev.sentinel.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.CompatibilityProjectSnapshot;
import dev.sentinel.domain.init.InitGateOption;
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

class PomToolConfiguratorValidationTest {
  private static final int JAVA_17 = 17;

  /** Well-formed apart from an undefined entity, so only the pre-write check can reject it. */
  private static final String UNDEFINED_ENTITY_POM =
      "<project>\n  <name>&undefined;</name>\n  <build>\n    <plugins>\n    </plugins>\n"
          + "  </build>\n</project>\n";

  @Test
  void leavesPomUnchangedAndNamesToolWhenValidationFails(@TempDir final Path root)
      throws IOException {
    final Path pom = root.resolve("pom.xml");
    Files.writeString(pom, UNDEFINED_ENTITY_POM);
    final byte[] original = Files.readAllBytes(pom);

    assertThatThrownBy(() -> new PomToolConfigurator().apply(project(root), List.of(gate("pmd"))))
        .isInstanceOf(SentinelException.class)
        .hasMessageContaining("while adding maven-pmd-plugin")
        .hasMessageContaining("not well-formed XML (line 2");
    assertThat(Files.readAllBytes(pom)).isEqualTo(original);
  }

  @Test
  void compatibilityPathValidatesBeforeWriting(@TempDir final Path root) throws IOException {
    final Path pom = root.resolve("pom.xml");
    Files.writeString(pom, UNDEFINED_ENTITY_POM);
    final byte[] original = Files.readAllBytes(pom);
    final PomToolConfigurator configurator =
        new PomToolConfigurator(
            ignored ->
                new CompatibilityProjectSnapshot(
                    project(Path.of(".")),
                    new ToolchainSnapshot(
                        JAVA_17, Optional.of("3.9.0"), Optional.empty(), Framework.NONE),
                    Map.of()));
    final Predicate<Object> approveAll = ignored -> true;

    assertThatThrownBy(
            () -> configurator.apply(project(root), List.of(gate("format")), null, approveAll))
        .isInstanceOf(SentinelException.class)
        .hasMessageContaining("while adding spotless-maven-plugin");
    assertThat(Files.readAllBytes(pom)).isEqualTo(original);
  }

  @Test
  void writesValidatedPomWhenChecksPass(@TempDir final Path root) throws IOException {
    final Path pom = root.resolve("pom.xml");
    Files.writeString(pom, "<project>\n  <build/>\n</project>\n");

    new PomToolConfigurator().apply(project(root), List.of(gate("pmd"), gate("archunit")));

    assertThat(Files.readString(pom))
        .contains("<artifactId>maven-pmd-plugin</artifactId>")
        .contains("<artifactId>archunit-junit5</artifactId>");
  }

  private static Project project(final Path root) {
    return new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  }

  private static InitGateOption gate(final String id) {
    return new InitGateOption(id, id, List.of("mvn", id), true, "available");
  }
}
