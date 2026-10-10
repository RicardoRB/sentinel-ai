package dev.sentinel.application.init;

import dev.sentinel.ProjectFixtures;
import dev.sentinel.application.project.ProjectDetector;
import dev.sentinel.domain.FakeEnvironmentInspection;
import dev.sentinel.domain.init.BuildToolConfiguration;
import dev.sentinel.domain.init.ConfigurationStorage;
import dev.sentinel.domain.init.InitGateOption;
import dev.sentinel.domain.init.PomChange;
import dev.sentinel.domain.init.QualityPreset;
import dev.sentinel.domain.init.RuleFileChange;
import dev.sentinel.domain.init.RuleFileGeneration;
import dev.sentinel.domain.project.Project;
import dev.sentinel.infrastructure.init.ArchitectureTestGenerator;
import dev.sentinel.infrastructure.project.FileSystemProjectInspection;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Fault-injection collaborators for initialization rollback tests. */
final class InitFailureFixtures {
  private InitFailureFixtures() {}

  static InitService failingService(final Path root) {
    final Path configuration = root.resolve("sentinel.toml");
    final ConfigurationStorage storage =
        new ConfigurationStorage() {
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
    final BuildToolConfiguration pom =
        new BuildToolConfiguration() {
          @Override
          public PomChange apply(final Project project, final List<InitGateOption> gates) {
            return new PomChange(
                project.root().resolve("pom.xml"), ProjectFixtures.PLAIN_POM, List.of());
          }

          @Override
          public void rollback(final PomChange change) {}
        };
    final RuleFileGeneration rules =
        new RuleFileGeneration() {
          @Override
          public RuleFileChange apply(
              final Project project, final QualityPreset preset, final List<InitGateOption> gates) {
            throw new IllegalStateException("setup failed");
          }

          @Override
          public void rollback(final RuleFileChange change) {}
        };
    return new InitService(
        new ProjectDetector(new FileSystemProjectInspection()),
        Set.of(),
        new InitSetupCatalog(new FakeEnvironmentInspection()),
        pom,
        new ArchitectureTestGenerator(),
        storage,
        rules);
  }
}
