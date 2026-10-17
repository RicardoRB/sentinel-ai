package dev.sentinel.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.domain.agent.PlannedFileChange;
import dev.sentinel.domain.agent.PlannedFileChange.Action;
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

class PresetRuleFilePlanTest {
  private final PresetRuleFileWriter writer = new PresetRuleFileWriter();

  @Test
  void missingFilesAreCreatedWithoutTouchingTheProject(@TempDir final Path root) {
    final List<PlannedFileChange> plan =
        writer.plan(project(root), QualityPreset.STANDARD, gates("pmd", "checkstyle"));

    assertThat(plan)
        .containsExactly(
            new PlannedFileChange(root.resolve("config/pmd-ruleset.xml"), Action.CREATE),
            new PlannedFileChange(root.resolve("config/checkstyle.xml"), Action.CREATE));
    assertThat(root.resolve("config")).doesNotExist();
  }

  @Test
  void currentFileIsUnchangedAndOtherPresetIsModified(@TempDir final Path root) {
    writer.apply(project(root), QualityPreset.STANDARD, gates("pmd"));

    assertThat(writer.plan(project(root), QualityPreset.STANDARD, gates("pmd")))
        .extracting(PlannedFileChange::action)
        .containsExactly(Action.UNCHANGED);
    assertThat(writer.plan(project(root), QualityPreset.STRICT, gates("pmd")))
        .extracting(PlannedFileChange::action)
        .containsExactly(Action.MODIFY);
  }

  @Test
  void unmarkedFileIsPreserved(@TempDir final Path root) throws IOException {
    Files.createDirectories(root.resolve("config"));
    Files.writeString(root.resolve("config/checkstyle.xml"), "<module name=\"Checker\"/>\n");

    assertThat(writer.plan(project(root), QualityPreset.STANDARD, gates("checkstyle")))
        .extracting(PlannedFileChange::action)
        .containsExactly(Action.PRESERVE_USER_OWNED);
  }

  @Test
  void noPresetPlansNothing(@TempDir final Path root) {
    assertThat(writer.plan(project(root), null, gates("pmd"))).isEmpty();
  }

  private static Project project(final Path root) {
    return new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  }

  private static List<InitGateOption> gates(final String... ids) {
    return List.of(ids).stream()
        .map(id -> new InitGateOption(id, id, List.of("mvn", id), true, "available"))
        .toList();
  }
}
