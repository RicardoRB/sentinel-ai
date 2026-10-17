package dev.sentinel.infrastructure.init;

import static dev.sentinel.ProjectSnapshot.assertPlanMatches;

import dev.sentinel.domain.agent.PlannedFileChange;
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
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Each init adapter's plan matches what it writes, on a fresh run and on a re-run. */
class InitAdapterPlanContractTest {
  private static final List<InitGateOption> GATES =
      Stream.of("pmd", "checkstyle", "spotbugs", "enforcer", "archunit", "coverage")
          .map(id -> new InitGateOption(id, id, List.of("mvn", id), true, "available"))
          .toList();
  private static final QualityPreset.ArchitectureExtras EXTRAS =
      QualityPreset.STRICT.rules().architectureExtras();

  @TempDir Path root;

  @Test
  void mavenBuildFileMatchesPlan() throws IOException {
    Files.writeString(
        root.resolve("pom.xml"), "<project><modelVersion>4.0.0</modelVersion></project>");
    final PomToolConfigurator pom = new PomToolConfigurator();

    assertPlanMatchesTwice(
        () -> List.of(pom.plan(maven(), GATES, QualityPreset.STRICT, true)),
        () -> pom.apply(maven(), GATES, QualityPreset.STRICT, finding -> false));
  }

  @Test
  void gradleBuildFileMatchesPlan() throws IOException {
    Files.writeString(root.resolve("build.gradle.kts"), "plugins { java }\n");
    final Project project = new Project(root, Language.KOTLIN, BuildTool.GRADLE, Framework.NONE);
    final GradleToolConfigurator gradle = new GradleToolConfigurator();

    assertPlanMatchesTwice(
        () -> List.of(gradle.plan(project, GATES, null, true)),
        () -> gradle.apply(project, GATES, null, finding -> false));
  }

  @Test
  void ruleFilesMatchPlanIncludingUserOwnedAndOtherPresetFiles() throws IOException {
    final PresetRuleFileWriter rules = new PresetRuleFileWriter();
    rules.apply(maven(), QualityPreset.STANDARD, GATES);
    Files.writeString(root.resolve("config/checkstyle.xml"), "<module name=\"Checker\"/>\n");

    assertPlanMatches(
        root,
        () -> rules.plan(maven(), QualityPreset.STRICT, GATES),
        () -> rules.apply(maven(), QualityPreset.STRICT, GATES));
  }

  @Test
  void architectureTestMatchesPlan() {
    final ArchitectureTestGenerator architecture = new ArchitectureTestGenerator();

    assertPlanMatches(
        root,
        () -> List.of(architecture.plan(maven(), "hexagonal", EXTRAS)),
        () -> architecture.apply(maven(), "hexagonal", EXTRAS));
    assertPlanMatches(
        root,
        () -> List.of(architecture.plan(maven(), "clean", EXTRAS)),
        () -> architecture.apply(maven(), "clean", EXTRAS));
  }

  @Test
  void hooksMatchPlanForUserAndInstalledHooks() throws IOException {
    final QualityHookInstaller hooks = new QualityHookInstaller();
    final Path preCommit = root.resolve(".git/hooks/pre-commit");
    Files.createDirectories(preCommit.getParent());
    Files.writeString(preCommit, "#!/bin/sh\necho user\n");

    assertPlanMatchesTwice(() -> hooks.plan(root), () -> hooks.apply(root));
  }

  /** A fresh run and a re-run must both match their plans. */
  private void assertPlanMatchesTwice(
      final Supplier<List<PlannedFileChange>> planner, final Runnable applier) {
    assertPlanMatches(root, planner, applier);
    assertPlanMatches(root, planner, applier);
  }

  private Project maven() {
    return new Project(root, Language.JAVA, BuildTool.MAVEN, Framework.NONE);
  }
}
