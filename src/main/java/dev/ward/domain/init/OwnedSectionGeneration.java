package dev.ward.domain.init;

import dev.ward.domain.agent.PlannedFileChange;

/** Port that writes Ward-owned sections into project files without touching user content. */
public interface OwnedSectionGeneration {
  /** Describes, without writing, what {@link #apply} would do with the section's file. */
  PlannedFileChange plan(OwnedSection section);

  /**
   * Writes the section, replacing an earlier copy of it.
   *
   * @throws dev.ward.domain.config.WardException if user-owned content blocks the write
   */
  OwnedSectionChange apply(OwnedSection section);

  /** Restores the file to its state before {@code change}; {@code null} does nothing. */
  void rollback(OwnedSectionChange change);
}
