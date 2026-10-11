package dev.sentinel.domain.doctor;

import java.nio.file.Path;

/** Result of resolving and checking one configured executable. */
public record ExecutableObservation(
    String requested, Path resolved, boolean available, boolean regularFile, boolean executable) {
  public boolean usable() {
    return available && regularFile && executable;
  }
}
