package dev.sentinel.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.config.GateConfiguration;
import dev.sentinel.domain.config.PartialCheckPlan;
import dev.sentinel.domain.config.SentinelConfiguration;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import dev.sentinel.infrastructure.config.TomlConfigurationReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.codehaus.plexus.util.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Applies the narrowing arguments init writes for Maven with each tool's own file matching. A form
 * a tool does not understand analyzes nothing and passes, so each test also pins that the narrowed
 * run still finds the edited file.
 *
 * <p>maven-checkstyle-plugin collects files with {@code FileUtils.getFiles(sourceDirectory,
 * includes, excludes)}. spotless-maven-plugin splits {@code spotlessFiles} on commas and keeps a
 * file when one pattern matches its absolute path.
 */
class PartialNarrowingToolMatchingTest {
  private static final String SOURCES = "src/main/java";
  private static final String ORDER = SOURCES + "/com/acme/order/Order.java";
  private static final String INVOICE = SOURCES + "/com/acme/billing/Invoice.java";

  @TempDir Path root;

  private Map<String, GateConfiguration> gates;

  @BeforeEach
  void project() throws IOException {
    for (final String source : List.of(ORDER, INVOICE)) {
      Files.createDirectories(root.resolve(source).getParent());
      Files.writeString(root.resolve(source), "class Sample {}\n");
    }
    final Path file = root.resolve(SentinelConfiguration.FILE_NAME);
    Files.writeString(
        file,
        ConfigurationTemplate.render(
            List.of(gate("checkstyle"), gate("format")),
            QualityPreset.STANDARD,
            new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE)));
    gates = new TomlConfigurationReader().read(file).gates();
  }

  @Test
  void checkstyleIncludesSelectExactlyTheEditedFile() throws IOException {
    final String includes = value(narrowed("checkstyle", ORDER), "-Dcheckstyle.includes=");

    assertThat(checkstyleFiles(includes)).containsExactly(root.resolve(ORDER).toFile());
    assertThat(checkstyleFiles(value(narrowed("checkstyle", ORDER, INVOICE), "=")))
        .containsExactlyInAnyOrder(root.resolve(ORDER).toFile(), root.resolve(INVOICE).toFile());
  }

  @Test
  void rootRelativeCheckstyleIncludesWouldAnalyzeNothing() throws IOException {
    assertThat(checkstyleFiles(ORDER)).isEmpty();
  }

  @Test
  void spotlessFilesSelectExactlyTheEditedFile() {
    final String patterns = value(narrowed("format", ORDER), "-DspotlessFiles=");

    assertThat(spotlessMatches(patterns, ORDER)).isTrue();
    assertThat(spotlessMatches(patterns, INVOICE)).isFalse();
    assertThat(spotlessMatches(value(narrowed("format", ORDER, INVOICE), "="), INVOICE)).isTrue();
  }

  @Test
  void rootRelativeSpotlessFilesWouldCheckNothing() {
    assertThat(spotlessMatches(ORDER, ORDER)).isFalse();
  }

  private List<String> narrowed(final String gate, final String... paths) {
    final PartialCheckPlan.GatePlan plan =
        PartialCheckPlan.create(
                root,
                Map.of(gate, gates.get(gate)),
                List.of(paths).stream().map(root::resolve).toList(),
                Files::exists)
            .gates()
            .get(gate);
    assertThat(plan.scope()).isEqualTo(PartialCheckPlan.Scope.NARROW);
    return plan.args();
  }

  private static String value(final List<String> args, final String prefix) {
    assertThat(args).singleElement().asString().contains(prefix);
    return args.getFirst().substring(args.getFirst().indexOf(prefix) + prefix.length());
  }

  private List<File> checkstyleFiles(final String includes) throws IOException {
    return FileUtils.getFiles(root.resolve(SOURCES).toFile(), includes, null);
  }

  private boolean spotlessMatches(final String patterns, final String source) {
    final String absolute = root.resolve(source).toFile().getAbsolutePath();
    return List.of(patterns.split(",")).stream()
        .anyMatch(pattern -> Pattern.compile(pattern).matcher(absolute).matches());
  }

  private static InitGateOption gate(final String id) {
    return new InitGateOption(id, id, List.of("mvn", id), true, "available");
  }
}
