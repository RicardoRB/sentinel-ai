package dev.ward.application.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.config.WardException;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.project.BuildTool;
import dev.ward.domain.project.Framework;
import dev.ward.domain.project.Language;
import dev.ward.domain.project.Project;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Source-language gates resolve per language, primary first, and never borrow silently. */
class SourceLanguagesTest {
  private static final Path ROOT = Path.of("app");

  @Test
  void javaKotlinAndMixedProjectsGetTheSameGatesInTheSameOrder() {
    final List<String> java = gateIds(project(Language.JAVA, Set.of()));

    assertThat(gateIds(project(Language.KOTLIN, Set.of()))).isEqualTo(java);
    assertThat(gateIds(project(Language.JAVA, Set.of(Language.KOTLIN)))).isEqualTo(java);
    assertThat(java).startsWith("tests", "compile", "coverage", "spotbugs").doesNotHaveDuplicates();
  }

  @Test
  void primaryLanguageComesFirstAndEachGateOnce() {
    final SourceLanguageGates<String> first = fixed("lint", "shared");
    final SourceLanguageGates<String> second = fixed("types", "shared");
    final Project project = project(Language.KOTLIN, Set.of(Language.JAVA));

    final SourceLanguages<String> gates =
        SourceLanguages.of(Map.of(Language.KOTLIN, first, Language.JAVA, second), project);

    assertThat(gates.analysisGates("cmd"))
        .extracting(InitGateOption::id)
        .containsExactly("lint", "shared", "types");
    assertThat(gates.presetGates(QualityPreset.STANDARD))
        .containsExactly("lint", "shared", "types");
  }

  @Test
  void languageWithoutGatesFailsNamingLanguageAndBuildTool() {
    final Project project = project(Language.KOTLIN, Set.of());

    assertThatThrownBy(() -> SourceLanguages.of(Map.of(Language.JAVA, fixed("pmd")), project))
        .isInstanceOf(WardException.class)
        .hasMessage("Ward init does not support Kotlin Gradle projects yet.");
  }

  private static List<String> gateIds(final Project project) {
    return new JvmInitSupport()
        .gates(project, new FakeEnvironmentInspection()).analysis().stream()
            .map(InitGateOption::id)
            .toList();
  }

  private static Project project(final Language language, final Set<Language> others) {
    return new Project(
        ROOT, language, BuildTool.GRADLE, Framework.NONE, false, false, ROOT, others);
  }

  private static SourceLanguageGates<String> fixed(final String... ids) {
    return new SourceLanguageGates<>() {
      @Override
      public List<InitGateOption> analysisGates(final String command) {
        return List.of(ids).stream()
            .map(id -> new InitGateOption(id, id, List.of(command), true, ""))
            .toList();
      }

      @Override
      public List<String> presetGates(final QualityPreset preset) {
        return List.of(ids);
      }
    };
  }
}
