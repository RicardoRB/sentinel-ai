package dev.ward;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.infrastructure.cli.WardCommand;
import dev.ward.infrastructure.cli.init.InitCommand;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AgentSkillsConsistencyTest {
  @Test
  void repositorySkillsMatchTheCli() throws IOException {
    assertThat(SkillFixtures.consistency(Path.of("")).problems()).isEmpty();
    assertThat(Path.of("skills/ward-init/SKILL.md")).isRegularFile();
    assertThat(Path.of("skills/ward-tune/SKILL.md")).isRegularFile();
  }

  @Test
  void initOptionsIncludeMixinsHelpAndRootOptions() {
    assertThat(CliOptionNames.of(InitCommand.class, WardCommand.class))
        .contains("--preset", "--dry-run", "-C", "--directory", "--help", "--version", "--verbose");
  }

  @Test
  void validFixtureHasNoProblems(@TempDir final Path root) throws IOException {
    SkillFixtures.write(root, "ward init --preset strict --dry-run");

    assertThat(SkillFixtures.consistency(root).problems()).isEmpty();
  }

  @Test
  void reportsMissingSkillsDirectory(@TempDir final Path root) throws IOException {
    assertThat(SkillFixtures.consistency(root).problems())
        .containsExactly("skills/ directory is missing");
  }
}
