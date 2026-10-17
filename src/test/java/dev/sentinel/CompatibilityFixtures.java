package dev.sentinel;

import dev.sentinel.domain.init.CompatibilityProjectSnapshot;
import dev.sentinel.domain.init.ToolchainSnapshot;
import dev.sentinel.domain.project.Framework;
import dev.sentinel.infrastructure.init.PomToolConfigurator;
import java.util.Map;
import java.util.Optional;

/** A Maven project whose declared Spotless version needs a compatibility update. */
public final class CompatibilityFixtures {
  public static final String OUTDATED_SPOTLESS_POM =
      "<project><modelVersion>4.0.0</modelVersion><build><plugins><plugin>"
          + "<artifactId>spotless-maven-plugin</artifactId><version>1.0.0</version>"
          + "</plugin></plugins></build></project>";

  private CompatibilityFixtures() {}

  /** A configurator that sees Java 17, Maven 3.9.0, and Spotless 1.0.0 declared. */
  public static PomToolConfigurator outdatedSpotless() {
    return new PomToolConfigurator(
        project ->
            new CompatibilityProjectSnapshot(
                project,
                new ToolchainSnapshot(17, Optional.of("3.9.0"), Optional.empty(), Framework.NONE),
                Map.of("spotless-maven-plugin", "1.0.0")));
  }
}
