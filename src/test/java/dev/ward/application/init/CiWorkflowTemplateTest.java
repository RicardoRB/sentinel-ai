package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.OwnedSection;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CiWorkflowTemplateTest {
  private static final Project MAVEN =
      new Project(Path.of("/p"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);

  private static List<InitGateOption> gates(final String... ids) {
    return java.util.Arrays.stream(ids)
        .map(id -> new InitGateOption(id, id, List.of(id), true, ""))
        .toList();
  }

  @Test
  void installsOnlyTheToolsTheSelectedGatesRun() {
    final String body =
        CiWorkflowTemplate.render("github", MAVEN, gates("tests", "semgrep"), "0.0.12").body();

    assertThat(body)
        .contains("pip install semgrep==", "actions/setup-python", "cache: maven")
        .doesNotContain("gitleaks", "trivy", "openspec", "fetch-depth", "NVD_API_KEY");
  }

  @Test
  void securityGatesAddHistoryCacheAndKey() {
    final String body =
        CiWorkflowTemplate.render(
                "github",
                MAVEN,
                gates("gitleaks", "trivy", "dependency-check", "openspec"),
                "0.0.12")
            .body();

    assertThat(body)
        .contains(
            "fetch-depth: 0",
            "gitleaks_8.30.1_linux_x64",
            "Restore trivy cache",
            "Save trivy cache",
            "NVD_API_KEY: ${{ secrets.NVD_API_KEY }}",
            "@fission-ai/openspec@");
  }

  @Test
  void pinsReleasesButNotDevelopmentBuilds() {
    final Project gradle =
        new Project(Path.of("/p"), Language.JAVA, BuildTool.GRADLE, Framework.NONE);

    assertThat(CiWorkflowTemplate.render("github", gradle, gates("tests"), "dev").body())
        .doesNotContain("WARD_VERSION")
        .doesNotContain("Install gate tools")
        .contains("cache: gradle");
    assertThat(CiWorkflowTemplate.render("github", MAVEN, gates("tests"), "0.0.12").body())
        .contains("WARD_VERSION: v0.0.12");
  }

  @Test
  void ownsTheWholeWorkflowFile() {
    assertThat(CiWorkflowTemplate.render("github", MAVEN, gates("tests"), "dev"))
        .satisfies(
            section -> {
              assertThat(section.wholeFile()).isTrue();
              assertThat(section.file()).isEqualTo(Path.of("/p/.github/workflows/ward.yml"));
            });
  }

  @Test
  void rejectsUnknownProviders() {
    assertThatThrownBy(() -> CiWorkflowTemplate.render("jenkins", MAVEN, gates(), "dev"))
        .isInstanceOf(WardException.class)
        .hasMessageContaining("Unknown CI provider 'jenkins'");
  }

  @Test
  void rootProjectWorkflowIsUnchanged() throws IOException {
    final OwnedSection workflow =
        CiWorkflowTemplate.render(
            "github",
            MAVEN,
            gates("tests", "gitleaks", "trivy", "semgrep", "dependency-check", "openspec"),
            "0.1.1");

    assertThat(workflow.body())
        .isEqualTo(Files.readString(Path.of("src/test/resources/ci/ward-root.yml")));
    assertThat(workflow.file()).isEqualTo(Path.of("/p/.github/workflows/ward.yml"));
  }

  @Test
  void nestedProjectWorkflowLandsAtTheRepositoryRootScopedToTheProject() {
    final OwnedSection workflow =
        CiWorkflowTemplate.render("github", nested("apps/api"), gates("tests"), "0.1.1");

    assertThat(workflow.file()).isEqualTo(Path.of("/repo/.github/workflows/ward-apps-api.yml"));
    assertThat(workflow.body())
        .startsWith("name: 'Ward (apps/api)'\n")
        .contains(
            "  pull_request:\n    paths: ['apps/api/**', '.github/workflows/ward-apps-api.yml']\n",
            "    branches: [main]\n    paths: ['apps/api/**', '.github/workflows/ward-apps-api.yml']\n",
            "    defaults:\n      run:\n        working-directory: 'apps/api'\n",
            "run: ward check --profile ci --format sarif --output ward.sarif\n",
            "hashFiles('apps/api/ward.sarif')",
            "sarif_file: apps/api/ward.sarif\n",
            "category: ward-apps-api\n");
  }

  @Test
  void workflowNameSlugsThePath() {
    assertThat(CiWorkflowTemplate.fileName(nested("Services/Billing.Core")))
        .isEqualTo("ward-services-billing-core.yml");
    assertThat(CiWorkflowTemplate.fileName(MAVEN)).isEqualTo("ward.yml");
  }

  private static Project nested(final String path) {
    final Path repository = Path.of("/repo");
    return new Project(
        repository.resolve(path),
        Language.JAVA,
        BuildTool.MAVEN,
        Framework.NONE,
        false,
        false,
        repository,
        Set.of());
  }
}
