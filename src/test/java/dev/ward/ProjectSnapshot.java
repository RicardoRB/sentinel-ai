package dev.ward;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ward.domain.agent.PlannedFileChange;
import dev.ward.domain.agent.PlannedFileChange.Action;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Captures a project's files so tests can compare what planning and applying changed. */
public final class ProjectSnapshot {
  private ProjectSnapshot() {}

  /** Every regular file under {@code root}, by relative path, with its content. */
  public static Map<String, String> of(final Path root) {
    final Map<String, String> files = new TreeMap<>();
    try (Stream<Path> paths = Files.walk(root)) {
      paths
          .filter(Files::isRegularFile)
          .forEach(file -> files.put(root.relativize(file).toString(), read(file)));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return files;
  }

  /**
   * Plans, checks that planning wrote nothing, applies, and checks that the files created,
   * modified, and removed are exactly the plan's writing entries.
   */
  public static void assertPlanMatches(
      final Path root, final Supplier<List<PlannedFileChange>> planner, final Runnable applier) {
    final Map<String, String> before = of(root);
    final List<PlannedFileChange> plan = planner.get();
    assertThat(of(root)).as("planning must not write").isEqualTo(before);

    applier.run();

    final Map<String, Action> planned = new TreeMap<>();
    plan.stream()
        .filter(change -> change.action().writes())
        .forEach(change -> planned.put(root.relativize(change.file()).toString(), change.action()));
    assertThat(changes(before, of(root))).as("applied changes").isEqualTo(planned);
  }

  /** The files created, modified, or removed between two snapshots. */
  public static Map<String, Action> changes(
      final Map<String, String> before, final Map<String, String> after) {
    final Map<String, Action> changes = new TreeMap<>();
    after.forEach(
        (file, content) -> {
          final String previous = before.get(file);
          if (previous == null) {
            changes.put(file, Action.CREATE);
          } else if (!previous.equals(content)) {
            changes.put(file, Action.MODIFY);
          }
        });
    before.keySet().stream()
        .filter(file -> !after.containsKey(file))
        .forEach(file -> changes.put(file, Action.REMOVE));
    return changes;
  }

  private static String read(final Path file) {
    try {
      return Files.readString(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
