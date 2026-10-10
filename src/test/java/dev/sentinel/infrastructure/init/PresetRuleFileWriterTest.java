package dev.sentinel.infrastructure.init;

import static org.assertj.core.api.Assertions.assertThat;

import dev.sentinel.TestProjects;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.init.RuleFileChange;
import dev.sentinel.domain.project.BuildTool;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.domain.project.Language;
import dev.sentinel.domain.project.Project;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PresetRuleFileWriterTest {
  @Test
  void writesOwnedFilesAndRestoresCreatedFiles(@TempDir final Path root) throws Exception {
    TestProjects.withPom(root, TestProjects.PLAIN_POM);
    final Project project = new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
    final List<InitGateOption> gates =
        List.of(gate("pmd"), gate("checkstyle"), gate("spotbugs"), gate("enforcer"));
    final PresetRuleFileWriter writer = new PresetRuleFileWriter();

    final RuleFileChange change = writer.apply(project, QualityPreset.STANDARD, gates);
    assertThat(Files.readString(root.resolve("config/pmd-ruleset.xml")))
        .contains("managed-by: sentinel", "quickstart")
        .doesNotContain("bestpractices");
    assertThat(Files.readString(root.resolve("config/enforcer-rules.xml")))
        .contains("dependencyConvergence", "requireMavenVersion");
    writer.rollback(change);
    assertThat(root.resolve("config")).doesNotExist();
  }

  @Test
  void preservesUnmarkedFilesAndReplacesMarkedFiles(@TempDir final Path root) throws Exception {
    TestProjects.withPom(root, TestProjects.PLAIN_POM);
    final Path config = root.resolve("config");
    Files.createDirectories(config);
    final Path pmd = config.resolve("pmd-ruleset.xml");
    Files.writeString(pmd, "user-owned");
    final PresetRuleFileWriter writer = new PresetRuleFileWriter();
    final RuleFileChange change =
        writer.apply(
            new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE),
            QualityPreset.STRICT,
            List.of(gate("pmd")));
    assertThat(Files.readString(pmd)).isEqualTo("user-owned");
    assertThat(change.preserved()).containsExactly(pmd);
  }

  private static InitGateOption gate(final String id) {
    return new InitGateOption(id, id, List.of("mvn", id), true, "available");
  }
}
