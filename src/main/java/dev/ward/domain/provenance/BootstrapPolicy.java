package dev.ward.domain.provenance;

import java.util.Optional;

/** Explicit-only first-baseline policy. */
public final class BootstrapPolicy {
  /**
   * Checks the first live-spec baseline. Without {@code selected}, a merge base with no live specs
   * is a finding. With it, the bootstrap needs a {@code declaration}, a merge base without live
   * specs, and a candidate that has them.
   */
  public Optional<EnforcementFinding> check(
      final GitRevisionSnapshot baseline,
      final GitRevisionSnapshot candidate,
      final BootstrapDeclaration declaration,
      final boolean selected) {
    final boolean baselineHasSpecs =
        baseline.files().keySet().stream().anyMatch(path -> path.startsWith("openspec/specs/"));
    final boolean candidateHasSpecs =
        candidate.files().keySet().stream().anyMatch(path -> path.startsWith("openspec/specs/"));
    if (selected && !declaration.present()) {
      return Optional.of(
          new EnforcementFinding(
              EnforcementFinding.Category.BOOTSTRAP,
              "openspec/specs/",
              "Bootstrap requires an explicit baseline declaration."));
    }
    if (selected && baselineHasSpecs) {
      return Optional.of(
          new EnforcementFinding(
              EnforcementFinding.Category.BOOTSTRAP,
              "openspec/specs/",
              "Bootstrap is allowed only when the merge-base has no live-spec baseline."));
    }
    if (selected && !candidateHasSpecs) {
      return Optional.of(
          new EnforcementFinding(
              EnforcementFinding.Category.BOOTSTRAP,
              "openspec/specs/",
              "Bootstrap requires a candidate live-spec baseline."));
    }
    if (!selected && !baselineHasSpecs) {
      return Optional.of(
          new EnforcementFinding(
              EnforcementFinding.Category.BOOTSTRAP,
              "openspec/specs/",
              "No prior live-spec baseline exists; select bootstrap explicitly."));
    }
    return Optional.empty();
  }
}
