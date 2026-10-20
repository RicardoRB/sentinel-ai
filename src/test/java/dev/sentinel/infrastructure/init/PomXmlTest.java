package dev.sentinel.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.QualityPreset;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class PomXmlTest {
  private static final String PLUGIN =
      """
          <plugin>
            <artifactId>new-plugin</artifactId>
          </plugin>
      """;

  @Test
  void addsPluginToProjectBuildWhenProfileBuildComesFirst() {
    final String pom =
        """
        <project>
            <profiles>
                <profile>
                    <build>
                        <plugins>
                            <plugin><artifactId>native</artifactId></plugin>
                        </plugins>
                    </build>
                </profile>
            </profiles>
            <build>
                <plugins>
                    <plugin><artifactId>existing</artifactId></plugin>
                </plugins>
            </build>
        </project>
        """;

    assertThat(PomXml.addPlugin(pom, PLUGIN))
        .isEqualTo(
            """
            <project>
                <profiles>
                    <profile>
                        <build>
                            <plugins>
                                <plugin><artifactId>native</artifactId></plugin>
                            </plugins>
                        </build>
                    </profile>
                </profiles>
                <build>
                    <plugins>
                        <plugin><artifactId>existing</artifactId></plugin>
                        <plugin>
                          <artifactId>new-plugin</artifactId>
                        </plugin>
                    </plugins>
                </build>
            </project>
            """);
  }

  @Test
  void skipsPluginManagementAndComments() {
    final String pom =
        "<project><build><!-- </plugins> --><pluginManagement><plugins></plugins>"
            + "</pluginManagement><plugins><plugin/></plugins></build></project>";

    final String updated = PomXml.addPlugin(pom, PLUGIN);

    assertThat(updated)
        .startsWith(
            "<project><build><!-- </plugins> --><pluginManagement><plugins></plugins>"
                + "</pluginManagement><plugins><plugin/>\n")
        .contains("<artifactId>new-plugin</artifactId>")
        .endsWith("</plugin>\n</plugins></build></project>");
  }

  @Test
  void expandsSelfClosingBuild() {
    final String updated = PomXml.addPlugin("<project><build/></project>", PLUGIN);

    assertThat(updated)
        .isEqualTo(
            "<project><build>\n  <plugins>\n    <plugin>\n      <artifactId>new-plugin</artifactId>\n"
                + "    </plugin>\n  </plugins>\n</build></project>");
  }

  @Test
  void createsBuildInsideProjectWithAttributes() {
    final String pom = "<?xml version=\"1.0\"?>\n<project xmlns=\"urn:pom\">\n  <x/>\n</project>\n";

    assertThat(PomXml.addPlugin(pom, PLUGIN))
        .isEqualTo(
            "<?xml version=\"1.0\"?>\n<project xmlns=\"urn:pom\">\n  <x/>\n  <build>\n"
                + "    <plugins>\n      <plugin>\n        <artifactId>new-plugin</artifactId>\n"
                + "      </plugin>\n    </plugins>\n  </build>\n</project>\n");
  }

  @Test
  void addsDependencyToProjectDependenciesNotManagementOrPluginDependencies() {
    final String pom =
        "<project><dependencyManagement><dependencies></dependencies></dependencyManagement>"
            + "<build><plugins><plugin><dependencies></dependencies></plugin></plugins></build>"
            + "<dependencies></dependencies></project>";

    final String updated =
        PomXml.addDependency(pom, "<dependency><artifactId>d</artifactId></dependency>");

    assertThat(updated)
        .endsWith(
            "<dependencies>\n  <dependency><artifactId>d</artifactId></dependency>\n"
                + "</dependencies></project>");
  }

  @Test
  void createsDependenciesWhenMissing() {
    assertThat(PomXml.addDependency("<project></project>", "<dependency/>"))
        .isEqualTo("<project>\n  <dependencies>\n    <dependency/>\n  </dependencies>\n</project>");
  }

  @Test
  void rejectsPomWithoutProject() {
    assertThatThrownBy(() -> PomXml.addPlugin("<other/>", PLUGIN))
        .isInstanceOf(SentinelException.class)
        .hasMessageContaining("missing <project>");
  }

  @Test
  void rejectsUnterminatedComment() {
    assertThatThrownBy(() -> PomXml.addPlugin("<project><!-- open", PLUGIN))
        .isInstanceOf(SentinelException.class)
        .hasMessageContaining("malformed");
  }

  @Test
  void pitPresetPlacesMutationThresholdInsideConfiguration() {
    final QualityPreset preset =
        Arrays.stream(QualityPreset.values())
            .filter(candidate -> candidate.rules().mutationThreshold() != null)
            .findFirst()
            .orElseThrow();

    final String updated =
        PomXml.addPlugin("<project></project>", PresetPluginSnippets.pit(preset));

    assertThat(updated)
        .contains(
            "</dependencies>\n        <configuration>\n          <mutationThreshold>"
                + preset.rules().mutationThreshold()
                + "</mutationThreshold>\n        </configuration>\n      </plugin>");
  }

  @Test
  void pitConfigurationsUseJava25CompatibleVersionsWithoutHistorySettings() {
    final String defaultPlugin = PomToolConfigurator.PIT_PLUGIN;
    final String presetPlugin = PresetPluginSnippets.pit(QualityPreset.STRICT);

    assertThat(defaultPlugin)
        .contains("<version>1.30.1</version>", "<version>1.2.3</version>")
        .doesNotContain("historyInputFile", "historyOutputFile");
    assertThat(presetPlugin)
        .contains("<version>1.30.1</version>", "<version>1.2.3</version>")
        .doesNotContain("historyInputFile", "historyOutputFile");
  }
}
