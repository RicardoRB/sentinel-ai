package dev.ward.domain.spec;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * One agent tool call to check against the approved-artifact guard.
 *
 * @param start where to start project detection
 * @param workingDirectory the directory relative paths start from; blank for the project root
 * @param values the written paths, or the single patch text, shell command, skill name, or
 *     description of unreadable input
 */
public record GuardRequest(Path start, String workingDirectory, Kind kind, List<String> values) {

  /** What the agent asked to do. */
  public enum Kind {
    PATHS,
    PATCH,
    SHELL,
    SKILL,
    UNREADABLE
  }

  /**
   * Validates the request; a missing working directory means the project root.
   *
   * @throws NullPointerException if the start or kind is missing
   */
  public GuardRequest {
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(kind, "kind");
    workingDirectory = workingDirectory == null ? "" : workingDirectory;
    values = List.copyOf(values.stream().map(value -> value == null ? "" : value).toList());
  }

  /** Creates a request for a tool call that writes {@code paths}. */
  public static GuardRequest paths(
      final Path start, final String workingDirectory, final List<String> paths) {
    return new GuardRequest(start, workingDirectory, Kind.PATHS, paths);
  }

  /** Creates a request for a tool call that applies {@code patch}. */
  public static GuardRequest patch(
      final Path start, final String workingDirectory, final String patch) {
    return new GuardRequest(start, workingDirectory, Kind.PATCH, single(patch));
  }

  /** Creates a request for a tool call that runs the shell {@code command}. */
  public static GuardRequest shell(
      final Path start, final String workingDirectory, final String command) {
    return new GuardRequest(start, workingDirectory, Kind.SHELL, single(command));
  }

  /** Creates a request for a tool call that loads the skill {@code name}. */
  public static GuardRequest skill(final Path start, final String name) {
    return new GuardRequest(start, "", Kind.SKILL, single(name));
  }

  /** Input that could not be read in full, such as an oversized or malformed payload. */
  public static GuardRequest unreadable(final Path start, final String description) {
    return new GuardRequest(start, "", Kind.UNREADABLE, single(description));
  }

  /** The single text value of a patch, shell, skill, or unreadable request. */
  public String text() {
    return values.isEmpty() ? "" : values.getFirst();
  }

  private static List<String> single(final String value) {
    return Collections.singletonList(value);
  }
}
