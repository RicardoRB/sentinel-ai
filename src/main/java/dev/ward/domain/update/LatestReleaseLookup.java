package dev.ward.domain.update;

import java.util.Optional;

/** Port that finds the latest published Ward release. */
@FunctionalInterface
public interface LatestReleaseLookup {
  /** Returns the latest release, or empty when it cannot be determined. */
  Optional<ReleaseVersion> latest();
}
