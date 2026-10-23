package dev.ward.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Maven modules belong to their aggregator in detection and discovery. */
class MavenModuleDiscoveryTest {
  private static final String AGGREGATOR =
      "<project><modules><module>core</module><module>web/pom.xml</module></modules></project>";
  private static final String LEAF = "<project><artifactId>leaf</artifactId></project>";
  private static final String POM = "pom.xml";

  @TempDir Path root;

  private final FileSystemProjectInspection inspection = new FileSystemProjectInspection();
  private ProjectTree tree;

  @BeforeEach
  void gitRepository() throws IOException {
    tree = new ProjectTree(root);
  }

  @Test
  void mavenModulesAreGroupedUnderTheirAggregator() throws IOException {
    tree.write("apps/api/" + POM, AGGREGATOR);
    tree.write("apps/api/core/" + POM, LEAF);
    tree.write("apps/api/web/" + POM, LEAF);

    assertThat(ProjectTree.roots(inspection.discover(root))).containsExactly(tree.path("apps/api"));
    assertThat(inspection.discover(root).getFirst().moduleCount()).isEqualTo(2);
  }

  @Test
  void profileModulesAreModules() throws IOException {
    tree.write(
        POM,
        "<project><profiles><profile><modules><module>extra</module></modules></profile>"
            + "</profiles></project>");
    tree.write("extra/" + POM, LEAF);

    assertThat(ProjectTree.roots(inspection.discover(root))).containsExactly(tree.path(""));
  }

  @Test
  void nestedAggregatorsResolveTransitively() throws IOException {
    tree.write(POM, "<project><modules><module>apps/api</module></modules></project>");
    tree.write("apps/api/" + POM, AGGREGATOR);
    tree.write("apps/api/core/" + POM, LEAF);

    assertThat(inspection.detect(root.resolve("apps/api/core")).map(Project::root))
        .contains(tree.path(""));
    assertThat(inspection.discover(root).getFirst().moduleCount()).isEqualTo(2);
  }

  @Test
  void detectionInsideAMavenModuleUsesTheAggregator() throws IOException {
    tree.write("apps/api/" + POM, AGGREGATOR);
    tree.write("apps/api/core/" + POM, LEAF);

    assertThat(inspection.detect(root.resolve("apps/api/core")).map(Project::root))
        .contains(tree.path("apps/api"));
  }

  @Test
  void unlistedNestedBuildIsItsOwnProject() throws IOException {
    tree.write("apps/api/" + POM, AGGREGATOR);
    tree.write("tools/generator/" + POM, LEAF);
    tree.write("apps/api/samples/" + POM, LEAF);

    assertThat(ProjectTree.roots(inspection.discover(root)))
        .containsExactly(
            tree.path("apps/api"), tree.path("apps/api/samples"), tree.path("tools/generator"));
    assertThat(inspection.detect(root.resolve("tools/generator")).map(Project::root))
        .contains(tree.path("tools/generator"));
  }

  @Test
  void singleProjectDetectionIsUnchanged() throws IOException {
    tree.write(POM, LEAF);

    assertThat(inspection.detect(root).map(Project::root)).contains(tree.path(""));
  }

  @Test
  void mavenModulesIgnoreUnrelatedModuleElements() throws IOException {
    tree.write(POM, "<project><build><module>core</module></build></project>");
    tree.write("core/" + POM, LEAF);

    assertThat(ProjectTree.roots(inspection.discover(root)))
        .containsExactly(tree.path(""), tree.path("core"));
  }
}
