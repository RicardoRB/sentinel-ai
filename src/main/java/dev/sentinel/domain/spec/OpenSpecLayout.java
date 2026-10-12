package dev.sentinel.domain.spec;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Maps project-relative paths to active OpenSpec items without touching the filesystem. */
public final class OpenSpecLayout {
  private static final String ROOT = "openspec";
  private static final String CHANGES = "changes";
  private static final String SPECS = "specs";
  private static final String ARCHIVE = "archive";
  private static final String SPEC_FILE = "spec.md";
  private static final String PARENT = "..";
  private static final String CURRENT = ".";

  private OpenSpecLayout() {}

  /** Returns each distinct active item in first-seen order; unrelated paths are ignored. */
  public static Set<SpecItem> route(final Collection<Path> projectRelativePaths) {
    final Set<SpecItem> items = new LinkedHashSet<>();
    projectRelativePaths.forEach(path -> item(path).ifPresent(items::add));
    return items;
  }

  /** Returns the active item a normalized project-relative path belongs to, if any. */
  public static Optional<SpecItem> item(final Path projectRelativePath) {
    final List<String> segments = segments(projectRelativePath);
    if (segments.size() < 3 || !ROOT.equals(segments.get(0))) {
      return Optional.empty();
    }
    return switch (segments.get(1)) {
      case CHANGES -> change(segments);
      case SPECS -> spec(segments);
      default -> Optional.empty();
    };
  }

  /** Returns the project-relative directory or file that must exist for the item. */
  public static Path location(final SpecItem item) {
    return item.kind() == SpecItem.Kind.CHANGE
        ? Path.of(ROOT, CHANGES, item.id())
        : Path.of(ROOT, SPECS).resolve(item.id()).resolve(SPEC_FILE);
  }

  private static Optional<SpecItem> change(final List<String> segments) {
    final String name = segments.get(2);
    if (segments.size() < 4 || ARCHIVE.equals(name) || name.startsWith(CURRENT)) {
      return Optional.empty();
    }
    return Optional.of(new SpecItem(SpecItem.Kind.CHANGE, name));
  }

  private static Optional<SpecItem> spec(final List<String> segments) {
    if (!SPEC_FILE.equals(segments.getLast())) {
      return Optional.empty();
    }
    final List<String> capability = segments.subList(2, segments.size() - 1);
    if (capability.isEmpty() || capability.stream().anyMatch(part -> part.startsWith(CURRENT))) {
      return Optional.empty();
    }
    return Optional.of(new SpecItem(SpecItem.Kind.SPEC, String.join("/", capability)));
  }

  private static List<String> segments(final Path path) {
    final List<String> segments = new ArrayList<>();
    if (path == null || path.isAbsolute()) {
      return segments;
    }
    for (final Path part : path.normalize()) {
      final String name = part.toString();
      if (PARENT.equals(name)) {
        return List.of();
      }
      if (!name.isEmpty() && !CURRENT.equals(name)) {
        segments.add(name);
      }
    }
    return segments;
  }
}
