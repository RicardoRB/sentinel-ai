package dev.sentinel.domain.update;

import java.util.Optional;

/** Port that finds the latest published Sentinel release. */
@FunctionalInterface
public interface LatestReleaseLookup {
  /** Returns the latest release, or empty when it cannot be determined. */
  Optional<ReleaseVersion> latest();
}
