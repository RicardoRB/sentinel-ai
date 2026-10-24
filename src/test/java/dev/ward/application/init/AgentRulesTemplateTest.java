package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.JavaPresetGates;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentRulesTemplateTest {
  private static final Project PROJECT =
      new Project(Path.of("/p"), Language.JAVA, BuildTool.MAVEN, Framework.NONE);

  private static List<InitGateOption> gates(final List<String> ids) {
    return ids.stream().map(id -> new InitGateOption(id, id, List.of(id), true, "")).toList();
  }

  @Test
  void strictPresetStatesEveryLimitAgentsMustMeet() {
    final String body =
        AgentRulesTemplate.render(
                PROJECT, gates(JavaPresetGates.STRICT), QualityPreset.STRICT, List.of())
            .body();

    assertThat(body)
        .contains(
            "files of at most 500 lines",
            "methods of at most 60 lines",
            "at most 5 parameters",
            "cyclomatic complexity of at most 10",
            "no magic numbers",
            "final parameters",
            "no `var`",
            "effort Max, threshold Low",
            "branch at least 75%, line at least 85%",
            "at least 60% of mutants killed",
            "`@ArchIgnore`",
            "requirePluginVersions",
            "Do not add suppressions");
  }

  @Test
  void userOwnedRuleFileIsReferencedInsteadOfQuoted() {
    final String body =
        AgentRulesTemplate.render(
                PROJECT,
                gates(List.of("checkstyle", "pmd")),
                QualityPreset.STRICT,
                List.of(Path.of("/p/config/checkstyle.xml")))
            .body();

    assertThat(body)
        .contains("Checkstyle: follow the rules in `config/checkstyle.xml`", "PMD (`")
        .doesNotContain("500 lines", "SpotBugs");
  }

  @Test
  void customSelectionListsGatesWithoutPresetLimits() {
    final String body =
        AgentRulesTemplate.render(PROJECT, gates(List.of("tests", "checkstyle")), null, List.of())
            .body();

    assertThat(body).contains("`tests`, `checkstyle`").doesNotContain("Checkstyle (");
  }

  @Test
  void writesASectionIntoAgentsMd() {
    assertThat(AgentRulesTemplate.render(PROJECT, gates(List.of("tests")), null, List.of()))
        .satisfies(
            section -> {
              assertThat(section.file()).isEqualTo(Path.of("/p/AGENTS.md"));
              assertThat(section.wholeFile()).isFalse();
            });
  }
}
