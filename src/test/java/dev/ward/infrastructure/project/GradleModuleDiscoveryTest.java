package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.DiscoveredProject;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Gradle modules are grouped and discovery skips dependency and build output. */
class GradleModuleDiscoveryTest {
  private static final String LEAF = "<project><artifactId>leaf</artifactId></project>";
  private static final String GRADLE_BUILD = "build.gradle.kts";
  private static final String POM = "pom.xml";

  @TempDir Path root;

  private final FileSystemProjectInspection inspection = new FileSystemProjectInspection();
  private ProjectTree tree;

  @BeforeEach
  void gitRepository() throws IOException {
    tree = new ProjectTree(root);
  }

  @Test
  void gradleKotlinBuildsBelowSettingsAreModules() throws IOException {
    tree.write("services/billing/settings.gradle.kts", "include(\":domain\", \":api:rest\")");
    tree.write("services/billing/" + GRADLE_BUILD, "");
    tree.write("services/billing/domain/" + GRADLE_BUILD, "");
    tree.write("services/billing/api/rest/" + GRADLE_BUILD, "");

    final List<DiscoveredProject> projects = inspection.discover(root);

    assertThat(ProjectTree.roots(projects)).containsExactly(tree.path("services/billing"));
    assertThat(projects.getFirst().project().buildTool()).isEqualTo(BuildTool.GRADLE);
    assertThat(projects.getFirst().moduleCount()).isEqualTo(2);
  }

  @Test
  void gradleGroovyModuleDetectsItsAggregator() throws IOException {
    tree.write("settings.gradle", "include 'domain'");
    tree.write("build.gradle", "");
    tree.write("domain/build.gradle", "");

    assertThat(inspection.detect(root.resolve("domain")).map(Project::root))
        .contains(tree.path(""));
  }

  @Test
  void discoverySkipsDependenciesBuildOutputAndHiddenDirectories() throws IOException {
    tree.write("package.json", "{}");
    tree.write("apps/api/" + POM, LEAF);
    tree.write("node_modules/left-pad/package.json", "{}");
    tree.write("node_modules/jar/" + POM, LEAF);
    tree.write("apps/api/target/classes/" + POM, LEAF);
    tree.write("apps/web/dist/package.json", "{}");
    tree.write("apps/web/build/package.json", "{}");
    tree.write("apps/web/out/package.json", "{}");
    tree.write(".idea/" + POM, LEAF);

    assertThat(ProjectTree.roots(inspection.discover(root)))
        .containsExactly(tree.path(""), tree.path("apps/api"));
  }
}
