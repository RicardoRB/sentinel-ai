package dev.ward.domain.traceability;

import java.nio.file.Path;
import java.util.Objects;

/** A stable source location used in traceability diagnostics. */
public record SourceLocation(Path path, int line) implements Comparable<SourceLocation> {
  private static final int FIRST_LINE = 1;

  /**
   * Normalizes the path to an absolute one.
   *
   * @throws IllegalArgumentException if {@code line} is not positive
   */
  public SourceLocation {
    path = path.toAbsolutePath().normalize();
    if (line < FIRST_LINE) {
      throw new IllegalArgumentException("line must be positive");
    }
  }

  @Override
  public int compareTo(final SourceLocation other) {
    final int pathOrder = path.toString().compareTo(other.path.toString());
    if (pathOrder != 0) {
      return pathOrder;
    }
    return Integer.compare(line, other.line);
  }

  // Comparable records keep explicit equality that matches compareTo.
  @Override
  public boolean equals(final Object object) {
    return object instanceof SourceLocation other && path.equals(other.path) && line == other.line;
  }

  @Override
  public int hashCode() {
    return Objects.hash(path, line);
  }
}
