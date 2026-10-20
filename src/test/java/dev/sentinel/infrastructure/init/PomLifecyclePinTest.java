package dev.sentinel.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.QualityPreset;
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

/** Strict pins Maven's default lifecycle plugins so requirePluginVersions passes after init. */
class PomLifecyclePinTest {
  private static final String POM =
      "<project>\n  <modelVersion>4.0.0</modelVersion>\n  <groupId>a</groupId>\n"
          + "  <artifactId>b</artifactId>\n  <version>1</version>\n</project>\n";
  private static final String COMPILER = "maven-compiler-plugin";

  @TempDir Path root;

  @Test
  void tablePinsMavenDefaultLifecycleVersions() {
    assertThat(PresetPluginSnippets.LIFECYCLE_PLUGINS)
        .extracting(plugin -> plugin.artifact() + ":" + plugin.version())
        .containsExactly(
            "maven-clean-plugin:3.2.0",
            "maven-resources-plugin:3.4.0",
            "maven-compiler-plugin:3.15.0",
            "maven-surefire-plugin:3.5.4",
            "maven-jar-plugin:3.5.0",
            "maven-install-plugin:3.1.4",
            "maven-deploy-plugin:3.1.4",
            "maven-site-plugin:3.12.1");
  }

  @Test
  void managedPluginIsInsertedWhateverParentsExist() {
    final String plugin = "<plugin><artifactId>x</artifactId></plugin>\n";
    final String noBuild = PomXml.addManagedPlugin(POM, plugin);
    final String buildOnly =
        PomXml.addManagedPlugin(POM.replace("</project>", "<build></build>\n</project>"), plugin);
    final String existing =
        PomXml.addManagedPlugin(
            POM.replace(
                "</project>",
                "<build><pluginManagement><plugins></plugins></pluginManagement></build>\n</project>"),
            plugin);

    for (final String pom : List.of(noBuild, buildOnly, existing)) {
      assertThat(pom)
          .containsOnlyOnce("<pluginManagement>")
          .containsPattern("(?s)<pluginManagement>\\s*<plugins>.*<artifactId>x</artifactId>");
    }
  }

  @Test
  void strictEnforcerPinsEveryUnpinnedLifecyclePlugin() throws IOException {
    final String pom = apply(POM, QualityPreset.STRICT);

    PresetPluginSnippets.LIFECYCLE_PLUGINS.forEach(
        plugin -> assertThat(pom).contains("<artifactId>" + plugin.artifact() + "</artifactId>"));
    assertThat(pom).contains("<pluginManagement>");
  }

  @Test
  void existingCompilerDeclarationIsKept() throws IOException {
    final String declared =
        POM.replace(
            "</project>",
            "<build><plugins><plugin><artifactId>maven-compiler-plugin</artifactId>"
                + "<version>3.13.0</version></plugin></plugins></build>\n</project>");

    final String pom = apply(declared, QualityPreset.STRICT);

    assertThat(pom).containsOnlyOnce("<artifactId>" + COMPILER + "</artifactId>");
    assertThat(pom).contains("<version>3.13.0</version>").doesNotContain("3.15.0");
  }

  @Test
  void standardPresetAddsNoPins() throws IOException {
    assertThat(apply(POM, QualityPreset.STANDARD)).doesNotContain("<pluginManagement>");
  }

  @Test
  void dryRunListsEachPin() throws IOException {
    Files.writeString(root.resolve("pom.xml"), POM);

    final List<String> details =
        new PomToolConfigurator().plan(project(), enforcer(), QualityPreset.STRICT, true).details();

    assertThat(details).contains("+ maven-site-plugin 3.12.1 (pluginManagement)");
    assertThat(Files.readString(root.resolve("pom.xml"))).isEqualTo(POM);
  }

  private String apply(final String pom, final QualityPreset preset) throws IOException {
    Files.writeString(root.resolve("pom.xml"), pom);
    new PomToolConfigurator().apply(project(), enforcer(), preset);
    return Files.readString(root.resolve("pom.xml"));
  }

  private Project project() {
    return new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  }

  private static List<InitGateOption> enforcer() {
    return List.of(
        new InitGateOption("enforcer", "enforcer", List.of("mvn", "enforcer:enforce"), true, "ok"));
  }
}
