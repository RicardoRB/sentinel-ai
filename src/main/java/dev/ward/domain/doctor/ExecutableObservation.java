package dev.ward.domain.doctor;

import java.nio.file.Path;

/** Result of resolving and checking one configured executable. */
public record ExecutableObservation(
    String requested, Path resolved, boolean available, boolean regularFile, boolean executable) {
  /** Returns whether the executable exists, is a regular file, and may be run. */
  public boolean usable() {
    return available && regularFile && executable;
  }
}
