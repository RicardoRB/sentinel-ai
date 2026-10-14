package dev.sentinel.domain.traceability;

import java.nio.file.Path;

/** A stable source location used in traceability diagnostics. */
public record SourceLocation(Path path, int line) implements Comparable<SourceLocation> {
  private static final int FIRST_LINE = 1;

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

  @Override
  public int hashCode() {
    return 31 * path.hashCode() + line;
  }

  @Override
  public boolean equals(final Object object) {
    return object instanceof SourceLocation other && line == other.line && path.equals(other.path);
  }
}
