package dev.ward.application.init;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.init.OwnedSection;
import dev.ward.domain.init.OwnedSectionChange;
import dev.ward.domain.init.OwnedSectionGeneration;

/** Owned-section port for runtimes that cannot write a CI workflow or agent rules. */
final class NoOwnedSectionGeneration implements OwnedSectionGeneration {
  private static final String UNAVAILABLE = "Owned sections are unavailable in this runtime.";

  @Override
  public PlannedFileChange plan(final OwnedSection section) {
    throw new IllegalStateException(UNAVAILABLE);
  }

  @Override
  public OwnedSectionChange apply(final OwnedSection section) {
    throw new IllegalStateException(UNAVAILABLE);
  }

  @Override
  public void rollback(final OwnedSectionChange change) {
    // Nothing was written, so nothing is restored.
  }
}
