package dev.ward.domain.doctor;

/** The explanation doctor and init give when Git ignores the OpenSpec directory. */
public final class OpenSpecIgnoreWarning {
  /** Why an ignored {@code openspec/} breaks the gate in CI, and what to do about it. */
  public static final String MESSAGE =
      "openspec/ is ignored by Git, so CI checkouts will not contain the specs and this gate will"
          + " fail there. Track openspec/ in Git or disable the gate in CI.";

  private OpenSpecIgnoreWarning() {}
}
