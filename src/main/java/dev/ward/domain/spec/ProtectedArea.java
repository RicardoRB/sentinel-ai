package dev.ward.domain.spec;

/** Why a project-relative path is protected from agent modification. */
public enum ProtectedArea {
  /** Inside {@code openspec/specs}: approved specs. */
  SPECS,
  /** Inside {@code openspec/changes/archive}: archived changes. */
  ARCHIVE,
  /** A directory that contains approved specs or archived changes. */
  ANCESTOR
}
