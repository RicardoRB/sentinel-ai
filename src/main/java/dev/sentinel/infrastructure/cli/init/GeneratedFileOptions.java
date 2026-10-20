package dev.sentinel.infrastructure.cli.init;

import picocli.CommandLine.Option;

/** Options that ask init to write Sentinel-owned files beyond the gate configuration. */
public class GeneratedFileOptions {
  // Picocli assigns annotated fields reflectively, so they cannot be final.
  @Option(
      names = "--ci",
      description = "Write a Sentinel-owned CI workflow that runs the ci profile: github.")
  private String ci;

  @Option(
      names = "--agent-rules",
      description = "Write the selected gates' rules into a Sentinel-owned section of AGENTS.md.")
  private boolean agentRules;

  /** Returns the CI provider, or {@code null} when no workflow is requested. */
  String ci() {
    return ci;
  }

  /** Returns whether the agent rules section is requested. */
  boolean agentRules() {
    return agentRules;
  }
}
