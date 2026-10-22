package dev.ward.application.init;

import dev.ward.domain.init.ArchitectureTestGeneration;
import dev.ward.domain.init.BuildToolConfiguration;
import dev.ward.domain.init.ConfigurationStorage;
import dev.ward.domain.init.OwnedSectionGeneration;
import dev.ward.domain.init.RuleFileGeneration;
import java.util.function.Supplier;

/**
 * The outbound ports initialization uses to write project files: the build file, rule files, the
 * ArchUnit test, Ward-owned sections such as the CI workflow, and {@code ward.toml}. They are
 * planned, applied, and rolled back together.
 *
 * @param buildTools writes quality plugins into the build file
 * @param ruleFiles writes the preset's Checkstyle and PMD rule files
 * @param architectureTests generates or reuses the ArchUnit test
 * @param storage supplies the port that reads and writes {@code ward.toml}
 * @param sections writes the CI workflow and the agent rules section
 */
public record InitProjectFiles(
    BuildToolConfiguration buildTools,
    RuleFileGeneration ruleFiles,
    ArchitectureTestGeneration architectureTests,
    Supplier<ConfigurationStorage> storage,
    OwnedSectionGeneration sections) {

  /** Groups the file-writing ports. */
  public InitProjectFiles(
      final BuildToolConfiguration buildTools,
      final RuleFileGeneration ruleFiles,
      final ArchitectureTestGeneration architectureTests,
      final ConfigurationStorage configuration,
      final OwnedSectionGeneration sections) {
    this(buildTools, ruleFiles, architectureTests, () -> configuration, sections);
  }

  /** Groups the file-writing ports for a setup that writes no owned sections. */
  public InitProjectFiles(
      final BuildToolConfiguration buildTools,
      final RuleFileGeneration ruleFiles,
      final ArchitectureTestGeneration architectureTests,
      final ConfigurationStorage configuration) {
    this(buildTools, ruleFiles, architectureTests, configuration, new NoOwnedSectionGeneration());
  }

  /** Groups the file-writing ports for a setup that writes no rule files. */
  public InitProjectFiles(
      final BuildToolConfiguration buildTools,
      final ArchitectureTestGeneration architectureTests,
      final ConfigurationStorage configuration) {
    this(buildTools, new NoRuleFileGeneration(), architectureTests, configuration);
  }

  /** Returns the port that reads and writes {@code ward.toml}. */
  public ConfigurationStorage configuration() {
    return storage.get();
  }
}
