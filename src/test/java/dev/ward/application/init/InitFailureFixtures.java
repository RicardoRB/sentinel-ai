package dev.ward.application.init;

import dev.ward.ProjectFixtures;
import dev.ward.application.project.ProjectDetector;
import dev.ward.domain.FakeEnvironmentInspection;
import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.BuildFileChange;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.ConfigurationStorage;
import dev.ward.domain.init.InitGateOption;
import dev.ward.domain.init.QualityPreset;
import dev.ward.domain.init.RuleFileChange;
import dev.ward.domain.init.RuleFileGeneration;
import dev.ward.domain.project.Project;
import dev.ward.domain.project.Toolchain;
import dev.ward.infrastructure.init.ArchitectureTestGenerator;
import dev.ward.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Fault-injection collaborators for initialization rollback tests. */
final class InitFailureFixtures {
  private InitFailureFixtures() {}

  static InitService failingService(final Path root) {
    return new InitService(
        new ProjectDetector(new FileSystemProjectInspection()),
        Set.of(),
        new InitSetupCatalog(new FakeEnvironmentInspection()),
        new InitProjectFiles(
            unchangedPom(),
            failingRules(),
            new ArchitectureTestGenerator(),
            storageThatFailsToRestore(root.resolve("ward.toml"))));
  }

  private static ConfigurationStorage storageThatFailsToRestore(final Path configuration) {
    return new ConfigurationStorage() {
      @Override
      public boolean exists(final Path file) {
        return file.equals(configuration);
      }

      @Override
      public Optional<String> read(final Path file) {
        return file.equals(configuration)
            ? Optional.of("version = 1 # original\n")
            : Optional.empty();
      }

      @Override
      public boolean create(final Path file, final String content) {
        return true;
      }

      @Override
      public void replace(final Path file, final String content) {}

      @Override
      public void restore(final Path file, final String originalContent) {
        throw new IllegalStateException("restore failed");
      }
    };
  }

  private static BuildToolConfiguration unchangedPom() {
    return new BuildToolConfiguration() {
      @Override
      public BuildFileChange apply(final Project project, final List<InitGateOption> gates) {
        return new BuildFileChange(
            Toolchain.JVM, project.root().resolve("pom.xml"), ProjectFixtures.PLAIN_POM, List.of());
      }

      @Override
      public PlannedFileChange plan(
          final Project project,
          final List<InitGateOption> gates,
          final QualityPreset preset,
          final boolean approved) {
        return new PlannedFileChange(
            project.root().resolve("pom.xml"), PlannedFileChange.Action.UNCHANGED);
      }

      @Override
      public void rollback(final BuildFileChange change) {}
    };
  }

  private static RuleFileGeneration failingRules() {
    return new RuleFileGeneration() {
      @Override
      public RuleFileChange apply(
          final Project project, final QualityPreset preset, final List<InitGateOption> gates) {
        throw new IllegalStateException("setup failed");
      }

      @Override
      public List<PlannedFileChange> plan(
          final Project project, final QualityPreset preset, final List<InitGateOption> gates) {
        return List.of();
      }

      @Override
      public void rollback(final RuleFileChange change) {}
    };
  }
}
