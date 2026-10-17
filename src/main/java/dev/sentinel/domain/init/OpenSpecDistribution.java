package dev.sentinel.domain.init;

import java.nio.file.Path;

/** The OpenSpec package Sentinel installs project-locally, and where it goes. */
public final class OpenSpecDistribution {
  private static final String PACKAGE = "@fission-ai/openspec@1.14.1";
  private static final String NODE_REQUIREMENT = "Node.js >=20.19.0";
  private static final Path DESTINATION = Path.of(".sentinel/tools/openspec");
  private static final Path EXECUTABLE = DESTINATION.resolve("node_modules/.bin/openspec");

  private OpenSpecDistribution() {}

  public static String packageName() {
    return PACKAGE;
  }

  public static String nodeRequirement() {
    return NODE_REQUIREMENT;
  }

  /** Where the package is installed under {@code projectRoot}. */
  public static Path destination(final Path projectRoot) {
    return projectRoot.resolve(DESTINATION);
  }

  /** The installed executable under {@code projectRoot}. */
  public static Path executable(final Path projectRoot) {
    return projectRoot.resolve(EXECUTABLE);
  }

  /** The installation init would run for {@code projectRoot} after consent. */
  public static PlannedInstallation installation(final Path projectRoot) {
    return new PlannedInstallation("openspec", PACKAGE, destination(projectRoot));
  }
}
