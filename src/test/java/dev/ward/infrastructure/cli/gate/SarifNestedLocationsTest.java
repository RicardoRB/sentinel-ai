package dev.ward.infrastructure.cli.gate;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JsonTree;
import dev.ward.domain.gate.CheckReport;
import dev.ward.domain.gate.Finding;
import dev.ward.domain.gate.FindingSeverity;
import dev.ward.domain.gate.GateFindings;
import dev.ward.domain.gate.GateResult;
import dev.ward.domain.gate.GateStatus;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import dev.ward.infrastructure.json.ForyJsonCodec;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** SARIF locations are relative to the repository root, so nested projects carry their path. */
class SarifNestedLocationsTest {
  private static final Path REPOSITORY = Path.of("/repo");
  private static final int LINE = 10;

  @Test
  void nestedProjectFindingsArePrefixedWithTheProjectPath() {
    assertThat(uri(project(REPOSITORY.resolve("apps/api")), "src/main/java/Foo.java"))
        .isEqualTo("apps/api/src/main/java/Foo.java");
  }

  @Test
  void rootProjectFindingsAreUnchanged() {
    assertThat(uri(project(REPOSITORY), "src/main/java/Foo.java"))
        .isEqualTo("src/main/java/Foo.java");
  }

  @Test
  void filesOutsideTheProjectStayFileUris() {
    assertThat(uri(project(REPOSITORY.resolve("apps/api")), "/x/B.java"))
        .isEqualTo("file:///x/B.java");
  }

  private static String uri(final Project project, final String file) {
    final Finding finding =
        new Finding(
            "pmd",
            "pmd",
            "UnusedLocalVariable",
            FindingSeverity.WARNING,
            "m",
            file,
            LINE,
            null,
            null);
    final GateResult result =
        GateResult.builder("pmd", GateStatus.FAILED)
            .command(List.of("x"))
            .exitCode(1)
            .duration(Duration.ZERO)
            .stdout("")
            .stderr("")
            .build()
            .withFindings(GateFindings.of(List.of(finding)));
    return JsonTree.parse(
            new SarifReportRenderer(new ForyJsonCodec())
                .render(new CheckReport(project, List.of(result))))
        .get("runs")
        .get(0)
        .get("results")
        .get(0)
        .get("locations")
        .get(0)
        .get("physicalLocation")
        .get("artifactLocation")
        .get("uri")
        .asString();
  }

  private static Project project(final Path root) {
    return new Project(
        root, Language.JAVA, BuildTool.MAVEN, Framework.NONE, false, false, REPOSITORY, Set.of());
  }
}
