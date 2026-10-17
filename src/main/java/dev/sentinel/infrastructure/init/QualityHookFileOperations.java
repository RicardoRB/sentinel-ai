package dev.sentinel.infrastructure.init;

import dev.sentinel.domain.config.SentinelException;
import dev.sentinel.domain.init.QualityHookChange.HookSnapshot;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** File-level operations used by the quality-hook transaction. */
final class QualityHookFileOperations {
  private QualityHookFileOperations() {}

  static String readOrEmpty(final Path path) throws IOException {
    return Files.exists(path) ? Files.readString(path) : "";
  }

  static void rejectMalformed(final Path path, final String content) {
    final int starts = count(content, QualityHookInstaller.START_MARKER);
    final int ends = count(content, QualityHookInstaller.END_MARKER);
    if ((starts != 0 || ends != 0) && (starts != 1 || ends != 1)) {
      throw new SentinelException(
          "Malformed or duplicate Sentinel quality-hook markers in " + path);
    }
  }

  static String update(final String original, final String profile) {
    final String section = section(profile);
    if (original.contains(QualityHookInstaller.START_MARKER)) {
      final int start = original.indexOf(QualityHookInstaller.START_MARKER);
      int end =
          original.indexOf(QualityHookInstaller.END_MARKER, start)
              + QualityHookInstaller.END_MARKER.length();
      // The section brings its own line break, so replace the existing one with it.
      if (end < original.length() && original.charAt(end) == '\n') {
        end++;
      }
      return original.substring(0, start) + section + original.substring(end);
    }
    final int shebangEnd = original.startsWith("#!") ? original.indexOf('\n') : -1;
    return shebangEnd >= 0
        ? original.substring(0, shebangEnd + 1) + section + original.substring(shebangEnd + 1)
        : section + original;
  }

  static String removeSection(final String content) {
    final int start = content.indexOf(QualityHookInstaller.START_MARKER);
    int end =
        content.indexOf(QualityHookInstaller.END_MARKER, start)
            + QualityHookInstaller.END_MARKER.length();
    if (end < content.length() && content.charAt(end) == '\n') {
      end++;
    }
    final String result = content.substring(0, start) + content.substring(end);
    return result.replaceFirst("(?s)\\A\\n", "");
  }

  static String section(final String profile) {
    return QualityHookInstaller.START_MARKER
        + "\nif ! sentinel check --profile "
        + profile
        + "; then\n  exit 1\nfi\n"
        + QualityHookInstaller.END_MARKER
        + "\n";
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

  private static int count(final String value, final String token) {
    int count = 0;
    int index = 0;
    while (index >= 0) {
      index = value.indexOf(token, index);
      if (index < 0) {
        break;
      }
      count++;
      index += token.length();
    }
    return count;
  }
}
