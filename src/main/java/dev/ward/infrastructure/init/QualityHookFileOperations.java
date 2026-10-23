package dev.ward.infrastructure.init;

import dev.ward.domain.config.WardException;
import dev.ward.domain.init.QualityHookChange.HookSnapshot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * File-level operations used by the quality-hook transaction. Every operation works on the section
 * of one project, so sections of other projects in the same hook stay untouched.
 */
final class QualityHookFileOperations {
  private QualityHookFileOperations() {}

  static String readOrEmpty(final Path path) throws IOException {
    return Files.exists(path) ? Files.readString(path) : "";
  }

  static void rejectMalformed(final Path path, final String content, final HookSection section) {
    final int starts = countLines(content, section.start());
    final int ends = countLines(content, section.end());
    if ((starts != 0 || ends != 0) && (starts != 1 || ends != 1)) {
      throw new WardException("Malformed or duplicate Ward quality-hook markers in " + path);
    }
  }

  static boolean contains(final String content, final HookSection section) {
    return lineIndex(content, section.start()) >= 0;
  }

  static String update(final String original, final HookSection section, final String profile) {
    final String body = section.render(profile);
    final int start = lineIndex(original, section.start());
    if (start >= 0) {
      return original.substring(0, start)
          + body
          + original.substring(sectionEnd(original, section));
    }
    final int shebangEnd = original.startsWith("#!") ? original.indexOf('\n') : -1;
    return shebangEnd >= 0
        ? original.substring(0, shebangEnd + 1) + body + original.substring(shebangEnd + 1)
        : body + original;
  }

  static String removeSection(final String content, final HookSection section) {
    final int start = lineIndex(content, section.start());
    final String result =
        content.substring(0, start) + content.substring(sectionEnd(content, section));
    return result.replaceFirst("(?s)\\A\\n", "");
  }

  static void writeHook(final Path path, final String content, final List<String> changed)
      throws IOException {
    if (Files.exists(path) && content.equals(Files.readString(path))) {
      return;
    }
    final Path parent = path.toAbsolutePath().getParent();
    if (parent != null) {
      Files.createDirectories(parent);
    }
    Files.writeString(path, content);
    path.toFile().setExecutable(true, false);
    changed.add(path.toString());
  }

  static HookSnapshot snapshot(final Path path) {
    try {
      return new HookSnapshot(
          path,
          Files.exists(path),
          Files.exists(path) ? Files.readString(path) : "",
          Files.exists(path) && path.toFile().canExecute());
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** The index just past the end marker line, including its line break. */
  private static int sectionEnd(final String content, final HookSection section) {
    final int start = lineIndex(content, section.start());
    int end = lineIndex(content.substring(start), section.end()) + start + section.end().length();
    if (end < content.length() && content.charAt(end) == '\n') {
      end++;
    }
    return end;
  }

  /**
   * Returns where {@code marker} starts as a whole line, or -1. Whole lines matter because the root
   * project's marker is a prefix of every nested project's marker.
   */
  private static int lineIndex(final String content, final String marker) {
    int index = content.indexOf(marker);
    while (index >= 0) {
      final int after = index + marker.length();
      final boolean lineStart = index == 0 || content.charAt(index - 1) == '\n';
      final boolean lineEnd = after == content.length() || content.charAt(after) == '\n';
      if (lineStart && lineEnd) {
        return index;
      }
      index = content.indexOf(marker, index + 1);
    }
    return -1;
  }

  private static int countLines(final String content, final String marker) {
    return (int) content.lines().filter(marker::equals).count();
  }
}
