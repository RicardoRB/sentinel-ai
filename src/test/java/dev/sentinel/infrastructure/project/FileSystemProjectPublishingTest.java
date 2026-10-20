package dev.sentinel.infrastructure.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** A Maven project publishes artifacts only when its POM declares distributionManagement. */
class FileSystemProjectPublishingTest {
  private static final String DISTRIBUTION =
      "<distributionManagement><repository><id>r</id><url>file:///r</url></repository>"
          + "</distributionManagement>";

  @TempDir Path root;

  @Test
  void directDistributionManagementPublishes() throws IOException {
    assertThat(publishes("<project>" + DISTRIBUTION + "</project>")).isTrue();
  }

  @Test
  void pomWithoutDistributionManagementDoesNotPublish() throws IOException {
    assertThat(publishes("<project><artifactId>app</artifactId></project>")).isFalse();
  }

  @Test
  void profileOnlyDistributionManagementDoesNotPublish() throws IOException {
    assertThat(
            publishes(
                "<project><profiles><profile>" + DISTRIBUTION + "</profile></profiles></project>"))
        .isFalse();
  }

  @Test
  void malformedPomDoesNotPublish() throws IOException {
    assertThat(publishes("<project>" + DISTRIBUTION)).isFalse();
  }

  @Test
  void pomWithDoctypeDoesNotPublish() throws IOException {
    assertThat(
            publishes(
                "<!DOCTYPE project [<!ENTITY x \"y\">]><project>" + DISTRIBUTION + "</project>"))
        .isFalse();
  }

  private boolean publishes(final String pom) throws IOException {
    Files.writeString(root.resolve("pom.xml"), pom);
    return new FileSystemProjectInspection().detect(root).orElseThrow().publishesArtifacts();
  }
}
