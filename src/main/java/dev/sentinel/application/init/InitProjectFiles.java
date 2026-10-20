package dev.sentinel.application.init;

import dev.sentinel.domain.init.ArchitectureTestGeneration;
import dev.sentinel.domain.init.BuildToolConfiguration;
import dev.sentinel.domain.init.ConfigurationStorage;
import dev.sentinel.domain.init.RuleFileGeneration;
import java.util.function.Supplier;

/**
 * The outbound ports initialization uses to write project files: the build file, rule files, the
 * ArchUnit test, and {@code sentinel.toml}. They are planned, applied, and rolled back together.
 *
 * @param buildTools writes quality plugins into the build file
 * @param ruleFiles writes the preset's Checkstyle and PMD rule files
 * @param architectureTests generates or reuses the ArchUnit test
 * @param storage supplies the port that reads and writes {@code sentinel.toml}
 */
public record InitProjectFiles(
    BuildToolConfiguration buildTools,
    RuleFileGeneration ruleFiles,
    ArchitectureTestGeneration architectureTests,
    Supplier<ConfigurationStorage> storage) {

  /** Groups the file-writing ports. */
  public InitProjectFiles(
      final BuildToolConfiguration buildTools,
      final RuleFileGeneration ruleFiles,
      final ArchitectureTestGeneration architectureTests,
      final ConfigurationStorage configuration) {
    this(buildTools, ruleFiles, architectureTests, () -> configuration);
  }

  /** Groups the file-writing ports for a setup that writes no rule files. */
  public InitProjectFiles(
      final BuildToolConfiguration buildTools,
      final ArchitectureTestGeneration architectureTests,
      final ConfigurationStorage configuration) {
    this(buildTools, new NoRuleFileGeneration(), architectureTests, configuration);
  }

  /** Returns the port that reads and writes {@code sentinel.toml}. */
  public ConfigurationStorage configuration() {
    return storage.get();
  }
}
