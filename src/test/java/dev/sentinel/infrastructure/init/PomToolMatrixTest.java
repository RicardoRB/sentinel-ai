package dev.sentinel.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.PomChange;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.Profile;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Applies every Maven tool gate under every preset to representative poms and checks the result
 * with Maven's strict model reader, so a malformed or misplaced snippet cannot pass the suite.
 */
class PomToolMatrixTest {
  private static final Path FIXTURES = Path.of("src/test/resources/fixtures/poms");

  /** Gates that add Maven declarations, matching the configurator's tool table. */
  private static final List<String> MAVEN_GATES =
      List.of(
          "checkstyle",
          "pmd",
          "spotbugs",
          "sonar",
          "coverage",
          "dependency-check",
          "archunit",
          "mutation",
          "enforcer",
          "format",
          "license",
          "api-compat");

  @ParameterizedTest(name = "{0}")
  @MethodSource("fixtures")
  void fixturesAreStrictlyValidPoms(final Path fixture) throws IOException {
    assertThat(read(Files.readString(fixture)).getArtifactId()).isNotBlank();
  }

  @ParameterizedTest(name = "{0} {1} {2}")
  @MethodSource("combinations")
  void generatedDeclarationsAreValidAndProjectLevel(
      final String fixtureName,
      final QualityPreset preset,
      final List<String> gates,
      @TempDir final Path root)
      throws IOException {
    final String original = Files.readString(FIXTURES.resolve(fixtureName));
    Files.writeString(root.resolve("pom.xml"), original);

    final PomChange change =
        new PomToolConfigurator()
            .apply(
                new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE),
                gates.stream().map(PomToolMatrixTest::gate).toList(),
                preset);

    final Model before = read(original);
    final String generated = Files.readString(root.resolve("pom.xml"));
    final Model after = read(generated);
    final List<String> projectLevel = new ArrayList<>();
    if (after.getBuild() != null) {
      after.getBuild().getPlugins().stream().map(Plugin::getArtifactId).forEach(projectLevel::add);
    }
    after.getDependencies().stream().map(Dependency::getArtifactId).forEach(projectLevel::add);
    assertThat(projectLevel).containsAll(change.tools());
    assertThat(profilePlugins(after)).isEqualTo(profilePlugins(before));
    if (gates.contains("mutation")) {
      assertThat(generated)
          .contains(
              "<artifactId>pitest-maven</artifactId>",
              "<version>1.30.1</version>",
              "<artifactId>pitest-junit5-plugin</artifactId>",
              "<version>1.2.3</version>")
          .doesNotContain("historyInputFile", "historyOutputFile");
    }
    if (before.getBuild() != null && before.getBuild().getPluginManagement() != null) {
      assertThat(after.getBuild().getPluginManagement().getPlugins())
          .extracting(Plugin::getArtifactId)
          .isEqualTo(
              before.getBuild().getPluginManagement().getPlugins().stream()
                  .map(Plugin::getArtifactId)
                  .toList());
    }
  }

  static Stream<Path> fixtures() throws IOException {
    try (Stream<Path> files = Files.list(FIXTURES)) {
      return files.sorted().toList().stream();
    }
  }

  static Stream<Arguments> combinations() throws IOException {
    final List<QualityPreset> presets = new ArrayList<>();
    presets.add(null);
    presets.addAll(Arrays.asList(QualityPreset.values()));
    final List<List<String>> gateSets = new ArrayList<>();
    MAVEN_GATES.forEach(gate -> gateSets.add(List.of(gate)));
    gateSets.add(MAVEN_GATES);
    final List<Arguments> arguments = new ArrayList<>();
    for (final Path fixture : fixtures().toList()) {
      for (final QualityPreset preset : presets) {
        for (final List<String> gates : gateSets) {
          arguments.add(Arguments.of(fixture.getFileName().toString(), preset, gates));
        }
      }
    }
    return arguments.stream();
  }

  private static List<List<String>> profilePlugins(final Model model) {
    return model.getProfiles().stream()
        .map(Profile::getBuild)
        .map(
            build ->
                build == null
                    ? List.<String>of()
                    : build.getPlugins().stream().map(Plugin::getArtifactId).toList())
        .toList();
  }

  private static Model read(final String pom) throws IOException {
    try (Reader reader = new StringReader(pom)) {
      return new MavenXpp3Reader().read(reader, true);
    } catch (XmlPullParserException e) {
      throw new AssertionError("Maven rejected the pom: " + e.getMessage(), e);
    }
  }

  private static InitGateOption gate(final String id) {
    return new InitGateOption(id, id, List.of("mvn", id), true, "available");
  }
}
